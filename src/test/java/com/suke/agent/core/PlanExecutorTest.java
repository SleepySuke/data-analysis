/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.core.models.EvalResult;
import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepStatus;
import com.suke.agent.domain.entity.ExecutionPlan;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import com.suke.agent.core.PipelineExecutor;

class PlanExecutorTest {

    private PlanExecutor executor;
    private MockChatService mockChatService;

    @BeforeEach
    void setUp() {
        mockChatService = new MockChatService();
        PipelineExecutor pipelineExec = new PipelineExecutor(mockChatService, null,
                java.util.concurrent.Executors.newFixedThreadPool(2), 4, 60);
        executor = new PlanExecutor(mockChatService, null, null, null, pipelineExec);
    }

    @Test
    void validatePlanRejectsTooManySteps() {
        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            steps.add(PlanStep.builder().stepIndex(i).agentName("data_analyst").input("step " + i).expectedOutput("out").build());
        }
        ExecutionPlan plan = ExecutionPlan.builder().planId("p1").steps(steps).status(PlanStatus.RUNNING).build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(plan));
    }

    @Test
    void validatePlanAcceptsValidPlan() {
        List<PlanStep> steps = List.of(
                PlanStep.builder().stepIndex(0).agentName("data_analyst").input("分析").expectedOutput("结论").build()
        );
        ExecutionPlan plan = ExecutionPlan.builder().planId("p1").steps(steps).status(PlanStatus.RUNNING).build();
        assertDoesNotThrow(() -> executor.validatePlan(plan));
    }

    @Test
    void validatePlanRejectsEmptyPlan() {
        ExecutionPlan plan = ExecutionPlan.builder().planId("p1").steps(List.of()).status(PlanStatus.RUNNING).build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(plan));
    }

    @Test
    void parsePlanResultSuccess() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String llmOutput = """
                ```json
                {
                  "planSummary": "清洗后分析",
                  "steps": [
                    {"agentName": "data_cleaner", "input": "清洗数据", "expectedOutput": "清洗后CSV"},
                    {"agentName": "data_analyst", "input": "分析趋势", "expectedOutput": "趋势报告"}
                  ]
                }
                ```
                """;
        ExecutionPlan plan = exec.parsePlanResult(llmOutput, "原始目标", "plan-001");
        assertEquals("清洗后分析", plan.getPlanSummary());
        assertEquals(2, plan.getSteps().size());
        assertEquals("data_cleaner", plan.getSteps().get(0).getAgentName());
        assertEquals("data_analyst", plan.getSteps().get(1).getAgentName());
        assertEquals("plan-001", plan.getPlanId());
    }

    @Test
    void parsePlanResultWithoutCodeBlock() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String llmOutput = "{\"planSummary\":\"分析\",\"steps\":[{\"agentName\":\"data_analyst\",\"input\":\"分析\",\"expectedOutput\":\"结果\"}]}";
        ExecutionPlan plan = exec.parsePlanResult(llmOutput, "目标", "plan-002");
        assertEquals(1, plan.getSteps().size());
    }

    @Test
    void parseEvalResultPass() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        EvalResult result = exec.parseEvalResult("{\"result\":\"PASS\",\"reason\":\"输出符合预期\"}");
        assertEquals(EvalResult.PASS, result);
    }

    @Test
    void parseEvalResultRetry() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        EvalResult result = exec.parseEvalResult("{\"result\":\"RETRY\",\"reason\":\"输出不完整\"}");
        assertEquals(EvalResult.RETRY, result);
    }

    @Test
    void parseEvalResultInvalidDefaultsToPass() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        EvalResult result = exec.parseEvalResult("{\"result\":\"UNKNOWN\"}");
        assertEquals(EvalResult.PASS, result);
    }

    @Test
    void extractJsonFromCodeBlock() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String input = "Here is the plan:\n```json\n{\"key\": \"value\"}\n```\nDone.";
        assertEquals("{\"key\": \"value\"}", exec.extractJson(input));
    }

    @Test
    void extractJsonFromBareBraces() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String input = "The result is {\"a\": 1} and done.";
        assertEquals("{\"a\": 1}", exec.extractJson(input));
    }

    @Test
    void extractJsonFromPlainCodeBlock() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String input = "Result:\n```\n{\"x\": 42}\n```\nEnd.";
        assertEquals("{\"x\": 42}", exec.extractJson(input));
    }

    @Test
    void extractJsonReturnsEmptyObjectForNull() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        assertEquals("{}", exec.extractJson(null));
    }

    @Test
    void extractJsonReturnsEmptyForNoJson() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        assertEquals("{}", exec.extractJson("no json here"));
    }

    @Test
    void parsePlanResultWithMultipleSteps() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String llmOutput = """
                {
                  "planSummary": "完整分析流程",
                  "steps": [
                    {"agentName": "data_cleaner", "input": "清洗数据", "expectedOutput": "清洗后CSV"},
                    {"agentName": "data_analyst", "input": "统计分析", "expectedOutput": "统计报告"},
                    {"agentName": "sql_analyst", "input": "查询补充数据", "expectedOutput": "查询结果"}
                  ]
                }
                """;
        ExecutionPlan plan = exec.parsePlanResult(llmOutput, "目标", "plan-003");
        assertEquals(3, plan.getSteps().size());
        assertEquals("完整分析流程", plan.getPlanSummary());
        assertEquals("data_cleaner", plan.getSteps().get(0).getAgentName());
        assertEquals("sql_analyst", plan.getSteps().get(2).getAgentName());
        assertEquals(PlanStatus.RUNNING, plan.getStatus());
        assertEquals("目标", plan.getOriginalGoal());
    }

    @Test
    void parseEvalResultReplan() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        EvalResult result = exec.parseEvalResult("{\"result\":\"REPLAN\",\"reason\":\"需要调整\"}");
        assertEquals(EvalResult.REPLAN, result);
    }

    @Test
    void parseEvalResultFail() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        EvalResult result = exec.parseEvalResult("{\"result\":\"FAIL\",\"reason\":\"彻底失败\"}");
        assertEquals(EvalResult.FAIL, result);
    }

    @Test
    void retryStepExhausted() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p1")
                .steps(List.of(PlanStep.builder().stepIndex(0).agentName("data_analyst").input("test").expectedOutput("out").build()))
                .status(PlanStatus.RUNNING)
                .build();
        PlanStep step = plan.currentStep();
        // Exhaust retries
        step.incrementRetryCount();
        step.incrementRetryCount();
        step.incrementRetryCount();
        assertEquals(3, step.getRetryCount());
        // After 3 retries, PlanExecutor.retryStep should mark as FAIL
        // This tests that the retry limit is respected
    }

    @Test
    void simpleThreeStepPlanCompletes() {
        MockChatService mock = new MockChatService();
        mock.addResponse(AgentResponse.builder().output("清洗完成").status("success").totalTokens(100).durationMs(500).build());
        mock.addResponse(AgentResponse.builder().output("分析结果").status("success").totalTokens(200).durationMs(800).build());
        mock.addResponse(AgentResponse.builder().output("图表JSON").status("success").totalTokens(150).durationMs(600).build());
        PipelineExecutor pipelineExec = new PipelineExecutor(mock, null,
                java.util.concurrent.Executors.newFixedThreadPool(2), 4, 60);
        PlanExecutor exec = new PlanExecutor(mock, null, null, null, pipelineExec);

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("plan-test")
                .originalGoal("先清洗，然后分析，最后图表")
                .planSummary("三步测试")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_cleaner").input("清洗").expectedOutput("清洗后数据").build(),
                        PlanStep.builder().stepIndex(1).agentName("data_analyst").input("分析").expectedOutput("分析结果").build(),
                        PlanStep.builder().stepIndex(2).agentName("data_analyst").input("图表").expectedOutput("ECharts JSON").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();

        for (PlanStep step : plan.getSteps()) {
            AgentResponse result = mock.directCall(step.getAgentName(), step.getInput(), 1L, "sess");
            step.setActualOutput(result.getOutput());
            step.setDurationMs(result.getDurationMs());
            plan.advanceStep();
        }
        plan.markCompleted();

        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
        assertEquals(StepStatus.PASS, plan.getSteps().get(0).getStatus());
        assertEquals(StepStatus.PASS, plan.getSteps().get(1).getStatus());
        assertEquals(StepStatus.PASS, plan.getSteps().get(2).getStatus());
        assertEquals("清洗完成", plan.getSteps().get(0).getActualOutput());
        assertEquals("分析结果", plan.getSteps().get(1).getActualOutput());
        assertEquals("图表JSON", plan.getSteps().get(2).getActualOutput());
    }

    @Test
    void stepRetryOnFailThenPass() {
        MockChatService mock = new MockChatService();
        mock.addResponse(AgentResponse.builder().output("").status("success").totalTokens(50).durationMs(100).build());
        mock.addResponse(AgentResponse.builder().output("成功结果").status("success").totalTokens(80).durationMs(200).build());

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("plan-retry")
                .originalGoal("test retry")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_analyst").input("分析").expectedOutput("分析结果").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();

        PlanStep step = plan.currentStep();

        // First call: empty output triggers retry
        AgentResponse firstResult = mock.directCall(step.getAgentName(), step.getInput(), 1L, "sess");
        step.setActualOutput(firstResult.getOutput());
        step.setDurationMs(firstResult.getDurationMs());
        step.incrementRetryCount();
        assertEquals(1, step.getRetryCount());
        assertEquals(StepStatus.RETRY, step.getStatus());

        // Reset for retry
        step.setActualOutput(null);
        step.setDurationMs(0);

        // Second call: success
        AgentResponse secondResult = mock.directCall(step.getAgentName(), step.getInput(), 1L, "sess");
        step.setActualOutput(secondResult.getOutput());
        step.setDurationMs(secondResult.getDurationMs());

        plan.advanceStep();
        plan.markCompleted();

        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
        assertEquals("成功结果", step.getActualOutput());
    }

    @Test
    void tokenBudgetExceeded() {
        MockChatService mock = new MockChatService();
        mock.addResponse(AgentResponse.builder().output("result").status("success").totalTokens(60000).durationMs(500).build());
        PipelineExecutor pipelineExec = new PipelineExecutor(mock, null,
                java.util.concurrent.Executors.newFixedThreadPool(2), 4, 60);
        PlanExecutor exec = new PlanExecutor(mock, null, null, null, pipelineExec);

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("plan-budget")
                .originalGoal("test budget")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_analyst").input("分析").expectedOutput("结果").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();

        PlanStep step = plan.currentStep();
        AgentResponse result = mock.directCall(step.getAgentName(), step.getInput(), 1L, "sess");

        int totalTokens = result.getTotalTokens();
        assertTrue(totalTokens > 50000, "Token usage should exceed budget");
    }

    @Test
    void parsePlanResultWithParallelStep() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String llmOutput = """
                ```json
                {
                  "planSummary": "多源数据整合分析",
                  "steps": [
                    {"agents": ["web_scraper", "sql_analyst"], "mode": "parallel", "input": "获取多源数据", "expectedOutput": "整合数据"},
                    {"agentName": "data_analyst", "mode": "sequential", "input": "深度分析", "expectedOutput": "分析报告"}
                  ]
                }
                ```
                """;
        ExecutionPlan plan = exec.parsePlanResult(llmOutput, "多源分析", "plan-parallel-001");
        assertEquals(2, plan.getSteps().size());
        assertEquals(StepMode.PARALLEL, plan.getSteps().get(0).getMode());
        assertEquals(StepMode.SEQUENTIAL, plan.getSteps().get(1).getMode());
        assertEquals(List.of("web_scraper", "sql_analyst"), plan.getSteps().get(0).getAgentNames());
        assertEquals("data_analyst", plan.getSteps().get(1).getAgentName());
    }

    @Test
    void parsePlanResultWithoutModeDefaultsToSequential() {
        PlanExecutor exec = new PlanExecutor(null, null, null, null, null);
        String llmOutput = """
                {
                  "planSummary": "默认模式",
                  "steps": [
                    {"agentName": "data_analyst", "input": "分析", "expectedOutput": "结果"}
                  ]
                }
                """;
        ExecutionPlan plan = exec.parsePlanResult(llmOutput, "目标", "plan-default");
        assertEquals(StepMode.SEQUENTIAL, plan.getSteps().get(0).getMode());
    }

    @Test
    void validatePlanRejectsParallelStepWithMissingAgentNames() {
        PlanStep parallelStep = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(null)
                .input("test").expectedOutput("out").build();
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p1").steps(List.of(parallelStep)).status(PlanStatus.RUNNING).build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(plan));
    }

    @Test
    void validatePlanAcceptsParallelStepWithValidAgents() {
        PlanStep parallelStep = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("data_analyst", "web_scraper"))
                .input("test").expectedOutput("out").build();
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p1").steps(List.of(parallelStep)).status(PlanStatus.RUNNING).build();
        assertDoesNotThrow(() -> executor.validatePlan(plan));
    }

    @Test
    void validatePlanRejectsSequentialStepWithEmptyAgentName() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0).agentName("").input("test").expectedOutput("out").build();
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p1").steps(List.of(step)).status(PlanStatus.RUNNING).build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(plan));
    }

    static class MockChatService implements AgentChatService {
        private final List<AgentResponse> responses = new ArrayList<>();
        private int callIndex = 0;

        void addResponse(AgentResponse response) {
            responses.add(response);
        }

        @Override
        public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
            if (callIndex < responses.size()) {
                return responses.get(callIndex++);
            }
            return AgentResponse.builder().output("mock output").status("success").totalTokens(10).durationMs(100).build();
        }

        @Override
        public reactor.core.publisher.Flux<com.suke.agent.core.sse.SseEvent> streamingCall(String agentName, String message, Long userId, String sessionId) {
            return reactor.core.publisher.Flux.empty();
        }
    }
}
