/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description Plan-Execute-Loop 编排执行器（确定性 Harness 层）
 */
package com.suke.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.models.EvalResult;
import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepResult;
import com.suke.agent.core.models.StepStatus;
import com.suke.agent.core.sse.*;
import com.suke.agent.domain.entity.ExecutionPlan;
import com.suke.agent.domain.entity.PlanStep;
import com.suke.agent.prompt.AgentPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class PlanExecutor {

    private static final int MAX_STEPS = 10;
    private static final int MAX_ITERATIONS = 10;
    private static final int MAX_RETRY_PER_STEP = 3;
    private static final int MAX_REPLAN_COUNT = 2;
    private static final int TOKEN_BUDGET = 50000;
    private static final long STEP_TIMEOUT_MS = 60_000;

    private final AgentChatService chatService;
    private final AgentRegistry agentRegistry;
    private final ChatClient deepseekClient;
    private final Executor executor;
    private final PipelineExecutor pipelineExecutor;

    public PlanExecutor(@Qualifier("agentChatService")AgentChatService chatService,
                        AgentRegistry agentRegistry,
                        @Qualifier("deepseekClient") ChatClient deepseekClient,
                         @Qualifier("planExecutor") Executor executor,
                        PipelineExecutor pipelineExecutor) {
        this.chatService = chatService;
        this.agentRegistry = agentRegistry;
        this.deepseekClient = deepseekClient;
        this.executor = executor;
        this.pipelineExecutor = pipelineExecutor;
    }

    public void validatePlan(ExecutionPlan plan) {
        Objects.requireNonNull(plan, "执行计划不能为null");
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new IllegalArgumentException("计划不能为空");
        }
        if (plan.getSteps().size() > MAX_STEPS) {
            throw new IllegalArgumentException("计划步骤数不能超过 " + MAX_STEPS);
        }
        for (PlanStep step : plan.getSteps()) {
            if (step.isParallel()) {
                if (step.getAgentNames() == null || step.getAgentNames().isEmpty()) {
                    throw new IllegalArgumentException("并行步骤 " + step.getStepIndex() + " 缺少 agentNames");
                }
                if (agentRegistry != null) {
                    for (String name : step.getAgentNames()) {
                        if (!agentRegistry.exists(name)) {
                            throw new IllegalArgumentException("并行步骤 " + step.getStepIndex() + " 的 Agent 不存在: " + name);
                        }
                    }
                }
            } else {
                if (step.getAgentName() == null || step.getAgentName().isBlank()) {
                    throw new IllegalArgumentException("步骤 " + step.getStepIndex() + " 的 Agent 名称为空");
                }
                if (agentRegistry != null && !agentRegistry.exists(step.getAgentName())) {
                    throw new IllegalArgumentException("步骤 " + step.getStepIndex() + " 的 Agent 不存在: " + step.getAgentName());
                }
            }
        }
    }

    public ExecutionPlan createPlan(String message, String planId) {
        String prompt = AgentPrompts.PLAN_SUPERVISOR
                + "\n\n--- 用户需求开始 ---\n" + message
                + "\n--- 用户需求结束 ---\n"
                + "\n请输出执行计划JSON：";

        try {
            String result = CompletableFuture.supplyAsync(() ->
                            deepseekClient.prompt().user(prompt).call().content(), executor)
                    .get(60, TimeUnit.SECONDS);
            return parsePlanResult(result, message, planId);
        } catch (TimeoutException e) {
            throw new RuntimeException("规划超时(60s)，请简化需求后重试", e);
        } catch (Exception e) {
            throw new RuntimeException("规划失败: " + e.getMessage(), e);
        }
    }

    ExecutionPlan parsePlanResult(String llmOutput, String originalGoal, String planId) {
        String jsonStr = extractJson(llmOutput);
        JSONObject parsed = JSON.parseObject(jsonStr);

        String summary = parsed.getString("planSummary");
        JSONArray stepsArr = parsed.getJSONArray("steps");

        if (stepsArr == null || stepsArr.isEmpty()) {
            throw new IllegalArgumentException("LLM 返回的计划步骤为空");
        }

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < stepsArr.size(); i++) {
            JSONObject s = stepsArr.getJSONObject(i);

            StepMode mode = "parallel".equalsIgnoreCase(s.getString("mode"))
                    ? StepMode.PARALLEL : StepMode.SEQUENTIAL;

            JSONArray agentsArr = s.getJSONArray("agents");
            List<String> agentNames = null;
            if (agentsArr != null && !agentsArr.isEmpty()) {
                agentNames = new ArrayList<>();
                for (int j = 0; j < agentsArr.size(); j++) {
                    agentNames.add(agentsArr.getString(j));
                }
            }

            steps.add(PlanStep.builder()
                    .stepIndex(i)
                    .mode(mode)
                    .agentName(s.getString("agentName"))
                    .agentNames(agentNames)
                    .input(s.getString("input"))
                    .expectedOutput(s.getString("expectedOutput"))
                    .build());
        }

        return ExecutionPlan.builder()
                .planId(planId)
                .originalGoal(originalGoal)
                .planSummary(summary)
                .steps(steps)
                .status(PlanStatus.RUNNING)
                .build();
    }

    public EvalResult evaluateStep(PlanStep step, ExecutionPlan plan) {
        String prompt = AgentPrompts.STEP_EVALUATOR
                + "\n\n--- 步骤信息开始 ---\n"
                + "步骤目标: " + step.getInput() + "\n"
                + "预期输出: " + step.getExpectedOutput() + "\n"
                + "实际输出: " + (step.getActualOutput() != null
                    ? step.getActualOutput().substring(0, Math.min(step.getActualOutput().length(), 2000))
                    : "无输出")
                + "\n--- 步骤信息结束 ---\n"
                + "\n请评估：";

        try {
            String result = CompletableFuture.supplyAsync(() ->
                            deepseekClient.prompt().user(prompt).call().content(), executor)
                    .get(30, TimeUnit.SECONDS);
            if (result == null || result.isBlank()) {
                log.warn("评估返回空结果，默认RETRY");
                return EvalResult.RETRY;
            }
            return parseEvalResult(result);
        } catch (TimeoutException e) {
            log.warn("评估超时(30s)，默认RETRY");
            return EvalResult.RETRY;
        } catch (Exception e) {
            log.warn("评估失败，默认RETRY: {}", e.getMessage());
            return EvalResult.RETRY;
        }
    }

    EvalResult parseEvalResult(String llmOutput) {
        String jsonStr = extractJson(llmOutput);
        JSONObject parsed = JSON.parseObject(jsonStr);
        String result = parsed.getString("result");
        try {
            return EvalResult.valueOf(result.toUpperCase());
        } catch (Exception e) {
            log.warn("无法解析评估结果 '{}', 默认PASS", result);
            return EvalResult.PASS;
        }
    }

    public AgentResponse executeLoop(String message, Long userId, String sessionId) {
        String planId = "plan-" + UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        int totalTokens = 0;

        ExecutionPlan plan = createPlan(message, planId);
        validatePlan(plan);

        log.info("[Plan:{}] Created plan with {} steps: {}", planId, plan.getSteps().size(), plan.getPlanSummary());

        int iterations = 0;
        try {
            while (plan.getStatus() == PlanStatus.RUNNING && plan.hasMoreSteps()) {
                if (++iterations > MAX_ITERATIONS) {
                    plan.markFailed("超过最大迭代次数");
                    break;
                }

                PlanStep step = plan.currentStep();
                step.setStatus(StepStatus.RUNNING);

                log.info("[Plan:{}] Executing step {}: {} -> {}", planId, step.getStepIndex(), displayName(step), step.getInput());

                StepResult stepResult;
                try {
                    stepResult = CompletableFuture.supplyAsync(() ->
                                    pipelineExecutor.executeStep(step, userId, sessionId), executor)
                            .get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    step.setActualOutput("步骤执行超时");
                    step.setDurationMs(STEP_TIMEOUT_MS);
                    step.setStatus(StepStatus.FAIL);
                    plan.markFailed("步骤 " + step.getStepIndex() + " 执行超时 (" + (STEP_TIMEOUT_MS/1000) + "s)");
                    break;
                } catch (Exception e) {
                    throw new RuntimeException("步骤执行异常", e);
                }
                step.setActualOutput(stepResult.getOutput());
                step.setDurationMs(stepResult.getDurationMs());
                if (stepResult.isParallel() && stepResult.getOutputs() != null) {
                    Map<String, String> parallelOutputs = new LinkedHashMap<>();
                    for (var entry : stepResult.getOutputs().entrySet()) {
                        parallelOutputs.put(entry.getKey(), entry.getValue().getOutput());
                    }
                    step.setOutputs(parallelOutputs);
                }
                totalTokens += stepResult.getTotalTokens();

                if (totalTokens > TOKEN_BUDGET) {
                    plan.markFailed("token 预算超限 (" + totalTokens + " > " + TOKEN_BUDGET + ")");
                    break;
                }

                EvalResult eval = evaluateStep(step, plan);
                log.info("[Plan:{}] Step {} evaluated as {}", planId, step.getStepIndex(), eval);

                switch (eval) {
                    case PASS -> plan.advanceStep();
                    case RETRY -> retryStep(plan, step);
                    case REPLAN -> replan(plan);
                    case FAIL -> {
                        step.setStatus(StepStatus.FAIL);
                        plan.markFailed("步骤评估为 FAIL: " + eval.getReason());
                    }
                }
            }

            if (plan.getStatus() == PlanStatus.RUNNING) {
                plan.markCompleted();
            }

        } catch (Exception e) {
            log.error("[Plan:{}] Execution failed: {}", planId, e.getMessage(), e);
            plan.markFailed(e.getMessage());
        }

        long durationMs = System.currentTimeMillis() - startTime;
        return buildSummary(plan, totalTokens, durationMs);
    }

    public Flux<SseEvent> executeLoopStream(String message, Long userId, String sessionId) {
        String planId = "plan-" + UUID.randomUUID().toString();

        return Flux.<SseEvent>create(sink -> {
            long startTime = System.currentTimeMillis();
            AtomicInteger totalTokens = new AtomicInteger(0);

            try {
                // 1. Plan
                ExecutionPlan plan = createPlan(message, planId);
                validatePlan(plan);

                log.info("[Plan:{}] Created streaming plan with {} steps", planId, plan.getSteps().size());

                // Emit plan_created
                sink.next(new PlanEvent(planId,
                        plan.getSteps().stream()
                                .map(s -> new PlanEvent.PlanStepSummary(s.getStepIndex(),
                                        s.isParallel() && s.getAgentNames() != null
                                                ? String.join(",", s.getAgentNames()) : s.getAgentName(),
                                        s.getInput()))
                                .toList(),
                        plan.getPlanSummary()));

                int iterations = 0;
                while (plan.getStatus() == PlanStatus.RUNNING && plan.hasMoreSteps()) {
                    if (++iterations > MAX_ITERATIONS) {
                        plan.markFailed("超过最大迭代次数");
                        break;
                    }

                    PlanStep step = plan.currentStep();
                    step.setStatus(StepStatus.RUNNING);

                    // Emit step_start
                    if (step.isParallel()) {
                        sink.next(new ParallelStepStartEvent(step.getStepIndex(), "parallel",
                                step.getAgentNames(), step.getInput()));
                    } else {
                        sink.next(new StepStartEvent(step.getStepIndex(), step.getAgentName(), step.getInput()));
                    }

                    // Execute step with timeout
                    StepResult stepResult;
                    try {
                        stepResult = CompletableFuture.supplyAsync(() ->
                                        pipelineExecutor.executeStep(step, userId, sessionId), executor)
                                .get(STEP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    } catch (TimeoutException e) {
                        step.setActualOutput("步骤执行超时");
                        step.setDurationMs(STEP_TIMEOUT_MS);
                        step.setStatus(StepStatus.FAIL);
                        plan.markFailed("步骤 " + step.getStepIndex() + " 执行超时");
                        if (step.isParallel()) {
                            sink.next(new ParallelStepResultEvent(step.getStepIndex(), "fail", step.getDurationMs(),
                                    null, "并行步骤超时"));
                        } else {
                            sink.next(new StepResultEvent(step.getStepIndex(), "fail", step.getDurationMs()));
                        }
                        break;
                    } catch (Exception e) {
                        throw new RuntimeException("步骤执行异常", e);
                    }

                    step.setActualOutput(stepResult.getOutput());
                    step.setDurationMs(stepResult.getDurationMs());
                    if (stepResult.isParallel() && stepResult.getOutputs() != null) {
                        Map<String, String> parallelOutputs = new LinkedHashMap<>();
                        for (var entry : stepResult.getOutputs().entrySet()) {
                            parallelOutputs.put(entry.getKey(), entry.getValue().getOutput());
                        }
                        step.setOutputs(parallelOutputs);
                    }
                    totalTokens.addAndGet(stepResult.getTotalTokens());

                    if (totalTokens.get() > TOKEN_BUDGET) {
                        plan.markFailed("token 预算超限");
                        if (step.isParallel()) {
                            sink.next(new ParallelStepResultEvent(step.getStepIndex(), "fail", step.getDurationMs(),
                                    step.getOutputs(), "token 预算超限"));
                        } else {
                            sink.next(new StepResultEvent(step.getStepIndex(), "fail", step.getDurationMs()));
                        }
                        break;
                    }

                    EvalResult eval = evaluateStep(step, plan);

                    switch (eval) {
                        case PASS -> {
                            plan.advanceStep();
                            if (step.isParallel()) {
                                sink.next(new ParallelStepResultEvent(step.getStepIndex(), "pass", step.getDurationMs(),
                                        step.getOutputs(), step.getActualOutput()));
                            } else {
                                sink.next(new StepResultEvent(step.getStepIndex(), "pass", step.getDurationMs()));
                            }
                        }
                        case RETRY -> {
                            if (step.getRetryCount() >= MAX_RETRY_PER_STEP) {
                                step.setStatus(StepStatus.FAIL);
                                plan.markFailed("步骤 " + step.getStepIndex() + " 重试次数超限");
                                if (step.isParallel()) {
                                    sink.next(new ParallelStepResultEvent(step.getStepIndex(), "fail", step.getDurationMs(),
                                            step.getOutputs(), "重试次数超限"));
                                } else {
                                    sink.next(new StepResultEvent(step.getStepIndex(), "fail", step.getDurationMs()));
                                }
                            } else {
                                sink.next(new StepRetryEvent(step.getStepIndex(), step.getRetryCount() + 1, eval.getReason()));
                                retryStep(plan, step);
                            }
                        }
                        case REPLAN -> {
                            if (step.isParallel()) {
                                sink.next(new ParallelStepResultEvent(step.getStepIndex(), "replan", step.getDurationMs(),
                                        step.getOutputs(), step.getActualOutput()));
                            } else {
                                sink.next(new StepResultEvent(step.getStepIndex(), "replan", step.getDurationMs()));
                            }
                            replan(plan);
                            if (plan.getStatus() == PlanStatus.RUNNING) {
                                sink.next(new ReplanEvent("步骤评估需要重新规划",
                                        plan.getSteps().stream()
                                                .skip(plan.getCurrentStepIndex())
                                                .map(s -> new ReplanEvent.PlanStepSummary(s.getStepIndex(), displayName(s), s.getInput()))
                                                .toList()));
                            }
                        }
                        case FAIL -> {
                            step.setStatus(StepStatus.FAIL);
                            plan.markFailed("步骤评估为 FAIL");
                            if (step.isParallel()) {
                                sink.next(new ParallelStepResultEvent(step.getStepIndex(), "fail", step.getDurationMs(),
                                        step.getOutputs(), "步骤评估为 FAIL"));
                            } else {
                                sink.next(new StepResultEvent(step.getStepIndex(), "fail", step.getDurationMs()));
                            }
                        }
                    }
                }

                if (plan.getStatus() == PlanStatus.RUNNING) {
                    plan.markCompleted();
                }

                long durationMs = System.currentTimeMillis() - startTime;
                AgentResponse summary = buildSummary(plan, totalTokens.get(), durationMs);
                sink.next(new DoneEvent(summary.getTraceId(), totalTokens.get(), durationMs));

            } catch (Exception e) {
                log.error("[Plan:{}] Streaming execution failed: {}", planId, e.getMessage(), e);
                try {
                    sink.next(new ErrorEvent("执行失败"));
                } catch (Exception ignored) {}
            } finally {
                sink.complete();
            }
        });
    }

    private void retryStep(ExecutionPlan plan, PlanStep step) {
        if (step.getRetryCount() >= MAX_RETRY_PER_STEP) {
            step.setStatus(StepStatus.FAIL);
            plan.markFailed("步骤 " + step.getStepIndex() + " 重试次数超限 (" + MAX_RETRY_PER_STEP + ")");
            return;
        }
        step.incrementRetryCount();
        step.setActualOutput(null);
        step.setDurationMs(0);
    }

    private void replan(ExecutionPlan plan) {
        if (plan.getReplanCount() >= MAX_REPLAN_COUNT) {
            plan.markFailed("重规划次数超限 (" + MAX_REPLAN_COUNT + ")");
            return;
        }
        plan.incrementReplanCount();
        String remainingGoal = buildRemainingGoal(plan);
        ExecutionPlan newPlan = createPlan(remainingGoal, plan.getPlanId());
        List<PlanStep> newSteps = new ArrayList<>(plan.getSteps().subList(0, plan.getCurrentStepIndex()));
        newSteps.addAll(newPlan.getSteps());
        plan.setSteps(newSteps);
        for (int i = 0; i < plan.getSteps().size(); i++) {
            plan.getSteps().get(i).setStepIndex(i);
        }
        if (plan.getSteps().size() > MAX_STEPS) {
            plan.markFailed("重规划后步骤数超过限制 (" + plan.getSteps().size() + " > " + MAX_STEPS + ")");
            return;
        }
    }

    private String buildRemainingGoal(ExecutionPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("原始目标: ").append(plan.getOriginalGoal()).append("\n\n");
        sb.append("--- 已完成步骤开始 ---\n");
        for (int i = 0; i < plan.getCurrentStepIndex(); i++) {
            PlanStep s = plan.getSteps().get(i);
            sb.append("- ").append(displayName(s)).append(": ").append(s.getInput())
                    .append(" → ").append(s.getActualOutput() != null ? s.getActualOutput().substring(0, Math.min(s.getActualOutput().length(), 200)) : "无输出")
                    .append("\n");
        }
        sb.append("--- 已完成步骤结束 ---\n\n");
        PlanStep current = plan.currentStep();
        if (current == null) {
            sb.append("当前无活跃步骤\n");
        } else {
            sb.append("--- 当前失败步骤开始 ---\n");
            sb.append(displayName(current)).append(": ").append(current.getInput()).append("\n");
            sb.append("--- 当前失败步骤结束 ---\n\n");
        }
        sb.append("请重新规划后续步骤：");
        return sb.toString();
    }

    private AgentResponse buildSummary(ExecutionPlan plan, int totalTokens, long durationMs) {
        StringBuilder output = new StringBuilder();
        output.append("计划执行").append(plan.getStatus() == PlanStatus.COMPLETED ? "完成" : "失败").append("\n\n");
        output.append("目标: ").append(plan.getOriginalGoal()).append("\n");
        output.append("计划摘要: ").append(plan.getPlanSummary()).append("\n");
        if (plan.getFailureReason() != null) {
            output.append("失败原因: ").append(plan.getFailureReason()).append("\n");
        }
        output.append("\n");

        for (PlanStep step : plan.getSteps()) {
            output.append("步骤 ").append(step.getStepIndex() + 1).append(": ");
            output.append("[").append(step.getStatus()).append("] ");
            output.append(displayName(step)).append(" - ").append(step.getInput());
            if (step.getActualOutput() != null) {
                output.append("\n  输出: ").append(step.getActualOutput(), 0, Math.min(step.getActualOutput().length(), 300));
            }
            output.append("\n");
        }

        // Note: output contains LLM-generated text; client must HTML-escape before rendering
        return AgentResponse.builder()
                .output(output.toString())
                .traceId(plan.getPlanId())
                .totalTokens(totalTokens)
                .durationMs(durationMs)
                .status(plan.getStatus() == PlanStatus.COMPLETED ? "success" : "failed")
                .sourceAgent("plan_executor")
                .build();
    }

    private String displayName(PlanStep s) {
        if (s.isParallel() && s.getAgentNames() != null) {
            return String.join(",", s.getAgentNames());
        }
        return s.getAgentName() != null ? s.getAgentName() : "unknown";
    }

    String extractJson(String text) {
        if (text == null) return "{}";
        int codeStart = text.indexOf("```json");
        if (codeStart >= 0) {
            int jsonStart = codeStart + 7;
            int codeEnd = text.indexOf("```", jsonStart);
            if (codeEnd > jsonStart) {
                return text.substring(jsonStart, codeEnd).trim();
            }
        }
        codeStart = text.indexOf("```");
        if (codeStart >= 0) {
            int jsonStart = text.indexOf('\n', codeStart) + 1;
            int codeEnd = text.indexOf("```", jsonStart);
            if (codeEnd > jsonStart) {
                return text.substring(jsonStart, codeEnd).trim();
            }
        }
        int braceStart = text.indexOf('{');
        if (braceStart >= 0) {
            int depth = 0;
            for (int i = braceStart; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') depth--;
                if (depth == 0) {
                    return text.substring(braceStart, i + 1);
                }
            }
        }
        return "{}";
    }
}
