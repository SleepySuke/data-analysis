/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-02
 * @description Handoff集成测试，验证Agent间转交的完整流程和循环检测
 */

package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.suke.agent.memory.*;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.tool.HandoffTool;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class HandoffIntegrationTest {

    private AgentOrchestrator orchestrator;
    private AgentRegistry registry;
    private HandoffManager handoffManager;
    private HandoffTool handoffTool;
    private Agent analystAgent;
    private Agent cleanerAgent;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
        AgentTraceService traceService = new AgentTraceService(traceMapper);
        handoffManager = new HandoffManager(registry, traceService);
        handoffTool = new HandoffTool(registry);

        UserProfileMapper userProfileMapper = mock(UserProfileMapper.class);
        InteractionLogMapper interactionLogMapper = mock(InteractionLogMapper.class);
        LongTermMemoryStore memoryStore = new LongTermMemoryStore(
                userProfileMapper, interactionLogMapper, new UserProfileInjector());

        AgentSkillMapper skillMapper = mock(AgentSkillMapper.class);
        SkillManager skillManager = new SkillManager(skillMapper);

        UserBehaviorTracker behaviorTracker = mock(UserBehaviorTracker.class);
        ConversationHistoryManager historyManager = mock(ConversationHistoryManager.class);
        when(historyManager.isFirstTurn(anyString())).thenReturn(true);

        TopicExtractor topicExtractor = mock(TopicExtractor.class);
        WorkingMemory workingMemory = mock(WorkingMemory.class);
        when(workingMemory.buildContext(anyString())).thenReturn("");

        IntentRouter intentRouter = mock(IntentRouter.class);

        orchestrator = new AgentOrchestrator(registry, traceService, handoffManager,
                memoryStore, skillManager, behaviorTracker, historyManager,
                topicExtractor, workingMemory, intentRouter);

        analystAgent = mock(Agent.class);
        cleanerAgent = mock(Agent.class);

        registry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .description("数据分析师")
                .prompt("test")
                .handoffs(List.of("data_cleaner"))
                .agent(analystAgent)
                .build());

        registry.register(AgentDescriptor.builder()
                .name("data_cleaner")
                .description("数据清洗专家")
                .prompt("test")
                .handoffs(List.of("data_analyst"))
                .agent(cleanerAgent)
                .build());
    }

    @AfterEach
    void tearDown() {
        HandoffContext.clear();
    }

    @Test
    void analystHandoffToCleanerThenBack() throws Exception {
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_analyst");
                    handoffTool.handoff("data_cleaner", "缺失率30%");
                    return Optional.of(mockOutput("发现数据有30%缺失值"));
                })
                .thenAnswer(inv -> Optional.of(mockOutput("分析完成：销售趋势上升")));

        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_cleaner");
                    handoffTool.handoff("data_analyst", "清洗完成");
                    return Optional.of(mockOutput("缺失值已填充"));
                });

        AgentResponse response = orchestrator.directCall("data_analyst", "分析销售数据", 1L, "sess1");

        assertEquals("success", response.getStatus());
        assertEquals("分析完成：销售趋势上升", response.getOutput());
        assertEquals(2, response.getHandoffs().size());

        assertEquals("data_analyst", response.getHandoffs().get(0).getFromAgent());
        assertEquals("data_cleaner", response.getHandoffs().get(0).getToAgent());
        assertEquals("data_cleaner", response.getHandoffs().get(1).getFromAgent());
        assertEquals("data_analyst", response.getHandoffs().get(1).getToAgent());

        assertEquals("data_analyst", response.getSourceAgent());
    }

    @Test
    void handoffCycleDetectedOnConsecutiveRoundTrips() throws Exception {
        // 连续往返 ≥2 次触发循环检测
        // 链路: analyst→cleaner(1)→analyst(2)→cleaner(3)
        // 当 history=[A→C, C→A] 时，第 3 次 A→C 的 consecutiveRoundTrips=2，validate 抛异常
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_analyst");
                    handoffTool.handoff("data_cleaner", "need clean");
                    return Optional.of(mockOutput("dirty"));
                })
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_analyst");
                    handoffTool.handoff("data_cleaner", "still dirty");
                    return Optional.of(mockOutput("still dirty"));
                });

        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_cleaner");
                    handoffTool.handoff("data_analyst", "cleaned");
                    return Optional.of(mockOutput("cleaned"));
                });

        AgentResponse response = orchestrator.directCall("data_analyst", "analyze", 1L, "sess2");

        // 应该在 validate 阶段检测到连续往返循环
        assertEquals("failed", response.getStatus());
    }

    private NodeOutput mockOutput(String text) {
        NodeOutput output = mock(NodeOutput.class);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage(text)));
        when(output.state()).thenReturn(new OverAllState(data));
        return output;
    }
}
