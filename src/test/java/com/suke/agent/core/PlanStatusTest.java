/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.suke.agent.core.models.EvalResult;
import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepStatus;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlanStatusTest {

    @Test
    void allPlanStatusesExist() {
        assertNotNull(PlanStatus.PLANNING);
        assertNotNull(PlanStatus.RUNNING);
        assertNotNull(PlanStatus.COMPLETED);
        assertNotNull(PlanStatus.FAILED);
        assertEquals(4, PlanStatus.values().length);
    }

    @Test
    void allStepStatusesExist() {
        assertNotNull(StepStatus.PENDING);
        assertNotNull(StepStatus.RUNNING);
        assertNotNull(StepStatus.PASS);
        assertNotNull(StepStatus.FAIL);
        assertNotNull(StepStatus.RETRY);
        assertNotNull(StepStatus.SKIPPED);
        assertEquals(6, StepStatus.values().length);
    }

    @Test
    void allEvalResultsExist() {
        assertNotNull(EvalResult.PASS);
        assertNotNull(EvalResult.RETRY);
        assertNotNull(EvalResult.REPLAN);
        assertNotNull(EvalResult.FAIL);
        assertEquals(4, EvalResult.values().length);
    }

    @Test
    void evalResultHasReason() {
        EvalResult result = EvalResult.REPLAN;
        assertNotNull(result.getReason());
        assertFalse(result.getReason().isEmpty());
    }
}
