package com.suke.agent.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ConversationHistoryManagerTest {

    private MemorySaver checkpointSaver;
    private ContextCompressor compressor;
    private ConversationHistoryManager manager;

    @BeforeEach
    void setUp() {
        checkpointSaver = MemorySaver.builder().build();
        compressor = TestContextCompressor.create(8000, 3);
        manager = new ConversationHistoryManager(checkpointSaver, compressor, 8000); // uses @Value default constructor
    }

    @Nested
    @DisplayName("首轮检测")
    class FirstTurnDetection {

        @Test
        @DisplayName("无 checkpoint → isFirstTurn=true")
        void isFirstTurn_whenNoCheckpoint_returnsTrue() {
            assertTrue(manager.isFirstTurn("sess-new"));
        }

        @Test
        @DisplayName("有历史消息 → isFirstTurn=false")
        void isFirstTurn_whenHasMessages_returnsFalse() throws Exception {
            // 预存 2 条消息到 checkpoint
            preloadMessages("sess-existing", List.of(
                    new UserMessage("分析数据"),
                    new AssistantMessage("分析结果")
            ));

            assertFalse(manager.isFirstTurn("sess-existing"));
        }

        @Test
        @DisplayName("不同 session 互不影响")
        void isFirstTurn_differentSessions_independent() throws Exception {
            preloadMessages("sess-a", List.of(
                    new UserMessage("hello"),
                    new AssistantMessage("hi")
            ));

            assertTrue(manager.isFirstTurn("sess-b"));
            assertFalse(manager.isFirstTurn("sess-a"));
        }
    }

    @Nested
    @DisplayName("历史消息读取")
    class HistoryMessages {

        @Test
        @DisplayName("无 checkpoint → 空列表")
        void getHistoryMessages_whenNoCheckpoint_returnsEmpty() {
            List<Message> result = manager.getHistoryMessages("sess-new");
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("有 checkpoint → 返回正确消息列表")
        void getHistoryMessages_whenHasCheckpoint_returnsMessages() throws Exception {
            List<Message> expected = List.of(
                    new UserMessage("分析数据"),
                    new AssistantMessage("Q1销售增长10%")
            );
            preloadMessages("sess-1", expected);

            List<Message> result = manager.getHistoryMessages("sess-1");

            assertEquals(2, result.size());
            assertEquals("分析数据", result.get(0).getText());
            assertEquals("Q1销售增长10%", result.get(1).getText());
        }

        @Test
        @DisplayName("消息计数正确")
        void getHistoryMessageCount_returnsCorrectCount() throws Exception {
            preloadMessages("sess-2", List.of(
                    new UserMessage("a"),
                    new AssistantMessage("b"),
                    new UserMessage("c"),
                    new AssistantMessage("d")
            ));

            assertEquals(4, manager.getHistoryMessageCount("sess-2"));
        }
    }

    @Nested
    @DisplayName("压缩判断")
    class CompressionDecision {

        @Test
        @DisplayName("历史短 → 不需要压缩")
        void shouldCompress_shortHistory_returnsFalse() throws Exception {
            preloadMessages("sess-short", List.of(
                    new UserMessage("短消息"),
                    new AssistantMessage("短回复")
            ));
            manager = new ConversationHistoryManager(checkpointSaver, compressor, 8000);

            assertFalse(manager.shouldCompress("sess-short"));
        }

        @Test
        @DisplayName("历史长 → 需要压缩")
        void shouldCompress_longHistory_returnsTrue() throws Exception {
            // 创建大量消息
            List<Message> messages = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                messages.add(new UserMessage("用户消息_" + i + "，这是一段较长的测试数据，包含足够的中文内容"));
                messages.add(new AssistantMessage("助手回复_" + i + "，这是详细的分析结果，包含大量数据分析内容"));
            }
            preloadMessages("sess-long", messages);
            manager = new ConversationHistoryManager(checkpointSaver, compressor, 100);

            assertTrue(manager.shouldCompress("sess-long"));
        }

        @Test
        @DisplayName("空历史 → 不需要压缩")
        void shouldCompress_emptyHistory_returnsFalse() {
            assertFalse(manager.shouldCompress("sess-empty"));
        }
    }

    private void preloadMessages(String sessionId, List<Message> messages) throws Exception {
        Map<String, Object> state = new HashMap<>();
        state.put("messages", new ArrayList<>(messages));
        Checkpoint checkpoint = Checkpoint.builder()
                .state(state)
                .nodeId("test-node")
                .nextNodeId("next-node")
                .build();
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        checkpointSaver.put(config, checkpoint);
    }
}
