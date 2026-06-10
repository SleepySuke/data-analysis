/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.sse.SseEvent;
import com.suke.agent.memory.*;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.skill.mapper.AgentSkillMapper;
import com.suke.agent.tool.HandoffTool;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;

import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@Tag("integration")
abstract class AgentE2ETestBase {

    AgentRegistry registry;
    AgentOrchestrator orchestrator;
    AgentRoutingFacade routingFacade;
    PlanExecutor planExecutor;
    PipelineExecutor pipelineExecutor;
    HandoffTool handoffTool;
    HandoffManager handoffManager;
    ExecutorService planExecutorService;
    ExecutorService parallelExecutorService;
    JSONObject fixture;

    AgentTraceMapper traceMapper;
    ConversationHistoryManager historyManager;
    WorkingMemory workingMemory;
    IntentRouter intentRouter;

    @BeforeEach
    void baseSetUp() throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixture/e2e_integration_expected.json")) {
            Objects.requireNonNull(is, "Fixture file not found");
            fixture = JSON.parseObject(new String(is.readAllBytes()));
        }

        registry = new AgentRegistry();
        traceMapper = mock(AgentTraceMapper.class);
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
        historyManager = mock(ConversationHistoryManager.class);
        when(historyManager.isFirstTurn(anyString())).thenReturn(true);

        TopicExtractor topicExtractor = mock(TopicExtractor.class);
        workingMemory = mock(WorkingMemory.class);
        when(workingMemory.buildContext(anyString())).thenReturn("");

        intentRouter = mock(IntentRouter.class);

        orchestrator = new AgentOrchestrator(registry, traceService, handoffManager,
                memoryStore, skillManager, behaviorTracker, historyManager,
                topicExtractor, workingMemory, intentRouter);

        planExecutorService = Executors.newFixedThreadPool(2);
        parallelExecutorService = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void baseTearDown() {
        HandoffContext.clear();
        planExecutorService.shutdownNow();
        parallelExecutorService.shutdownNow();
    }

    Agent registerMockAgent(String name, String description, List<String> handoffs) {
        Agent agent = mock(Agent.class);
        registry.register(AgentDescriptor.builder()
                .name(name)
                .description(description)
                .prompt("test prompt for " + name)
                .handoffs(handoffs)
                .agent(agent)
                .build());
        return agent;
    }

    NodeOutput mockOutput(String text) {
        NodeOutput output = mock(NodeOutput.class);
        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage(text)));
        when(output.state()).thenReturn(new OverAllState(data));
        when(output.tokenUsage()).thenReturn(null);
        return output;
    }

    NodeOutput mockStreamingToken(String text) {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.AGENT_MODEL_STREAMING);
        when(streaming.chunk()).thenReturn(text);
        return streaming;
    }

    NodeOutput mockStreamEnd() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.AGENT_MODEL_FINISHED);
        when(streaming.chunk()).thenReturn("");
        return streaming;
    }

    List<String> collectEventTypes(Flux<SseEvent> flux) {
        return flux.map(SseEvent::type).collectList().block();
    }

    List<SseEvent> collectEvents(Flux<SseEvent> flux) {
        return flux.collectList().block();
    }

    void assertEventTypesMatch(List<String> expected, List<String> actual) {
        List<String> actualFiltered = actual.stream()
                .filter(t -> !"token".equals(t) && !"agent_stream_end".equals(t))
                .toList();
        List<String> expectedFiltered = expected.stream()
                .filter(t -> !"token".equals(t) && !"agent_stream_end".equals(t))
                .toList();
        org.junit.jupiter.api.Assertions.assertEquals(expectedFiltered, actualFiltered,
                "Event type mismatch. Full actual: " + actual);
    }
}
