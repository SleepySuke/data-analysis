/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepResult;
import com.suke.agent.domain.entity.ExecutionPlan;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class PipelineIntegrationTest {

    private static JSONObject fixture;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream is = PipelineIntegrationTest.class.getResourceAsStream("/fixture/pipeline_integration_expected.json")) {
            assertNotNull(is, "Fixture file not found");
            fixture = JSON.parseObject(new String(is.readAllBytes()));
        }
    }

    @Test
    void mixedSequentialAndParallelPlan() {
        // Setup mock responses matching fixture
        PipelineExecutorTest.MockChatService chatService = new PipelineExecutorTest.MockChatService();

        JSONArray expectedSteps = fixture.getJSONArray("steps");

        // Step 0: data_cleaner (sequential)
        JSONObject step0Expected = expectedSteps.getJSONObject(0).getJSONObject("expectedOutput");
        chatService.addResponse("data_cleaner", AgentResponse.builder()
                .output(step0Expected.getString("actualOutput"))
                .status("success").totalTokens(80).durationMs(300).build());

        // Step 1: web_scraper + sql_analyst (parallel)
        JSONObject step1Expected = expectedSteps.getJSONObject(1).getJSONObject("expectedOutput");
        JSONObject agentOutputs = step1Expected.getJSONObject("agentOutputs");
        chatService.addResponse("web_scraper", AgentResponse.builder()
                .output(agentOutputs.getJSONObject("web_scraper").getString("output"))
                .status("success").totalTokens(100).durationMs(500).build());
        chatService.addResponse("sql_analyst", AgentResponse.builder()
                .output(agentOutputs.getJSONObject("sql_analyst").getString("output"))
                .status("success").totalTokens(90).durationMs(400).build());

        // Step 2: data_analyst (sequential)
        JSONObject step2Expected = expectedSteps.getJSONObject(2).getJSONObject("expectedOutput");
        chatService.addResponse("data_analyst", AgentResponse.builder()
                .output(step2Expected.getString("actualOutput"))
                .status("success").totalTokens(200).durationMs(800).build());

        PipelineExecutor pipelineExec = new PipelineExecutor(chatService, null,
                Executors.newFixedThreadPool(4), 4, 60);

        // Build plan matching fixture
        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("plan-integration")
                .originalGoal("完整分析流程")
                .planSummary("清洗→并行采集→分析")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).mode(StepMode.SEQUENTIAL)
                                .agentName("data_cleaner").input("清洗数据").expectedOutput("清洗后数据").build(),
                        PlanStep.builder().stepIndex(1).mode(StepMode.PARALLEL)
                                .agentNames(List.of("web_scraper", "sql_analyst"))
                                .input("获取多源数据").expectedOutput("整合数据").build(),
                        PlanStep.builder().stepIndex(2).mode(StepMode.SEQUENTIAL)
                                .agentName("data_analyst").input("分析").expectedOutput("分析报告").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();

        // Execute all steps via PipelineExecutor
        for (PlanStep step : plan.getSteps()) {
            step.setStatus(com.suke.agent.core.models.StepStatus.RUNNING);
            StepResult result = pipelineExec.executeStep(step, 1L, "sess-integration");
            step.setActualOutput(result.getOutput());
            step.setDurationMs(result.getDurationMs());
            if (result.isParallel() && result.getOutputs() != null) {
                var parallelOutputs = new java.util.LinkedHashMap<String, String>();
                for (var e : result.getOutputs().entrySet()) {
                    parallelOutputs.put(e.getKey(), e.getValue().getOutput());
                }
                step.setOutputs(parallelOutputs);
            }
            step.setStatus(com.suke.agent.core.models.StepStatus.PASS);
            plan.advanceStep();
        }
        plan.markCompleted();

        // Assert against fixture
        assertEquals(fixture.getString("finalPlanStatus"), plan.getStatus().name());

        // Step 0 assertions
        PlanStep s0 = plan.getSteps().get(0);
        assertEquals(step0Expected.getString("actualOutput"), s0.getActualOutput());
        assertFalse(s0.isParallel());

        // Step 1 assertions
        PlanStep s1 = plan.getSteps().get(1);
        assertTrue(s1.isParallel());
        assertNotNull(s1.getOutputs());
        assertEquals(2, s1.getOutputs().size());
        JSONArray keywords = step1Expected.getJSONArray("containsKeywords");
        for (int i = 0; i < keywords.size(); i++) {
            assertTrue(s1.getActualOutput().contains(keywords.getString(i)),
                    "Parallel merged output should contain: " + keywords.getString(i));
        }

        // Step 2 assertions
        PlanStep s2 = plan.getSteps().get(2);
        assertEquals(step2Expected.getString("actualOutput"), s2.getActualOutput());
        assertFalse(s2.isParallel());
    }
}
