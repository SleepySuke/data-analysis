package com.suke.agent.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContextCompressorTest {

    private ContextCompressor compressor;

    @BeforeEach
    void setUp() {
        compressor = TestContextCompressor.create(8000, 3);
    }

    // ========== 中文/英文 Token 估算 ==========

    @Nested
    @DisplayName("Token 估算")
    class TokenEstimation {

        @Test
        @DisplayName("纯中文：11个中文字符 → ceil(11/1.5)=8，高于旧公式 ceil(11/4)=3")
        void estimateTokensForText_chineseOnly_higherThanOldFormula() {
            String input = "分析销售数据的增长趋势"; // 11 Chinese chars

            int real = compressor.estimateTokensForText(input);

            int expectedNew = 8; // ceil(11/1.5) = 8
            int expectedOld = 3; // ceil(11/4) = 3
            assertEquals(expectedNew, real, "新公式结果应为 " + expectedNew);
            assertTrue(real > expectedOld, "新公式结果应高于旧公式 " + expectedOld);
        }

        @Test
        @DisplayName("纯英文：25个英文字符 → ceil(25/4)=7")
        void estimateTokensForText_englishOnly_correct() {
            String input = "analyze sales data trends"; // 25 chars including spaces

            int real = compressor.estimateTokensForText(input);

            int expected = 7; // ceil(25/4) = 7
            assertEquals(expected, real);
        }

        @Test
        @DisplayName("混合：6中文+10英文 → ceil(6/1.5)+ceil(10/4)=4+3=7")
        void estimateTokensForText_mixedChineseEnglish_correct() {
            String input = "分析sales增长trend数据"; // 6 Chinese + 10 others

            int real = compressor.estimateTokensForText(input);

            int expected = 7; // ceil(6/1.5) + ceil(10/4) = 4 + 3 = 7
            assertEquals(expected, real);
        }

        @Test
        @DisplayName("空字符串 → 0 token")
        void estimateTokensForText_empty_returnsZero() {
            assertEquals(0, compressor.estimateTokensForText(""));
        }

        @Test
        @DisplayName("estimateTokens 对消息列表求和正确")
        void estimateMessages_sumsAllMessages() {
            List<Message> messages = List.of(
                    new UserMessage("分析数据"),     // 4 Chinese = 3 tokens
                    new AssistantMessage("结果")      // 2 Chinese = 2 tokens
            );

            int tokens = compressor.estimateTokens(messages);

            assertEquals(5, tokens); // 3 + 2 = 5
        }
    }

    // ========== compressWithSummary 无 LLM 降级（summaryClient=null）==========

    @Nested
    @DisplayName("压缩 - 无 LLM 降级为截断")
    class CompressionWithoutLlm {

        @Test
        @DisplayName("不超预算：原样返回 same reference")
        void compressWithSummary_underBudget_returnsUnmodified() {
            List<Message> messages = createMessages(2);

            List<Message> result = compressor.compressWithSummary(messages, 8000);

            assertSame(messages, result, "不超预算应返回原列表");
        }

        @Test
        @DisplayName("超预算无 LLM：降级为截断，保留最近 N 轮")
        void compressWithSummary_overBudget_noLlm_fallsBackToTruncation() {
            compressor = TestContextCompressor.create(100, 2);
            List<Message> messages = createMessages(10);

            List<Message> result = compressor.compressWithSummary(messages, 100);

            // 降级为截断：保留最近 2 轮 = 4 条消息
            assertTrue(result.size() <= 4, "降级截断应保留最近 2 轮 = 4 条消息，实际: " + result.size());
        }

        @Test
        @DisplayName("空消息列表：返回空")
        void compressWithSummary_empty_returnsEmpty() {
            List<Message> result = compressor.compressWithSummary(List.of(), 8000);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("截断保留 SystemMessage")
        void truncateToRecent_preservesSystemMessages() {
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是一个数据分析师"));
            messages.addAll(createMessages(6));

            List<Message> result = compressor.truncateToRecent(messages, 2);

            // 1 SystemMessage + 4 recent messages (2 rounds)
            assertTrue(result.stream().anyMatch(m -> m instanceof SystemMessage));
            assertTrue(result.get(0) instanceof SystemMessage);
            assertEquals(5, result.size()); // 1 system + 4 recent
        }
    }

    // ========== compressWithSummary 有 LLM 摘要 ==========

    @Nested
    @DisplayName("压缩 - LLM 摘要")
    class CompressionWithLlm {

        @Test
        @DisplayName("LLM 摘要成功：1 条摘要 + 最近 N 轮")
        void compressWithSummary_llmSuccess_producesSummaryPlusRecent() {
            compressor = TestContextCompressor.create(100, 3, (msgs) -> "用户从多角度分析了Q1销售数据，关注区域差异和利润率");

            List<Message> messages = createMessages(10); // 20 条消息

            List<Message> result = compressor.compressWithSummary(messages, 100);

            // 期望：1 摘要 SystemMessage + 6 recent (3 rounds)
            assertTrue(result.size() >= 7, "应有至少 7 条消息（1 摘要 + 6 近 3 轮），实际: " + result.size());

            // 验证摘要存在
            boolean hasSummary = result.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .anyMatch(m -> m.getText().contains("[对话历史摘要]"));
            assertTrue(hasSummary, "应包含 [对话历史摘要] 标记的 SystemMessage");

            // 验证最近 3 轮保留：顺序为 User→Assistant，最后一对是 round 9
            assertTrue(result.get(result.size() - 2).getText().contains("用户消息_9"));
            assertTrue(result.get(result.size() - 1).getText().contains("助手回复_9"));
        }

        @Test
        @DisplayName("LLM 摘要失败：降级为截断，不崩溃")
        void compressWithSummary_llmFails_fallsBackToTruncation() {
            compressor = TestContextCompressor.create(100, 2, (msgs) -> {
                throw new RuntimeException("LLM service unavailable");
            });

            List<Message> messages = createMessages(10);

            List<Message> result = compressor.compressWithSummary(messages, 100);

            // 降级为截断：最近 2 轮 = 4 条消息
            assertNotNull(result);
            assertTrue(result.size() <= 4, "降级截断应保留最近 2 轮 = 4 条");
            assertDoesNotThrow(() -> compressor.compressWithSummary(messages, 100));
        }

        @Test
        @DisplayName("SystemMessage 在摘要压缩中保留")
        void compressWithSummary_preservesOriginalSystemMessages() {
            compressor = TestContextCompressor.create(100, 3, (msgs) -> "摘要内容");

            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage("你是数据分析师"));
            messages.addAll(createMessages(10));

            List<Message> result = compressor.compressWithSummary(messages, 100);

            // 原始 SystemMessage 保留
            boolean hasOriginalSystem = result.stream()
                    .filter(m -> m instanceof SystemMessage)
                    .anyMatch(m -> m.getText().equals("你是数据分析师"));
            assertTrue(hasOriginalSystem, "原始 SystemMessage 应保留");
        }
    }

    // ========== 旧测试保留 ==========

    @Test
    void noCompressWhenUnderBudget() {
        List<Message> messages = List.of(
                new UserMessage("分析销售数据"),
                new AssistantMessage("Q1销售下降5%")
        );

        List<Message> result = compressor.compressIfNeeded(messages, 8000);

        assertEquals(2, result.size());
        assertSame(messages, result);
    }

    @Test
    void keepRecentRoundsIntact() {
        List<Message> messages = createMessages(6);

        List<Message> result = compressor.compressIfNeeded(messages, 100);

        assertNotNull(result);
        assertTrue(result.size() >= 6);
    }

    @Test
    void estimateTokensReturnsPositive() {
        List<Message> messages = List.of(
                new UserMessage("分析数据"),
                new AssistantMessage("分析结果")
        );

        int tokens = compressor.estimateTokens(messages);

        assertTrue(tokens > 0);
    }

    @Test
    void emptyMessagesReturnsEmpty() {
        List<Message> result = compressor.compressIfNeeded(List.of(), 8000);

        assertTrue(result.isEmpty());
    }

    @Test
    void fallbackToTruncateWhenSummaryUnavailable() {
        compressor = TestContextCompressor.create(100, 2);
        List<Message> messages = createMessages(10);

        List<Message> result = compressor.truncateToRecent(messages, 2);

        assertEquals(4, result.size());
    }

    @Test
    void truncateToRecentKeepsLatest() {
        List<Message> messages = createMessages(6);

        List<Message> result = compressor.truncateToRecent(messages, 2);

        assertEquals(4, result.size());
        assertTrue(((UserMessage) result.get(0)).getText().contains("用户消息_4"));
    }

    @Test
    void singleRoundNotCompressed() {
        List<Message> messages = List.of(
                new UserMessage("分析数据")
        );

        List<Message> result = compressor.compressIfNeeded(messages, 8000);

        assertEquals(1, result.size());
    }

    private List<Message> createMessages(int rounds) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            messages.add(new UserMessage("用户消息_" + i + "，这是一段测试数据，包含一些内容用于模拟实际对话，每轮对话都包含足够的文字"));
            messages.add(new AssistantMessage("助手回复_" + i + "，这是分析结果，包含一些详细的数据分析内容，用于测试压缩逻辑的正确性"));
        }
        return messages;
    }
}
