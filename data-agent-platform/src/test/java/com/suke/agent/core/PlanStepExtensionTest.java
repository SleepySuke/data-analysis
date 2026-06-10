/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.core.models.StepMode;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanStepExtensionTest {

    @Test
    void sequentialStepHasModeField() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .agentName("data_analyst")
                .mode(StepMode.SEQUENTIAL)
                .input("分析数据")
                .expectedOutput("分析报告")
                .build();
        assertEquals(StepMode.SEQUENTIAL, step.getMode());
        assertEquals("data_analyst", step.getAgentName());
    }

    @Test
    void parallelStepHasAgentNamesAndOutputs() {
        PlanStep step = PlanStep.builder()
                .stepIndex(1)
                .mode(StepMode.PARALLEL)
                .agentNames(List.of("web_scraper", "sql_analyst"))
                .input("获取多源数据")
                .expectedOutput("整合数据报告")
                .build();
        assertEquals(StepMode.PARALLEL, step.getMode());
        assertEquals(2, step.getAgentNames().size());
        assertTrue(step.getAgentNames().contains("web_scraper"));
        assertNull(step.getOutputs());
    }

    @Test
    void parallelStepStoresOutputs() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .mode(StepMode.PARALLEL)
                .agentNames(List.of("a1", "a2"))
                .input("test")
                .expectedOutput("result")
                .build();
        step.setOutputs(Map.of("a1", "output1", "a2", "output2"));
        assertEquals("output1", step.getOutputs().get("a1"));
        assertEquals("output2", step.getOutputs().get("a2"));
    }

    @Test
    void defaultModeIsSequential() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .agentName("data_analyst")
                .input("test")
                .expectedOutput("result")
                .build();
        assertEquals(StepMode.SEQUENTIAL, step.getMode());
    }

    @Test
    void isParallelHelperMethod() {
        PlanStep parallel = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL).input("t").expectedOutput("r").build();
        PlanStep sequential = PlanStep.builder()
                .stepIndex(0).mode(StepMode.SEQUENTIAL).agentName("a").input("t").expectedOutput("r").build();
        assertTrue(parallel.isParallel());
        assertFalse(sequential.isParallel());
    }
}
