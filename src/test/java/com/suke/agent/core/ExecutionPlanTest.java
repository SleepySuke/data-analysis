/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepStatus;
import com.suke.agent.domain.entity.ExecutionPlan;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

class ExecutionPlanTest {

    @Test
    void planTracksCurrentStep() {
        ExecutionPlan plan = createThreeStepPlan();
        assertEquals(0, plan.getCurrentStepIndex());
        assertTrue(plan.hasMoreSteps());

        plan.advanceStep();
        assertEquals(1, plan.getCurrentStepIndex());

        plan.advanceStep();
        assertEquals(2, plan.getCurrentStepIndex());

        plan.advanceStep();
        assertFalse(plan.hasMoreSteps());
    }

    @Test
    void planMarkCompleted() {
        ExecutionPlan plan = createThreeStepPlan();
        plan.advanceStep();
        plan.advanceStep();
        plan.advanceStep();
        plan.markCompleted();
        assertEquals(PlanStatus.COMPLETED, plan.getStatus());
    }

    @Test
    void planMarkFailed() {
        ExecutionPlan plan = createThreeStepPlan();
        plan.markFailed("timeout");
        assertEquals(PlanStatus.FAILED, plan.getStatus());
    }

    @Test
    void planCountsReplans() {
        ExecutionPlan plan = createThreeStepPlan();
        assertEquals(0, plan.getReplanCount());
        plan.incrementReplanCount();
        assertEquals(1, plan.getReplanCount());
        plan.incrementReplanCount();
        assertEquals(2, plan.getReplanCount());
    }

    @Test
    void stepTracksRetry() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .agentName("data_analyst")
                .input("分析CSV")
                .expectedOutput("分析结论")
                .build();

        assertEquals(0, step.getRetryCount());
        step.incrementRetryCount();
        assertEquals(1, step.getRetryCount());
        assertEquals(StepStatus.RETRY, step.getStatus());
    }

    @Test
    void stepRecordsOutput() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .agentName("data_analyst")
                .input("分析CSV")
                .expectedOutput("分析结论")
                .build();

        assertNull(step.getActualOutput());
        step.setActualOutput("结论：数据呈上升趋势");
        step.setDurationMs(1500);
        assertEquals("结论：数据呈上升趋势", step.getActualOutput());
        assertEquals(1500, step.getDurationMs());
    }

    @Test
    void insertStepsReindexes() {
        ExecutionPlan plan = createThreeStepPlan();
        // Insert 2 new steps at index 1
        List<PlanStep> newSteps = List.of(
                PlanStep.builder().stepIndex(0).agentName("web_scraper").input("抓取数据").expectedOutput("网页数据").build(),
                PlanStep.builder().stepIndex(0).agentName("data_cleaner").input("清洗抓取数据").expectedOutput("清洗后数据").build()
        );
        plan.insertSteps(1, newSteps);
        // Should now have 5 steps, all re-indexed
        assertEquals(5, plan.getSteps().size());
        assertEquals(0, plan.getSteps().get(0).getStepIndex());
        assertEquals(1, plan.getSteps().get(1).getStepIndex());
        assertEquals(4, plan.getSteps().get(4).getStepIndex());
        // Original step at index 0 still data_cleaner
        assertEquals("data_cleaner", plan.getSteps().get(0).getAgentName());
        // New steps at 1 and 2
        assertEquals("web_scraper", plan.getSteps().get(1).getAgentName());
    }

    @Test
    void markFailedStoresReason() {
        ExecutionPlan plan = createThreeStepPlan();
        plan.markFailed("timeout exceeded");
        assertEquals(PlanStatus.FAILED, plan.getStatus());
        assertEquals("timeout exceeded", plan.getFailureReason());
    }

    @Test
    void replanCountExceedsLimit() {
        ExecutionPlan plan = createThreeStepPlan();
        plan.incrementReplanCount();
        plan.incrementReplanCount();
        assertEquals(2, plan.getReplanCount());
        // PlanExecutor.replan() checks replanCount >= MAX_REPLAN_COUNT (2) and marks failed
        // This test verifies the data model supports tracking replan count correctly
    }

    private ExecutionPlan createThreeStepPlan() {
        return ExecutionPlan.builder()
                .planId("plan-001")
                .originalGoal("先清洗数据，然后分析趋势，最后生成图表")
                .planSummary("三步分析计划")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_cleaner").input("清洗数据").expectedOutput("清洗后数据").build(),
                        PlanStep.builder().stepIndex(1).agentName("data_analyst").input("分析趋势").expectedOutput("趋势分析").build(),
                        PlanStep.builder().stepIndex(2).agentName("data_analyst").input("生成图表").expectedOutput("ECharts JSON").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();
    }
}
