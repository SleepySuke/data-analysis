/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.sse.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * E2E tests covering Plan-Execute Loop scenarios (Fixture 7-10):
 * F7: 3-step plan execution with all PASS
 * F8: 1-step plan with RETRY then PASS
 * F9: 2-step plan with REPLAN, replan creates new 1-step plan with PASS
 * F10: 1-step plan with all RETRY until max iterations exceeded
 */
class PlanExecuteE2ETest extends AgentE2ETestBase {

    private ChatClient deepseekClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setUpPlanExecutor() {
        deepseekClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        // Chain: deepseekClient.prompt() -> requestSpec
        //        requestSpec.user(String) -> requestSpec
        //        requestSpec.call() -> callResponseSpec
        //        callResponseSpec.content() -> String
        when(deepseekClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        pipelineExecutor = new PipelineExecutor(orchestrator, null, parallelExecutorService, 4, 60);
        planExecutor = new PlanExecutor(orchestrator, registry, deepseekClient,
                planExecutorService, pipelineExecutor);
    }

    // ── Fixture 7: Three-step Plan-Execute ─────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture7_planExecute_threeStepsAllPass() throws Exception {
        JSONObject f7 = fixture.getJSONObject("fixture7_planExecute");

        // Register agents
        Agent cleanerAgent = registerMockAgent("data_cleaner", "数据清洗专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct calls
        NodeOutput cleanerOutput = mockOutput("数据清洗完成");
        NodeOutput analystOutput1 = mockOutput("销售趋势分析完成");
        NodeOutput analystOutput2 = mockOutput("分析报告已生成");

        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(cleanerOutput));

        AtomicInteger analystCallCount = new AtomicInteger(0);
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenAnswer(inv -> {
                    int count = analystCallCount.incrementAndGet();
                    if (count == 1) return Optional.of(analystOutput1);
                    return Optional.of(analystOutput2);
                });

        // Mock ChatClient: plan JSON (call 1), then 3 PASS evals (calls 2-4)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan
                        """
                        {"planSummary":"数据清洗分析流程","steps":[
                          {"agentName":"data_cleaner","input":"清洗数据","expectedOutput":"清洗完成"},
                          {"agentName":"data_analyst","input":"分析销售趋势","expectedOutput":"趋势分析"},
                          {"agentName":"data_analyst","input":"生成分析报告","expectedOutput":"报告生成"}
                        ]}
                        """;
                case 2, 3, 4 -> // evaluateStep for each of 3 steps
                        "{\"result\":\"PASS\",\"reason\":\"ok\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"ok\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("分析销售数据", 1L, "sess-f7");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: PlanEvent with 3 steps
        List<PlanEvent> planEvents = events.stream()
                .filter(e -> e instanceof PlanEvent)
                .map(e -> (PlanEvent) e)
                .toList();
        assertEquals(1, planEvents.size(), "Expected exactly 1 PlanEvent");
        assertEquals(3, planEvents.get(0).steps().size(), "Expected 3 plan steps");

        // Verify: 3 step_start + 3 step_result
        long stepStartCount = types.stream().filter("step_start"::equals).count();
        long stepResultCount = types.stream().filter("step_result"::equals).count();
        assertEquals(3, stepStartCount, "Expected 3 step_start events");
        assertEquals(3, stepResultCount, "Expected 3 step_result events");

        // Verify: DoneEvent at end
        assertInstanceOf(DoneEvent.class, events.get(events.size() - 1),
                "Last event should be DoneEvent, got: " + events.get(events.size() - 1).type());

        // Verify all step_results are pass
        List<StepResultEvent> resultEvents = events.stream()
                .filter(e -> e instanceof StepResultEvent)
                .map(e -> (StepResultEvent) e)
                .toList();
        for (StepResultEvent re : resultEvents) {
            assertEquals("pass", re.status(), "Expected step result status=pass");
        }

        // Verify plan status from fixture
        assertEquals("COMPLETED", f7.getString("planStatus"));
    }

    // ── Fixture 8: Step Retry ──────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture8_stepRetry_retryThenPass() throws Exception {
        // Register agent
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct call — will be called multiple times due to retry
        NodeOutput analystOutput = mockOutput("分析完成");
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient: plan JSON (call 1), RETRY eval (call 2), PASS eval (call 3)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan
                        """
                        {"planSummary":"单步分析","steps":[
                          {"agentName":"data_analyst","input":"分析数据","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2 -> // first eval: RETRY
                        "{\"result\":\"RETRY\",\"reason\":\"输出不完整\"}";
                case 3 -> // second eval: PASS (after retry)
                        "{\"result\":\"PASS\",\"reason\":\"ok\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"ok\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("分析数据", 1L, "sess-f8");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: StepRetryEvent with stepIndex=0, retryCount=1
        List<StepRetryEvent> retryEvents = events.stream()
                .filter(e -> e instanceof StepRetryEvent)
                .map(e -> (StepRetryEvent) e)
                .toList();
        assertFalse(retryEvents.isEmpty(), "Expected at least one StepRetryEvent");
        StepRetryEvent retryEvent = retryEvents.get(0);
        assertEquals(0, retryEvent.stepIndex(), "Expected stepIndex=0");
        assertEquals(1, retryEvent.retryCount(), "Expected retryCount=1");

        // Verify: DoneEvent at end (completed successfully after retry)
        boolean hasDone = types.contains("done");
        assertTrue(hasDone, "Expected 'done' event in: " + types);

        // Verify event sequence: after retry, the step is re-executed
        // so there should be 2 step_start and at least 1 step_result (pass)
        long stepStartCount = types.stream().filter("step_start"::equals).count();
        assertTrue(stepStartCount >= 2,
                "Expected at least 2 step_start events (initial + retry), got: " + stepStartCount);
    }

    // ── Fixture 9: Replan ──────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture9_replan_evalReplanThenNewPlanPass() throws Exception {
        // Register agents
        Agent cleanerAgent = registerMockAgent("data_cleaner", "数据清洗专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct calls
        NodeOutput cleanerOutput = mockOutput("数据清洗完成");
        NodeOutput analystOutput = mockOutput("分析完成");

        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(cleanerOutput));
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient:
        // call 1: initial plan (2 steps: cleaner, analyst)
        // call 2: eval step 0 = REPLAN
        // call 3: replan creates new 1-step plan (analyst)
        // call 4: eval new step = PASS
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan: initial 2-step plan
                        """
                        {"planSummary":"清洗加分析","steps":[
                          {"agentName":"data_cleaner","input":"清洗数据","expectedOutput":"清洗完成"},
                          {"agentName":"data_analyst","input":"分析数据","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2 -> // eval step 0: REPLAN
                        "{\"result\":\"REPLAN\",\"reason\":\"需要调整计划\"}";
                case 3 -> // replan: create new 1-step plan
                        """
                        {"planSummary":"重新规划-直接分析","steps":[
                          {"agentName":"data_analyst","input":"综合分析","expectedOutput":"综合报告"}
                        ]}
                        """;
                case 4 -> // eval new step: PASS
                        "{\"result\":\"PASS\",\"reason\":\"ok\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"ok\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("清洗并分析数据", 1L, "sess-f9");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: ReplanEvent with non-null reason
        List<ReplanEvent> replanEvents = events.stream()
                .filter(e -> e instanceof ReplanEvent)
                .map(e -> (ReplanEvent) e)
                .toList();
        assertFalse(replanEvents.isEmpty(), "Expected at least one ReplanEvent");
        ReplanEvent replanEvent = replanEvents.get(0);
        assertNotNull(replanEvent.reason(), "ReplanEvent reason should not be null");
        assertFalse(replanEvent.reason().isBlank(), "ReplanEvent reason should not be blank");

        // Verify: DoneEvent at end
        boolean hasDone = types.contains("done");
        assertTrue(hasDone, "Expected 'done' event in: " + types);

        // Verify step_result with status=replan exists
        List<StepResultEvent> resultEvents = events.stream()
                .filter(e -> e instanceof StepResultEvent)
                .map(e -> (StepResultEvent) e)
                .toList();
        boolean hasReplanResult = resultEvents.stream()
                .anyMatch(e -> "replan".equals(e.status()));
        assertTrue(hasReplanResult, "Expected a step_result with status=replan");
    }

    // ── Fixture 10: Max Iterations ─────────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture10_maxIterations_terminatesAfterMaxRetries() throws Exception {
        // Register agent
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct call
        NodeOutput analystOutput = mockOutput("分析结果不够好");
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient: plan JSON (call 1), then always RETRY
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            if (call == 1) {
                return """
                        {"planSummary":"单步分析","steps":[
                          {"agentName":"data_analyst","input":"分析数据","expectedOutput":"分析报告"}
                        ]}
                        """;
            }
            // All evaluations return RETRY
            return "{\"result\":\"RETRY\",\"reason\":\"输出不符合预期\"}";
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("分析数据", 1L, "sess-f10");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: it terminates (should not loop forever) — we got events back
        assertFalse(events.isEmpty(), "Expected events to be emitted");

        // Verify: terminates with either DoneEvent or ErrorEvent
        boolean hasTerminal = types.contains("done") || types.contains("error");
        assertTrue(hasTerminal, "Expected 'done' or 'error' event for termination, got: " + types);

        // After MAX_RETRY_PER_STEP (3) retries on the same step, it should mark as failed
        // Verify step_retry events were emitted
        long retryCount = types.stream().filter("step_retry"::equals).count();
        assertTrue(retryCount > 0, "Expected step_retry events, got: " + types);

        // The execution should not have generated an excessive number of events
        // MAX_RETRY_PER_STEP = 3, so 3 retries then fail
        assertTrue(events.size() < 50, "Expected bounded number of events, got: " + events.size());

        // Verify that a step_result with fail status exists (retry limit exceeded)
        List<StepResultEvent> resultEvents = events.stream()
                .filter(e -> e instanceof StepResultEvent)
                .map(e -> (StepResultEvent) e)
                .toList();
        boolean hasFailResult = resultEvents.stream()
                .anyMatch(e -> "fail".equals(e.status()));
        assertTrue(hasFailResult, "Expected a step_result with status=fail (retry limit exceeded)");
    }
}
