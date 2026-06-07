/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepStatus;
import com.suke.agent.core.sse.*;
import com.suke.agent.domain.entity.ExecutionPlan;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PlanExecuteIntegrationTest {

    private static JSONObject fixture;

    @BeforeAll
    static void loadFixture() throws Exception {
        try (InputStream is = PlanExecuteIntegrationTest.class.getClassLoader()
                .getResourceAsStream("fixture/plan_execute_expected.json")) {
            assertNotNull(is, "Fixture file not found");
            String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            fixture = JSON.parseObject(content);
        }
    }

    @Test
    void planStatusTransitions() {
        JSONObject expected = fixture.getJSONObject("planStatusTransitions");

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p1")
                .originalGoal("test goal")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0)
                                .agentName("data_analyst")
                                .input("analyze")
                                .expectedOutput("result")
                                .build()
                ))
                .status(PlanStatus.valueOf(expected.getString("initialStatus")))
                .build();

        assertEquals(PlanStatus.valueOf(expected.getString("initialStatus")), plan.getStatus());
        assertTrue(plan.hasMoreSteps());

        plan.advanceStep();
        assertEquals(PlanStatus.valueOf(expected.getString("expectedStatusAfterAdvance")), plan.getStatus());
        assertFalse(plan.hasMoreSteps());
        assertEquals(expected.getBoolean("expectedHasMoreSteps"), plan.hasMoreSteps());

        plan.markCompleted();
        assertEquals(PlanStatus.valueOf(expected.getString("expectedStatusAfterComplete")), plan.getStatus());
        assertEquals(StepStatus.valueOf(expected.getString("expectedStepStatusAfterComplete")),
                plan.getSteps().get(0).getStatus());
    }

    @Test
    void planFailureFlow() {
        JSONObject expected = fixture.getJSONObject("planFailureFlow");

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p2")
                .originalGoal("fail test")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0)
                                .agentName("data_analyst")
                                .input("test")
                                .expectedOutput("out")
                                .build()
                ))
                .status(PlanStatus.valueOf(expected.getString("initialStatus")))
                .build();

        plan.markFailed(expected.getString("failureReason"));
        assertEquals(PlanStatus.valueOf(expected.getString("expectedStatus")), plan.getStatus());
        assertEquals(StepStatus.valueOf(expected.getString("expectedStepStatus")),
                plan.getSteps().get(0).getStatus());
    }

    @Test
    void retryExhaustion() {
        JSONObject expected = fixture.getJSONObject("retryExhaustion");

        PlanStep step = PlanStep.builder()
                .stepIndex(0)
                .agentName("data_analyst")
                .input("test")
                .expectedOutput("result")
                .build();

        assertEquals(0, step.getRetryCount());

        List<Integer> expectedCounts = expected.getList("expectedRetryCounts", Integer.class);
        String expectedStatus = expected.getString("expectedStatusAfterEachRetry");

        for (int i = 0; i < expectedCounts.size(); i++) {
            step.incrementRetryCount();
            assertEquals(expectedCounts.get(i), step.getRetryCount());
            assertEquals(StepStatus.valueOf(expectedStatus), step.getStatus());
        }

        assertEquals(expected.getInteger("maxRetryCount").intValue(), step.getRetryCount());
    }

    @Test
    void replanCountLimit() {
        JSONObject expected = fixture.getJSONObject("replanCountLimit");
        List<Integer> expectedCounts = expected.getList("expectedCounts", Integer.class);

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p3")
                .originalGoal("replan test")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_analyst").input("test").expectedOutput("out").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();

        for (int i = 0; i < expectedCounts.size(); i++) {
            if (i > 0) plan.incrementReplanCount();
            assertEquals(expectedCounts.get(i).intValue(), plan.getReplanCount());
        }
    }

    @Test
    void intentResultSimpleVsComplex() {
        JSONObject expected = fixture.getJSONObject("intentResultSimpleVsComplex");

        JSONObject simpleExpected = expected.getJSONObject("simple");
        IntentRouter.IntentResult simple = new IntentRouter.IntentResult(
                simpleExpected.getString("agentName"),
                simpleExpected.getBoolean("complex"),
                simpleExpected.getString("reason")
        );
        assertEquals(simpleExpected.getString("agentName"), simple.agentName());
        assertFalse(simple.complex());
        assertEquals(simpleExpected.getString("reason"), simple.reason());

        JSONObject complexExpected = expected.getJSONObject("complex");
        IntentRouter.IntentResult complex = new IntentRouter.IntentResult(
                complexExpected.getString("agentName"),
                complexExpected.getBoolean("complex"),
                complexExpected.getString("reason")
        );
        assertTrue(complex.complex());
    }

    @Test
    void sseEventTypes() {
        JSONObject expected = fixture.getJSONObject("sseEventTypes");

        JSONObject planExpected = expected.getJSONObject("planEvent");
        PlanEvent planEvent = new PlanEvent(planExpected.getString("planId"), List.of(), planExpected.getString("summary"));
        assertEquals(planExpected.getString("type"), planEvent.type());
        assertEquals(planExpected.getString("planId"), planEvent.planId());
        assertEquals(planExpected.getString("summary"), planEvent.summary());

        JSONObject startExpected = expected.getJSONObject("stepStartEvent");
        StepStartEvent startEvent = new StepStartEvent(startExpected.getInteger("stepIndex"), startExpected.getString("agent"), "input");
        assertEquals(startExpected.getString("type"), startEvent.type());
        assertEquals(startExpected.getInteger("stepIndex").intValue(), startEvent.stepIndex());
        assertEquals(startExpected.getString("agent"), startEvent.agent());

        JSONObject resultExpected = expected.getJSONObject("stepResultEvent");
        StepResultEvent resultEvent = new StepResultEvent(resultExpected.getInteger("stepIndex"), resultExpected.getString("status"), resultExpected.getLong("durationMs"));
        assertEquals(resultExpected.getString("type"), resultEvent.type());
        assertEquals(resultExpected.getString("status"), resultEvent.status());
        assertEquals(resultExpected.getLong("durationMs").longValue(), resultEvent.durationMs());

        JSONObject retryExpected = expected.getJSONObject("stepRetryEvent");
        StepRetryEvent retryEvent = new StepRetryEvent(retryExpected.getInteger("stepIndex"), retryExpected.getInteger("retryCount"), "reason");
        assertEquals(retryExpected.getString("type"), retryEvent.type());
        assertEquals(retryExpected.getInteger("retryCount").intValue(), retryEvent.retryCount());

        JSONObject replanExpected = expected.getJSONObject("replanEvent");
        ReplanEvent replanEvent = new ReplanEvent(replanExpected.getString("reason"), List.of());
        assertEquals(replanExpected.getString("type"), replanEvent.type());
        assertEquals(replanExpected.getString("reason"), replanEvent.reason());
    }

    @Test
    void planValidation() {
        JSONObject expected = fixture.getJSONObject("planValidation");
        PlanExecutor executor = createPlanExecutor();

        // Valid plan
        JSONObject validExpected = expected.getJSONObject("validPlan");
        ExecutionPlan validPlan = ExecutionPlan.builder()
                .planId("valid")
                .steps(List.of(
                        PlanStep.builder().stepIndex(0)
                                .agentName(validExpected.getJSONArray("steps").getJSONObject(0).getString("agentName"))
                                .input("test").expectedOutput("out").build()
                ))
                .status(PlanStatus.RUNNING)
                .build();
        assertDoesNotThrow(() -> executor.validatePlan(validPlan));

        // Empty plan
        ExecutionPlan emptyPlan = ExecutionPlan.builder()
                .planId("empty")
                .steps(List.of())
                .status(PlanStatus.RUNNING)
                .build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(emptyPlan));

        // Too many steps
        JSONObject tooManyExpected = expected.getJSONObject("tooManySteps");
        List<PlanStep> tooMany = new ArrayList<>();
        for (int i = 0; i < tooManyExpected.getInteger("stepCount"); i++) {
            tooMany.add(PlanStep.builder().stepIndex(i).agentName("data_analyst").input("s" + i).expectedOutput("o").build());
        }
        ExecutionPlan bigPlan = ExecutionPlan.builder().planId("big").steps(tooMany).status(PlanStatus.RUNNING).build();
        assertThrows(IllegalArgumentException.class, () -> executor.validatePlan(bigPlan));
    }

    @Test
    void insertStepsAndReindex() {
        JSONObject expected = fixture.getJSONObject("insertStepsAndReindex");

        ExecutionPlan plan = ExecutionPlan.builder()
                .planId("p4")
                .originalGoal("test insert")
                .steps(new ArrayList<>(List.of(
                        PlanStep.builder().stepIndex(0).agentName("data_analyst").input("s0").expectedOutput("o0").build(),
                        PlanStep.builder().stepIndex(1).agentName("data_analyst").input("s1").expectedOutput("o1").build()
                )))
                .status(PlanStatus.RUNNING)
                .build();

        int insertAt = expected.getInteger("insertAtIndex");
        List<String> newAgents = expected.getList("newSteps", JSONObject.class)
                .stream().map(j -> j.getString("agentName")).toList();
        List<PlanStep> newSteps = new ArrayList<>();
        for (int i = 0; i < newAgents.size(); i++) {
            newSteps.add(PlanStep.builder().stepIndex(0).agentName(newAgents.get(i)).input("new" + i).expectedOutput("out").build());
        }

        plan.insertSteps(insertAt, newSteps);

        assertEquals(expected.getInteger("expectedTotalSteps").intValue(), plan.getSteps().size());
        List<String> expectedOrder = expected.getList("expectedOrder", String.class);
        for (int i = 0; i < expectedOrder.size(); i++) {
            assertEquals(expectedOrder.get(i), plan.getSteps().get(i).getAgentName(),
                    "Step " + i + " agentName mismatch");
            assertEquals(i, plan.getSteps().get(i).getStepIndex(),
                    "Step " + i + " stepIndex mismatch");
        }
    }

    /**
     * Create a PlanExecutor with a mock AgentOrchestrator that returns canned responses.
     * Uses reflection to bypass the 11-parameter AgentOrchestrator constructor and
     * the @Qualifier-annotated PlanExecutor constructor.
     */
    private PlanExecutor createPlanExecutor() {
        try {
            // Create a mock AgentOrchestrator via reflection (all fields null)
            Constructor<AgentOrchestrator> orchCtor = AgentOrchestrator.class.getDeclaredConstructor(
                    AgentRegistry.class, com.suke.agent.trace.AgentTraceService.class,
                    HandoffManager.class, com.suke.agent.memory.LongTermMemoryStore.class,
                    com.suke.agent.skill.SkillManager.class, com.suke.agent.memory.UserBehaviorTracker.class,
                    com.suke.agent.memory.ConversationHistoryManager.class,
                    com.suke.agent.memory.TopicExtractor.class,
                    com.suke.agent.memory.WorkingMemory.class,
                    IntentRouter.class);
            AgentOrchestrator mockOrch = orchCtor.newInstance(
                    null, null, null, null, null, null, null, null, null, null);

            // Create PlanExecutor with mock AgentChatService and null ChatClient
            Constructor<PlanExecutor> ctor = PlanExecutor.class.getDeclaredConstructor(
                    AgentChatService.class, AgentRegistry.class, org.springframework.ai.chat.client.ChatClient.class, java.util.concurrent.Executor.class);
            return ctor.newInstance(mockOrch, null, null, null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create PlanExecutor via reflection", e);
        }
    }
}
