/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.sse.*;
import com.suke.agent.tool.HandoffTool;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * E2E tests covering basic agent interaction scenarios (Fixture 1-4):
 * F1: Direct call with response and trace verification
 * F2: Streaming call with token and done events
 * F3: Streaming call with handoff between agents
 * F4: Streaming call to non-existent agent returns error
 */
class BasicAgentE2ETest extends AgentE2ETestBase {

    // ── Fixture 1: Direct Call (sync) ──────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture1_directCall_returnsSuccessWithOutputAndTrace() throws Exception {
        JSONObject f1 = fixture.getJSONObject("fixture1_directCall");
        JSONObject expectedResp = f1.getJSONObject("response");
        JSONObject expectedTrace = f1.getJSONObject("trace");

        // Register data_analyst with handoff capability to data_cleaner
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家",
                List.of("data_cleaner"));
        // Also register data_cleaner so handoff validation doesn't fail
        registerMockAgent("data_cleaner", "数据清洗专家", List.of("data_analyst"));

        // Mock synchronous invocation — extract mockOutput to variable to avoid
        // UnfinishedStubbing from Mockito inline mock creation inside when()
        NodeOutput mockOut = mockOutput("销售趋势呈上升趋势，Q3增长15%");
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(mockOut));

        // Execute
        AgentResponse response = orchestrator.directCall("data_analyst",
                "分析销售数据趋势", 1L, "sess-f1");

        // Assert response
        assert response.getStatus().equals(expectedResp.getString("status"))
                : "Expected status=" + expectedResp.getString("status") + " but got " + response.getStatus();
        assert response.getSourceAgent().equals(expectedResp.getString("sourceAgent"))
                : "Expected sourceAgent=" + expectedResp.getString("sourceAgent") + " but got " + response.getSourceAgent();

        for (String keyword : expectedResp.getList("outputContains", String.class)) {
            assert response.getOutput().contains(keyword)
                    : "Expected output to contain '" + keyword + "' but was: " + response.getOutput();
        }

        // No handoffs occurred
        assert response.getHandoffs() == null || response.getHandoffs().isEmpty()
                : "Expected no handoffs but got " + (response.getHandoffs() != null ? response.getHandoffs().size() : 0);

        // Verify trace saved asynchronously (with timeout for async trace save)
        verify(traceMapper, timeout(2000).atLeastOnce()).insert((com.suke.agent.trace.AgentTrace) any());
    }

    // ── Fixture 2: Streaming Call ──────────────────────────────────────

    @Test
    void fixture2_streamingCall_emitsTokenThenDone() throws Exception {
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Create mock outputs before stubbing to avoid UnfinishedStubbing
        NodeOutput token = mockStreamingToken("分析结果");
        NodeOutput end = mockStreamEnd();

        // Mock streaming: one token chunk then stream end
        when(analystAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.just(token, end));

        Flux<SseEvent> flux = orchestrator.streamingCall("data_analyst",
                "分析销售数据", 1L, "sess-f2");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e instanceof TokenEvent
                        && ((TokenEvent) e).content().equals("分析结果"))
                .expectNextMatches(e -> e instanceof DoneEvent)
                .verifyComplete();
    }

    // ── Fixture 3: Streaming Handoff ───────────────────────────────────

    @Test
    void fixture3_streamingHandoff_emitsHandoffAndTokensFromBothAgents() throws Exception {
        JSONObject f3 = fixture.getJSONObject("fixture3_streamingHandoff");
        JSONObject handoffSpec = f3.getJSONArray("handoffs").getJSONObject(0);

        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家",
                List.of("data_cleaner"));
        Agent cleanerAgent = registerMockAgent("data_cleaner", "数据清洗专家",
                List.of("data_analyst"));

        // Create mock outputs before stubbing to avoid UnfinishedStubbing
        NodeOutput analystToken = mockStreamingToken("发现缺失值");
        NodeOutput analystEnd = mockStreamEnd();
        NodeOutput cleanerToken = mockStreamingToken("清洗完成");
        NodeOutput cleanerEnd = mockStreamEnd();

        // data_analyst streams a token, sets handoff context, triggers handoff
        when(analystAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    HandoffContext.setCurrentAgent("data_analyst");
                    handoffTool.handoff("data_cleaner", "发现缺失值");
                    return Flux.just(analystToken, analystEnd);
                });

        // data_cleaner streams its response
        when(cleanerAgent.stream(anyString(), any(RunnableConfig.class)))
                .thenReturn(Flux.just(cleanerToken, cleanerEnd));

        Flux<SseEvent> flux = orchestrator.streamingCall("data_analyst",
                "分析数据，如果发现质量问题请清洗", 1L, "sess-f3");

        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify handoff event exists with correct from/to
        List<HandoffEvent> handoffEvents = events.stream()
                .filter(e -> e instanceof HandoffEvent)
                .map(e -> (HandoffEvent) e)
                .toList();
        assert !handoffEvents.isEmpty() : "Expected at least one HandoffEvent";
        HandoffEvent he = handoffEvents.get(0);
        assert he.from().equals(handoffSpec.getString("from"))
                : "Expected handoff from=" + handoffSpec.getString("from") + " but got " + he.from();
        assert he.to().equals(handoffSpec.getString("to"))
                : "Expected handoff to=" + handoffSpec.getString("to") + " but got " + he.to();
        assert he.reason().contains(handoffSpec.getString("reasonContains"))
                : "Expected reason to contain '" + handoffSpec.getString("reasonContains") + "' but was: " + he.reason();

        // Verify event types contain handoff and done
        assert types.contains("handoff") : "Expected 'handoff' in event types: " + types;
        assert types.contains("done") : "Expected 'done' in event types: " + types;

        // At least 2 token events (before and after handoff)
        long tokenCount = types.stream().filter("token"::equals).count();
        assert tokenCount >= 2
                : "Expected at least 2 token events but got " + tokenCount + " in " + types;
    }

    // ── Fixture 4: Streaming Error ─────────────────────────────────────

    @Test
    void fixture4_streamingError_emitsErrorEvent() {
        // Do NOT register "bad_agent" -- it doesn't exist
        Flux<SseEvent> flux = orchestrator.streamingCall("bad_agent",
                "触发异常", 1L, "sess-f4");

        StepVerifier.create(flux)
                .expectNextMatches(e -> e instanceof ErrorEvent)
                .verifyComplete();
    }
}
