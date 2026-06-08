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
 * E2E tests covering Pipeline (serial/parallel) and full-chain scenarios (Fixture 11-14):
 * F11: 2-step sequential pipeline (cleaner -> analyst), both PASS
 * F12: 2-step plan with parallel [web_scraper, sql_analyst] -> analyst, both PASS
 * F13: Parallel step with partial failure (web_scraper=success + sql_analyst=failed), eval still PASS
 * F14: Full chain through AgentRoutingFacade, 3-step plan with complex intent
 */
class PipelineE2ETest extends AgentE2ETestBase {

    private ChatClient deepseekClient;
    private ChatClient.ChatClientRequestSpec requestSpec;
    private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setUpPipelineExecutor() {
        deepseekClient = mock(ChatClient.class);
        requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        callResponseSpec = mock(ChatClient.CallResponseSpec.class);

        when(deepseekClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        pipelineExecutor = new PipelineExecutor(orchestrator, null, parallelExecutorService, 4, 60);
        planExecutor = new PlanExecutor(orchestrator, registry, deepseekClient,
                planExecutorService, pipelineExecutor);
    }

    // ── Fixture 11: Sequential Pipeline ────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture11_sequentialPipeline_twoStepsBothPass() throws Exception {
        JSONObject f11 = fixture.getJSONObject("fixture11_sequentialPipeline");

        // Register agents
        Agent cleanerAgent = registerMockAgent("data_cleaner", "数据清洗专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct calls
        NodeOutput cleanerOutput = mockOutput("清洗完成");
        NodeOutput analystOutput = mockOutput("分析报告完成");

        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(cleanerOutput));
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient: plan JSON (call 1), then 2 PASS evals (calls 2-3)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan: 2-step sequential
                        """
                        {"planSummary":"清洗后分析","steps":[
                          {"agentName":"data_cleaner","input":"清洗数据","expectedOutput":"清洗完成"},
                          {"agentName":"data_analyst","input":"分析数据","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2, 3 -> // evaluateStep for each of 2 steps
                        "{\"result\":\"PASS\",\"reason\":\"OK\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"OK\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("清洗并分析数据", 1L, "sess-f11");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: PlanEvent with 2 steps
        List<PlanEvent> planEvents = events.stream()
                .filter(e -> e instanceof PlanEvent)
                .map(e -> (PlanEvent) e)
                .toList();
        assertEquals(1, planEvents.size(), "Expected exactly 1 PlanEvent");
        assertEquals(2, planEvents.get(0).steps().size(), "Expected 2 plan steps");
        assertEquals(f11.getInteger("stepCount"), planEvents.get(0).steps().size(),
                "Step count should match fixture");

        // Verify: 2 step_result with "pass" status
        List<StepResultEvent> resultEvents = events.stream()
                .filter(e -> e instanceof StepResultEvent)
                .map(e -> (StepResultEvent) e)
                .toList();
        assertEquals(2, resultEvents.size(), "Expected 2 step_result events");
        for (StepResultEvent re : resultEvents) {
            assertEquals("pass", re.status(), "Expected step result status=pass");
        }

        // Verify: DoneEvent at end
        assertInstanceOf(DoneEvent.class, events.get(events.size() - 1),
                "Last event should be DoneEvent");
    }

    // ── Fixture 12: Parallel Pipeline ──────────────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture12_parallelPipeline_parallelThenSequential() throws Exception {
        JSONObject f12 = fixture.getJSONObject("fixture12_parallelPipeline");

        // Register all 4 agents
        Agent scraperAgent = registerMockAgent("web_scraper", "网页抓取工具", List.of());
        Agent sqlAgent = registerMockAgent("sql_analyst", "SQL分析专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());
        registerMockAgent("data_cleaner", "数据清洗专家", List.of());

        // Mock agent direct calls
        NodeOutput scraperOutput = mockOutput("网页数据");
        NodeOutput sqlOutput = mockOutput("SQL查询结果");
        NodeOutput analystOutput = mockOutput("综合分析报告");

        when(scraperAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(scraperOutput));
        when(sqlAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(sqlOutput));
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient: plan JSON (call 1), then 2 PASS evals (calls 2-3)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan: 2-step, first parallel
                        """
                        {"planSummary":"抓取查询后综合分析","steps":[
                          {"agents":["web_scraper","sql_analyst"],"mode":"parallel","input":"获取数据","expectedOutput":"数据汇总"},
                          {"agentName":"data_analyst","input":"综合分析","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2, 3 -> // evaluateStep for each of 2 steps
                        "{\"result\":\"PASS\",\"reason\":\"OK\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"OK\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("抓取数据并分析", 1L, "sess-f12");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: PlanEvent with 2 steps
        List<PlanEvent> planEvents = events.stream()
                .filter(e -> e instanceof PlanEvent)
                .map(e -> (PlanEvent) e)
                .toList();
        assertEquals(1, planEvents.size(), "Expected exactly 1 PlanEvent");
        assertEquals(2, planEvents.get(0).steps().size(), "Expected 2 plan steps");

        // Verify: both step_result pass (includes parallel step_result)
        // ParallelStepResultEvent and StepResultEvent both have type "step_result"
        long stepResultCount = types.stream().filter("step_result"::equals).count();
        assertEquals(2, stepResultCount, "Expected 2 step_result events");

        // Verify first step_result is from parallel step (ParallelStepResultEvent)
        List<SseEvent> stepResults = events.stream()
                .filter(e -> "step_result".equals(e.type()))
                .toList();
        assertInstanceOf(ParallelStepResultEvent.class, stepResults.get(0),
                "First step_result should be ParallelStepResultEvent");
        ParallelStepResultEvent parallelResult = (ParallelStepResultEvent) stepResults.get(0);
        assertEquals("pass", parallelResult.status(), "Parallel step should pass");

        // Verify second step_result is sequential (StepResultEvent)
        assertInstanceOf(StepResultEvent.class, stepResults.get(1),
                "Second step_result should be StepResultEvent");
        StepResultEvent seqResult = (StepResultEvent) stepResults.get(1);
        assertEquals("pass", seqResult.status(), "Sequential step should pass");

        // Verify: DoneEvent at end
        assertInstanceOf(DoneEvent.class, events.get(events.size() - 1),
                "Last event should be DoneEvent");
    }

    // ── Fixture 13: Parallel Partial Failure ───────────────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture13_parallelPartialFailure_planCompletes() throws Exception {
        JSONObject f13 = fixture.getJSONObject("fixture13_parallelPartialFailure");

        // Register all 4 agents
        Agent scraperAgent = registerMockAgent("web_scraper", "网页抓取工具", List.of());
        Agent sqlAgent = registerMockAgent("sql_analyst", "SQL分析专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());
        registerMockAgent("data_cleaner", "数据清洗专家", List.of());

        // Mock agent direct calls
        NodeOutput scraperOutput = mockOutput("网页数据获取成功");
        when(scraperAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(scraperOutput));

        // sql_analyst throws exception — PipelineExecutor catches it and returns "执行失败" status
        when(sqlAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenThrow(new RuntimeException("数据库连接超时"));

        NodeOutput analystOutput = mockOutput("基于部分数据的分析报告");
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock ChatClient: plan JSON (call 1), then 2 PASS evals (calls 2-3)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan: 2-step, first parallel
                        """
                        {"planSummary":"部分失败场景","steps":[
                          {"agents":["web_scraper","sql_analyst"],"mode":"parallel","input":"获取数据","expectedOutput":"数据汇总"},
                          {"agentName":"data_analyst","input":"分析","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2, 3 -> // evaluateStep: both PASS (partial failure still gets PASS eval)
                        "{\"result\":\"PASS\",\"reason\":\"OK\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"OK\"}";
            };
        });

        // Execute
        Flux<SseEvent> flux = planExecutor.executeLoopStream("获取数据并分析", 1L, "sess-f13");
        List<SseEvent> events = collectEvents(flux);

        // Verify: DoneEvent exists (plan completes despite partial failure)
        boolean hasDone = events.stream().anyMatch(e -> e instanceof DoneEvent);
        assertTrue(hasDone, "Expected DoneEvent — plan should complete despite partial failure");

        // Verify: ParallelStepResultEvent with "pass" status (eval says PASS)
        List<ParallelStepResultEvent> parallelResults = events.stream()
                .filter(e -> e instanceof ParallelStepResultEvent)
                .map(e -> (ParallelStepResultEvent) e)
                .toList();
        assertFalse(parallelResults.isEmpty(), "Expected at least one ParallelStepResultEvent");
        assertEquals("pass", parallelResults.get(0).status(),
                "Parallel step eval should be PASS");
    }

    // ── Fixture 14: Full Chain via AgentRoutingFacade ──────────────────

    @SuppressWarnings("unchecked")
    @Test
    void fixture14_fullChain_threeStepPlanViaRoutingFacade() throws Exception {
        JSONObject f14 = fixture.getJSONObject("fixture14_fullChain");

        // Register all 4 agents
        Agent scraperAgent = registerMockAgent("web_scraper", "网页抓取工具", List.of());
        Agent sqlAgent = registerMockAgent("sql_analyst", "SQL分析专家", List.of());
        Agent cleanerAgent = registerMockAgent("data_cleaner", "数据清洗专家", List.of());
        Agent analystAgent = registerMockAgent("data_analyst", "数据分析专家", List.of());

        // Mock agent direct calls
        NodeOutput scraperOutput = mockOutput("竞品网页数据已抓取");
        NodeOutput sqlOutput = mockOutput("历史销售数据已查询");
        NodeOutput cleanerOutput = mockOutput("数据清洗完成");
        NodeOutput analystOutput = mockOutput("综合销售分析报告");

        when(scraperAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(scraperOutput));
        when(sqlAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(sqlOutput));
        when(cleanerAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(cleanerOutput));
        when(analystAgent.invokeAndGetOutput(anyString(), any(RunnableConfig.class)))
                .thenReturn(Optional.of(analystOutput));

        // Mock intentRouter: route with complexity=true (triggers plan-execute path)
        when(intentRouter.routeWithComplexity(anyString()))
                .thenReturn(new IntentRouter.IntentResult("data_analyst", true, "多步骤"));

        // Mock ChatClient: plan JSON (call 1), then 3 PASS evals (calls 2-4)
        AtomicInteger llmCallCount = new AtomicInteger(0);
        when(callResponseSpec.content()).thenAnswer(inv -> {
            int call = llmCallCount.incrementAndGet();
            return switch (call) {
                case 1 -> // createPlan: 3-step (parallel, cleaner, analyst)
                        """
                        {"planSummary":"完整销售分析流程","steps":[
                          {"agents":["web_scraper","sql_analyst"],"mode":"parallel","input":"获取竞品和历史数据","expectedOutput":"数据汇总"},
                          {"agentName":"data_cleaner","input":"清洗整合数据","expectedOutput":"清洗完成"},
                          {"agentName":"data_analyst","input":"对比分析生成报告","expectedOutput":"分析报告"}
                        ]}
                        """;
                case 2, 3, 4 -> // evaluateStep for each of 3 steps
                        "{\"result\":\"PASS\",\"reason\":\"OK\"}";
                default -> "{\"result\":\"PASS\",\"reason\":\"OK\"}";
            };
        });

        // Build routing facade
        routingFacade = new AgentRoutingFacade(orchestrator, planExecutor, intentRouter);

        // Execute full chain
        Flux<SseEvent> flux = routingFacade.streamingRouteWithPlan(
                "做一个完整的销售分析报告：抓取竞品数据，查询历史数据，清洗后对比分析",
                1L, "sess-f14");
        List<SseEvent> events = collectEvents(flux);
        List<String> types = events.stream().map(SseEvent::type).toList();

        // Verify: PlanEvent exists
        List<PlanEvent> planEvents = events.stream()
                .filter(e -> e instanceof PlanEvent)
                .map(e -> (PlanEvent) e)
                .toList();
        assertEquals(1, planEvents.size(), "Expected exactly 1 PlanEvent");
        assertEquals(3, planEvents.get(0).steps().size(), "Expected 3 plan steps");

        // Verify: at least 3 step_start events
        long stepStartCount = types.stream().filter("step_start"::equals).count();
        assertTrue(stepStartCount >= 3,
                "Expected at least 3 step_start events, got: " + stepStartCount);

        // Verify: DoneEvent at end
        assertInstanceOf(DoneEvent.class, events.get(events.size() - 1),
                "Last event should be DoneEvent");
    }
}
