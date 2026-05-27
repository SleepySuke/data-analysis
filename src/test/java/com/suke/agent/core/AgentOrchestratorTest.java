package com.suke.agent.core;

import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentOrchestratorTest {

    private AgentOrchestrator orchestrator;
    private AgentRegistry registry;
    private AgentTraceService traceService;
    private HandoffManager handoffManager;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
        traceService = new AgentTraceService(traceMapper);
        handoffManager = new HandoffManager(registry, traceService);
        orchestrator = new AgentOrchestrator(registry, traceService, handoffManager);
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
}
