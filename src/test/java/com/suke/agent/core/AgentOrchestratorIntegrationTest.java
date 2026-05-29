package com.suke.agent.core;

import com.suke.agent.memory.ConversationHistoryManager;
import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.agent.memory.TopicExtractor;
import com.suke.agent.memory.UserBehaviorTracker;
import com.suke.agent.memory.WorkingMemory;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.model.SkillDefinition;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentOrchestratorIntegrationTest {

    @Mock private AgentRegistry agentRegistry;
    @Mock private AgentTraceService traceService;
    @Mock private HandoffManager handoffManager;
    @Mock private LongTermMemoryStore memoryStore;
    @Mock private SkillManager skillManager;
    @Mock private UserBehaviorTracker behaviorTracker;
    @Mock private ConversationHistoryManager historyManager;
    @Mock private TopicExtractor topicExtractor;
    @Mock private WorkingMemory workingMemory;
    @Mock private IntentRouter intentRouter;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        when(workingMemory.buildContext(any())).thenReturn("");
        orchestrator = new AgentOrchestrator(
                agentRegistry, traceService, handoffManager,
                memoryStore, skillManager, behaviorTracker, historyManager, topicExtractor, workingMemory, intentRouter);
    }

    @Test
    void enrichMessageContainsMemoryContext() {
        when(memoryStore.buildMemoryContext(1L, "data_analyst")).thenReturn("[用户记忆]\n行业: finance\n");
        when(skillManager.buildMetadataPrompt("data_analyst", 1L)).thenReturn("");

        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name("data_analyst")
                .handoffs(List.of())
                .build();

        String result = orchestrator.enrichMessage("分析数据", "data_analyst", 1L, "sess-1", descriptor);

        assertTrue(result.contains("finance"));
        assertTrue(result.contains("分析数据"));
    }

    @Test
    void enrichMessageContainsSkillMetadata() {
        when(skillManager.buildMetadataPrompt("data_analyst", null))
                .thenReturn("[可用 Skills]\n- sales_analysis: 销售趋势分析\n");

        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name("data_analyst")
                .handoffs(List.of())
                .build();

        // userId is null, so memoryStore is not called
        String result = orchestrator.enrichMessage("分析数据", "data_analyst", null, "sess-1", descriptor);

        assertTrue(result.contains("sales_analysis"));
    }

    @Test
    void enrichMessageContainsHandoffTargets() {
        when(skillManager.buildMetadataPrompt(any(), any())).thenReturn("");

        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name("data_analyst")
                .handoffs(List.of("data_cleaner"))
                .build();

        String result = orchestrator.enrichMessage("分析数据", "data_analyst", null, "sess-1", descriptor);

        assertTrue(result.contains("data_cleaner"));
        assertTrue(result.contains("handoff"));
    }

    @Test
    void enrichMessageWithoutContextReturnsOriginal() {
        when(skillManager.buildMetadataPrompt(any(), any())).thenReturn("");

        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name("data_analyst")
                .handoffs(List.of())
                .build();

        String result = orchestrator.enrichMessage("分析数据", "data_analyst", null, "sess-1", descriptor);

        assertEquals("分析数据", result);
    }
}
