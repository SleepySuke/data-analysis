package com.suke.agent.core;

import com.suke.agent.memory.ConversationHistoryManager;
import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.agent.memory.TopicExtractor;
import com.suke.agent.memory.UserBehaviorTracker;
import com.suke.agent.memory.WorkingMemory;
import com.suke.agent.memory.UserProfileInjector;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator;
    private AgentRegistry registry;
    private AgentTraceService traceService;
    private HandoffManager handoffManager;
    private LongTermMemoryStore memoryStore;
    private SkillManager skillManager;
    private UserBehaviorTracker behaviorTracker;
    private ConversationHistoryManager historyManager;
    private TopicExtractor topicExtractor;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
        traceService = new AgentTraceService(traceMapper);
        handoffManager = new HandoffManager(registry, traceService);

        UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
        InteractionLogMapper interactionLogMapper = mock(InteractionLogMapper.class);
        memoryStore = new LongTermMemoryStore(userProfileMapper, interactionLogMapper, new UserProfileInjector());

        AgentSkillMapper skillMapper = mock(AgentSkillMapper.class);
        skillManager = new SkillManager(skillMapper);

        behaviorTracker = mock(UserBehaviorTracker.class);

        historyManager = mock(ConversationHistoryManager.class);
        when(historyManager.isFirstTurn(anyString())).thenReturn(true);

        topicExtractor = mock(TopicExtractor.class);
        WorkingMemory workingMemory = mock(WorkingMemory.class);
        when(workingMemory.buildContext(anyString())).thenReturn("");

        IntentRouter intentRouter = mock(IntentRouter.class);
        when(intentRouter.route(anyString())).thenAnswer(inv -> {
            String msg = inv.getArgument(0, String.class).toLowerCase();
            if (msg.contains("sql") || msg.contains("数据库") || msg.contains("查询"))
                return "sql_analyst";
            if (msg.contains("清洗") || msg.contains("缺失值") || msg.contains("异常值") || msg.contains("去重"))
                return "data_cleaner";
            return "data_analyst";
        });

        orchestrator = new AgentOrchestrator(registry, traceService, handoffManager,
                memoryStore, skillManager, behaviorTracker, historyManager, topicExtractor, workingMemory, intentRouter);
    }

    @Test
    void directCallToNonExistentAgentThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                orchestrator.directCall("non_existent", "hello", 1L, "sess1"));
    }

    @Test
    void directCallWithNullSessionIdGeneratesFallback() {
        // 注册一个没有 agent 实例的 descriptor
        registry.register(AgentDescriptor.builder()
                .name("test_agent")
                .prompt("test")
                .build());

        // 调用时 agent 实例为 null，会返回 failed 状态
        AgentResponse response = orchestrator.directCall("test_agent", "hello", 1L, null);

        assertEquals("failed", response.getStatus());
        assertNotNull(response.getTraceId());
        assertNotNull(response.getDurationMs());
    }

    @Test
    void autoRouteSelectsDataAnalyst() {
        registry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .prompt("test")
                .build());

        AgentResponse response = orchestrator.autoRoute("分析这个CSV数据", 1L, "sess1");
        assertEquals("failed", response.getStatus()); // fails because no agent instance
    }

    @Test
    void autoRouteSelectsSqlAnalyst() {
        registry.register(AgentDescriptor.builder()
                .name("sql_analyst")
                .prompt("test")
                .build());

        AgentResponse response = orchestrator.autoRoute("查询数据库中的用户表", 1L, "sess1");
        assertEquals("failed", response.getStatus());
    }

    @Test
    void autoRouteDefaultsToDataAnalyst() {
        registry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .prompt("test")
                .build());

        AgentResponse response = orchestrator.autoRoute("帮我分析数据", 1L, "sess1");
        assertEquals("failed", response.getStatus());
    }

    @Test
    void directCallWithNoHandoffReturnsImmediately() throws Exception {
        com.alibaba.cloud.ai.graph.agent.Agent mockAgent =
                mock(com.alibaba.cloud.ai.graph.agent.Agent.class);
        com.alibaba.cloud.ai.graph.NodeOutput mockOutput =
                mock(com.alibaba.cloud.ai.graph.NodeOutput.class);
        when(mockAgent.invokeAndGetOutput(anyString(), any())).thenReturn(
                java.util.Optional.of(mockOutput));

        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("messages", java.util.List.of(
                new org.springframework.ai.chat.messages.AssistantMessage("分析完成")));
        when(mockOutput.state()).thenReturn(
                new com.alibaba.cloud.ai.graph.OverAllState(data));

        registry.register(AgentDescriptor.builder()
                .name("test_agent")
                .prompt("test")
                .agent(mockAgent)
                .build());

        AgentResponse response = orchestrator.directCall("test_agent", "hello", 1L, "sess1");

        assertEquals("success", response.getStatus());
        assertEquals("分析完成", response.getOutput());
        assertTrue(response.getHandoffs().isEmpty());
        assertNull(com.suke.agent.core.HandoffContext.getPending());
    }

    @Test
    void directCallCleansUpHandoffContextOnError() throws Exception {
        com.alibaba.cloud.ai.graph.agent.Agent mockAgent =
                mock(com.alibaba.cloud.ai.graph.agent.Agent.class);
        when(mockAgent.invokeAndGetOutput(anyString(), any()))
                .thenThrow(new RuntimeException("agent crashed"));

        registry.register(AgentDescriptor.builder()
                .name("crash_agent")
                .prompt("test")
                .agent(mockAgent)
                .build());

        AgentResponse response = orchestrator.directCall("crash_agent", "hello", 1L, "sess1");

        assertEquals("failed", response.getStatus());
        assertNull(com.suke.agent.core.HandoffContext.getPending());
    }
}
