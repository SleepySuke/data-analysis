/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description AgentOrchestrator 流式调用测试
 */
package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.suke.agent.core.sse.*;
import com.suke.agent.memory.*;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.*;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentOrchestratorStreamingTest {

    private AgentOrchestrator orchestrator;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
        AgentTraceService traceService = new AgentTraceService(traceMapper);

        HandoffManager handoffManager = new HandoffManager(registry, traceService);
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
    }

    @AfterEach
    void tearDown() {
        HandoffContext.clear();
    }

    @Test
    void streamingCallEmitsTokenThenDone() throws Exception {
        Agent mockAgent = mock(Agent.class);

        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage("分析结果")));
        NodeOutput chunk = NodeOutput.of("agent_node", "test_agent", new OverAllState(data), null);

        when(mockAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.just(chunk));

        registry.register(AgentDescriptor.builder()
                .name("test_agent").prompt("test").agent(mockAgent).build());

        Flux<SseEvent> flux = orchestrator.streamingCall("test_agent", "hello", 1L, "sess1");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e instanceof TokenEvent)
                .expectNextMatches(e -> e instanceof DoneEvent)
                .verifyComplete();
    }

    @Test
    void streamingCallEmitsErrorOnAgentNotFound() {
        Flux<SseEvent> flux = orchestrator.streamingCall("non_existent", "hello", 1L, "sess1");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e.type().equals("error"))
                .verifyComplete();
    }

    @Test
    void streamingCallEmitsErrorOnStreamError() throws Exception {
        Agent mockAgent = mock(Agent.class);
        when(mockAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.error(new RuntimeException("LLM error")));

        registry.register(AgentDescriptor.builder()
                .name("test_agent").prompt("test").agent(mockAgent).build());

        Flux<SseEvent> flux = orchestrator.streamingCall("test_agent", "hello", 1L, "sess1");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e.type().equals("error"))
                .verifyComplete();
    }

    @Test
    void streamingCallWithHandoffEmitsHandoffEvent() throws Exception {
        Agent agentA = mock(Agent.class);
        Agent agentB = mock(Agent.class);

        // Agent A produces output and triggers handoff
        Map<String, Object> dataA = new HashMap<>();
        dataA.put("messages", List.of(new AssistantMessage("需要清洗")));
        NodeOutput outputA = NodeOutput.of("agent_node", "agent_a", new OverAllState(dataA), null);

        when(agentA.stream(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("agent_a");
                    HandoffContext.setPending(new HandoffRequest("agent_a", "agent_b", "脏数据", null));
                    return Flux.just(outputA);
                });

        // Agent B completes normally
        Map<String, Object> dataB = new HashMap<>();
        dataB.put("messages", List.of(new AssistantMessage("清洗完成")));
        NodeOutput outputB = NodeOutput.of("agent_node", "agent_b", new OverAllState(dataB), null);

        when(agentB.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.just(outputB));

        registry.register(AgentDescriptor.builder()
                .name("agent_a").prompt("test").handoffs(List.of("agent_b")).agent(agentA).build());
        registry.register(AgentDescriptor.builder()
                .name("agent_b").prompt("test").agent(agentB).build());

        Flux<SseEvent> flux = orchestrator.streamingCall("agent_a", "analyze", 1L, "sess1");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e instanceof TokenEvent)
                .expectNextMatches(e -> e instanceof HandoffEvent
                        && ((HandoffEvent) e).from().equals("agent_a")
                        && ((HandoffEvent) e).to().equals("agent_b"))
                .expectNextMatches(e -> e instanceof TokenEvent
                        && ((TokenEvent) e).content().equals("清洗完成"))
                .expectNextMatches(e -> e instanceof DoneEvent)
                .verifyComplete();
    }

    @Test
    void streamingCallWithNullSessionIdGeneratesOne() throws Exception {
        Agent mockAgent = mock(Agent.class);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage("ok")));
        NodeOutput chunk = NodeOutput.of("agent_node", "test_agent", new OverAllState(data), null);

        when(mockAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.just(chunk));

        registry.register(AgentDescriptor.builder()
                .name("test_agent").prompt("test").agent(mockAgent).build());

        Flux<SseEvent> flux = orchestrator.streamingCall("test_agent", "hello", 1L, null);

        StepVerifier.create(flux)
                .expectNextMatches(e -> e instanceof TokenEvent)
                .expectNextMatches(e -> e instanceof DoneEvent)
                .verifyComplete();
    }
}
