package com.suke.e2e;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentOrchestrator;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.core.AgentResponse;
import com.suke.agent.core.HandoffManager;
import com.suke.agent.core.IntentRouter;
import com.suke.agent.memory.ContextCompressor;
import com.suke.agent.memory.TestContextCompressor;
import com.suke.agent.memory.ConversationHistoryManager;
import com.suke.agent.memory.TopicExtractor;
import com.suke.agent.memory.WorkingMemory;
import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.agent.memory.UserBehaviorTracker;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.*;

/**
 * 对话流程 E2E 测试
 *
 * 测试范围：首轮上下文注入、多轮跳过注入、Token 估算、上下文压缩
 * Fixture: src/test/resources/fixture/context_compression_expected.json
 * 预期结果: 对每种场景定义预期的注入行为和压缩结果
 * 真实结果: 调用真实 AgentOrchestrator/ContextCompressor/ConversationHistoryManager
 * 通过标准: 真实结果与预期结果一致
 */
class ConversationFlowE2ETest {

    private JSONObject fixture;

    @BeforeEach
    void loadFixture() throws Exception {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("fixture/context_compression_expected.json")) {
            assertNotNull(is, "Fixture 文件不存在");
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            fixture = JSON.parseObject(content);
        }
    }

    // ========== E2E-1: 首轮对话注入完整上下文 ==========

    @Nested
    @DisplayName("E2E-1: 首轮对话")
    class FirstTurnE2E {

        @Test
        @DisplayName("E2E-1: 首轮对话注入完整上下文（含用户记忆+Skill元数据）")
        void e2e_firstTurn_enrichesWithFullContext() throws Exception {
            // ===== Expected (from fixture) =====
            JSONObject expected = fixture.getJSONObject("conversationFlow").getJSONObject("firstTurn");
            boolean expectedContainsMemory = expected.getBooleanValue("expectedContainsMemory");
            boolean expectedContainsSkill = expected.getBooleanValue("expectedContainsSkill");
            String expectedOriginalMessage = expected.getString("expectedOriginalMessage");

            // ===== Setup =====
            AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
            AgentTraceService traceService = new AgentTraceService(traceMapper);
            AgentRegistry registry = new AgentRegistry();
            HandoffManager handoffManager = new HandoffManager(registry, traceService);

            LongTermMemoryStore memoryStore = mock(LongTermMemoryStore.class);
            when(memoryStore.buildMemoryContext(eq(1L), eq("data_analyst"))).thenReturn(expected.getString("memoryContext"));

            SkillManager skillManager = mock(SkillManager.class);
            when(skillManager.buildMetadataPrompt("data_analyst", 1L))
                    .thenReturn(expected.getString("skillPrompt"));

            UserBehaviorTracker behaviorTracker = mock(UserBehaviorTracker.class);
            MemorySaver memorySaver = MemorySaver.builder().build();
            ContextCompressor compressor = TestContextCompressor.create(8000, 3);
            ConversationHistoryManager historyManager = new ConversationHistoryManager(memorySaver, compressor, 8000);

            // Mock ReactAgent to capture the enriched message
            ReactAgent mockAgent = mock(ReactAgent.class);
            doReturn(Optional.of(buildNodeOutput("分析完成", 150)))
                    .when(mockAgent).invokeAndGetOutput(anyString(), any(RunnableConfig.class));

            registry.register(AgentDescriptor.builder()
                    .name("data_analyst").prompt("test").agent(mockAgent).build());

            IntentRouter intentRouter = mock(IntentRouter.class);
            AgentOrchestrator orchestrator = new AgentOrchestrator(
                    registry, traceService, handoffManager,
                    memoryStore, skillManager, behaviorTracker, historyManager, mock(TopicExtractor.class), mock(WorkingMemory.class), intentRouter);

            // ===== Real =====
            orchestrator.directCall("data_analyst", expected.getString("userMessage"),
                    1L, expected.getString("sessionId"));

            // ===== Verify =====
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockAgent).invokeAndGetOutput(messageCaptor.capture(), any(RunnableConfig.class));
            String realMessage = messageCaptor.getValue();

            assertEquals(expectedContainsMemory, realMessage.contains("[用户记忆]"),
                    "首轮应包含用户记忆: expected=" + expectedContainsMemory);
            assertEquals(expectedContainsSkill, realMessage.contains("sales_analysis"),
                    "首轮应包含 Skill 元数据: expected=" + expectedContainsSkill);
            assertTrue(realMessage.contains(expectedOriginalMessage),
                    "应包含原始消息 '" + expectedOriginalMessage + "'");
        }
    }

    // ========== E2E-2: 多轮对话跳过重复注入 ==========

    @Nested
    @DisplayName("E2E-2: 多轮对话")
    class MultiTurnE2E {

        @Test
        @DisplayName("E2E-2: 后续轮次跳过上下文注入，传递原始消息")
        void e2e_secondTurn_skipsContextEnrichment() throws Exception {
            // ===== Expected (from fixture) =====
            JSONObject expected = fixture.getJSONObject("conversationFlow").getJSONObject("secondTurn");
            String expectedExactMessage = expected.getString("expectedExactMessage");
            boolean expectedContainsMemory = expected.getBooleanValue("expectedContainsMemory");

            // ===== Setup =====
            AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
            AgentTraceService traceService = new AgentTraceService(traceMapper);
            AgentRegistry registry = new AgentRegistry();
            HandoffManager handoffManager = new HandoffManager(registry, traceService);

            LongTermMemoryStore memoryStore = mock(LongTermMemoryStore.class);
            SkillManager skillManager = mock(SkillManager.class);
            UserBehaviorTracker behaviorTracker = mock(UserBehaviorTracker.class);

            MemorySaver memorySaver = MemorySaver.builder().build();
            ContextCompressor compressor = TestContextCompressor.create(8000, 3);
            ConversationHistoryManager historyManager = new ConversationHistoryManager(memorySaver, compressor, 8000);

            // Preload 4 messages for the session → not first turn
            String sessionId = expected.getString("sessionId");
            preloadMessages(memorySaver, sessionId, List.of(
                    new UserMessage("分析Q1销售数据"),
                    new AssistantMessage("Q1销售总额1250万元"),
                    new UserMessage("华东区域表现"),
                    new AssistantMessage("华东区域销售额520万元")
            ));

            ReactAgent mockAgent = mock(ReactAgent.class);
            doReturn(Optional.of(buildNodeOutput("华东区域详细分析", 200)))
                    .when(mockAgent).invokeAndGetOutput(anyString(), any(RunnableConfig.class));

            registry.register(AgentDescriptor.builder()
                    .name("data_analyst").prompt("test").agent(mockAgent).build());

            IntentRouter intentRouter = mock(IntentRouter.class);
            AgentOrchestrator orchestrator = new AgentOrchestrator(
                    registry, traceService, handoffManager,
                    memoryStore, skillManager, behaviorTracker, historyManager, mock(TopicExtractor.class), mock(WorkingMemory.class), intentRouter);

            // ===== Real =====
            orchestrator.directCall("data_analyst", expected.getString("userMessage"),
                    1L, sessionId);

            // ===== Verify =====
            ArgumentCaptor<String> messageCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockAgent).invokeAndGetOutput(messageCaptor.capture(), any(RunnableConfig.class));
            String realMessage = messageCaptor.getValue();

            assertEquals(expectedExactMessage, realMessage,
                    "后续轮次应传递原始消息，不含任何 context 注入");
            assertEquals(expectedContainsMemory, realMessage.contains("[用户记忆]"),
                    "后续轮次不应包含用户记忆");
        }
    }

    // ========== E2E-3: 中文 Token 估算 ==========

    @Nested
    @DisplayName("E2E-3: Token 估算")
    class TokenEstimationE2E {

        @Test
        @DisplayName("E2E-3: 中文 Token 估算高于旧公式")
        void e2e_chineseTokenEstimation_higherThanOldFormula() {
            // ===== Expected =====
            JSONObject expected = fixture.getJSONObject("tokenEstimation").getJSONObject("chineseOnly");
            int expectedNewTokens = expected.getIntValue("expectedNewFormulaTokens");
            int expectedOldTokens = expected.getIntValue("expectedOldFormulaTokens");
            String input = expected.getString("input");

            // ===== Real =====
            ContextCompressor compressor = TestContextCompressor.create(8000, 3);
            int realNew = compressor.estimateTokensForText(input);
            int realOld = (int) Math.ceil((double) input.length() / 4);

            // ===== Verify =====
            assertEquals(expectedNewTokens, realNew,
                    "新公式结果应为 " + expectedNewTokens);
            assertEquals(expectedOldTokens, realOld,
                    "旧公式结果应为 " + expectedOldTokens);
            assertTrue(realNew > realOld,
                    "新公式(" + realNew + ") 应高于旧公式(" + realOld + ")");
        }

        @Test
        @DisplayName("E2E-4: 混合中英文 Token 估算正确")
        void e2e_mixedTokenEstimation_correct() {
            // ===== Expected =====
            JSONObject expected = fixture.getJSONObject("tokenEstimation").getJSONObject("mixedChineseEnglish");
            int expectedTokens = expected.getIntValue("expectedNewFormulaTokens");
            String input = expected.getString("input");

            // ===== Real =====
            ContextCompressor compressor = TestContextCompressor.create(8000, 3);
            int real = compressor.estimateTokensForText(input);

            // ===== Verify =====
            assertEquals(expectedTokens, real,
                    "混合文本估算应为 " + expectedTokens);
        }

        @Test
        @DisplayName("E2E-5: 空字符串 Token 为 0")
        void e2e_emptyStringTokenEstimation_zero() {
            JSONObject expected = fixture.getJSONObject("tokenEstimation").getJSONObject("emptyString");
            int expectedTokens = expected.getIntValue("expectedTokens");

            ContextCompressor compressor = TestContextCompressor.create(8000, 3);
            int real = compressor.estimateTokensForText("");

            assertEquals(expectedTokens, real);
        }
    }

    // ========== E2E-6: 压缩触发 ==========

    @Nested
    @DisplayName("E2E-6: 上下文压缩")
    class CompressionE2E {

        @Test
        @DisplayName("E2E-6: LLM 摘要成功 — 1 条摘要 + 最近 N 轮保留")
        void e2e_compression_llmSummary_success() {
            // ===== Expected =====
            JSONObject expected = fixture.getJSONObject("compression").getJSONObject("triggered");
            int inputRounds = expected.getIntValue("inputRounds");
            int keepRecentRounds = expected.getIntValue("keepRecentRounds");
            int tokenBudget = expected.getIntValue("tokenBudget");
            int expectedMinSize = expected.getIntValue("expectedMinCompressedSize");
            int expectedRecentMsgs = expected.getIntValue("expectedRecentMessages");

            // ===== Real =====
            ContextCompressor compressor = TestContextCompressor.create(tokenBudget, keepRecentRounds,
                    (msgs) -> "用户从多角度分析了Q1销售数据，关注区域差异和利润率变化");
            List<Message> messages = createMessages(inputRounds);
            List<Message> real = compressor.compressWithSummary(messages, tokenBudget);

            // ===== Verify =====
            assertTrue(real.size() >= expectedMinSize,
                    "压缩后应有至少 " + expectedMinSize + " 条消息，实际: " + real.size());

            boolean hasSummary = real.stream()
                    .filter(m -> m instanceof org.springframework.ai.chat.messages.SystemMessage)
                    .anyMatch(m -> m.getText().contains("[对话历史摘要]"));
            assertTrue(hasSummary, "应包含 [对话历史摘要] 标记");

            // 最近 N 轮消息保留
            long recentCount = real.stream()
                    .filter(m -> !(m instanceof org.springframework.ai.chat.messages.SystemMessage))
                    .count();
            assertEquals(expectedRecentMsgs, recentCount,
                    "应保留最近 " + keepRecentRounds + " 轮 = " + expectedRecentMsgs + " 条消息");
        }

        @Test
        @DisplayName("E2E-7: LLM 失败降级为截断")
        void e2e_compression_llmFails_fallbackTruncation() {
            // ===== Expected =====
            JSONObject expected = fixture.getJSONObject("compression").getJSONObject("fallbackTruncation");
            int inputRounds = expected.getIntValue("inputRounds");
            int keepRecentRounds = expected.getIntValue("keepRecentRounds");
            int tokenBudget = expected.getIntValue("tokenBudget");
            int expectedMaxSize = expected.getIntValue("expectedMaxTruncatedSize");

            // ===== Real =====
            ContextCompressor compressor = TestContextCompressor.create(tokenBudget, keepRecentRounds,
                    (msgs) -> { throw new RuntimeException("LLM unavailable"); });
            List<Message> messages = createMessages(inputRounds);

            List<Message> real = assertDoesNotThrow(() ->
                    compressor.compressWithSummary(messages, tokenBudget));

            // ===== Verify =====
            assertTrue(real.size() <= expectedMaxSize,
                    "降级截断后应保留最多 " + expectedMaxSize + " 条消息，实际: " + real.size());
        }

        @Test
        @DisplayName("E2E-8: 不超预算不压缩")
        void e2e_compression_underBudget_unchanged() {
            // ===== Expected =====
            JSONObject expected = fixture.getJSONObject("compression").getJSONObject("notTriggered");
            int inputRounds = expected.getIntValue("inputRounds");
            int tokenBudget = expected.getIntValue("tokenBudget");
            int expectedSize = expected.getIntValue("expectedSize");

            // ===== Real =====
            ContextCompressor compressor = TestContextCompressor.create(tokenBudget, 3);
            List<Message> messages = createMessages(inputRounds);

            List<Message> real = compressor.compressWithSummary(messages, tokenBudget);

            // ===== Verify =====
            assertSame(messages, real, "不超预算应返回原列表");
            assertEquals(expectedSize, real.size());
        }
    }

    // ========== Helpers ==========

    private List<Message> createMessages(int rounds) {
        List<Message> messages = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            messages.add(new UserMessage("用户消息_" + i + "，这是一段测试数据，包含一些内容用于模拟实际对话，每轮对话都包含足够的文字"));
            messages.add(new AssistantMessage("助手回复_" + i + "，这是分析结果，包含一些详细的数据分析内容，用于测试压缩逻辑的正确性"));
        }
        return messages;
    }

    private void preloadMessages(MemorySaver saver, String sessionId, List<Message> messages) throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put("messages", new ArrayList<>(messages));
        Checkpoint checkpoint = Checkpoint.builder()
                .state(state)
                .nodeId("test-node")
                .nextNodeId("next-node")
                .build();
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        saver.put(config, checkpoint);
    }

    private NodeOutput buildNodeOutput(String text, int tokens) {
        List<Message> messages = List.of(new AssistantMessage(text));
        Map<String, Object> stateData = new HashMap<>();
        stateData.put("messages", messages);
        OverAllState state = new OverAllState(stateData);
        Usage usage = mock(Usage.class);
        when(usage.getTotalTokens()).thenReturn(tokens);
        return NodeOutput.of("agent_node", "data_analyst", state, usage);
    }
}
