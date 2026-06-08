/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 串行/并行步骤执行器，为 PlanExecutor 提供执行策略
 */
package com.suke.agent.core;

import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepResult;
import com.suke.agent.domain.entity.PlanStep;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;

import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class PipelineExecutor {

    private final AgentChatService chatService;
    private final ChatClient mergeClient;
    private final ExecutorService parallelExecutor;
    private final int maxParallelAgents;
    private final int parallelTimeoutSeconds;

    public PipelineExecutor(AgentChatService chatService,
                            ChatClient mergeClient,
                            ExecutorService parallelExecutor,
                            int maxParallelAgents,
                            int parallelTimeoutSeconds) {
        this.chatService = chatService;
        this.mergeClient = mergeClient;
        this.parallelExecutor = parallelExecutor;
        this.maxParallelAgents = maxParallelAgents;
        this.parallelTimeoutSeconds = parallelTimeoutSeconds;
    }

    public StepResult executeStep(PlanStep step, Long userId, String sessionId) {
        return switch (step.getMode()) {
            case SEQUENTIAL -> executeSequential(step, userId, sessionId);
            case PARALLEL -> executeParallel(step, userId, sessionId);
        };
    }

    private StepResult executeSequential(PlanStep step, Long userId, String sessionId) {
        long start = System.currentTimeMillis();
        try {
            AgentResponse response = chatService.directCall(
                    step.getAgentName(), step.getInput(), userId, sessionId);
            return StepResult.sequential(response.getOutput(), response.getStatus(),
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Sequential step {} failed: {}", step.getStepIndex(), e.getMessage(), e);
            return StepResult.failed("步骤执行异常");
        }
    }

    private StepResult executeParallel(PlanStep step, Long userId, String sessionId) {
        List<String> agents = step.getAgentNames();
        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException("并行步骤 " + step.getStepIndex() + " 缺少 agentNames");
        }
        if (agents.size() > maxParallelAgents) {
            throw new IllegalArgumentException(
                    "并行步骤 " + step.getStepIndex() + " 的 agent 数量 (" + agents.size()
                            + ") 超过上限 (" + maxParallelAgents + ")");
        }

        long start = System.currentTimeMillis();
        Map<String, CompletableFuture<AgentResponse>> futures = new LinkedHashMap<>();
        for (String agentName : agents) {
            String subSession = sessionId + "-sub-" + agentName;
            futures.put(agentName, CompletableFuture.supplyAsync(
                    () -> {
                        try {
                            return chatService.directCall(agentName, step.getInput(), userId, subSession);
                        } catch (Exception e) {
                            log.warn("Parallel agent {} failed: {}", agentName, e.getMessage());
                            return AgentResponse.builder()
                                    .output("执行失败")
                                    .status("failed")
                                    .totalTokens(0)
                                    .durationMs(0)
                                    .build();
                        }
                    },
                    parallelExecutor));
        }

        boolean timedOut = false;
        try {
            CompletableFuture.allOf(futures.values().toArray(new CompletableFuture[0]))
                    .get(parallelTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            timedOut = true;
            log.warn("Parallel step {} timed out after {}s", step.getStepIndex(), parallelTimeoutSeconds);
            futures.values().forEach(f -> f.cancel(true));
        } catch (Exception e) {
            log.error("Parallel step {} execution error: {}", step.getStepIndex(), e.getMessage(), e);
        }

        if (timedOut) {
            return StepResult.failed("并行步骤超时 (" + parallelTimeoutSeconds + "s)");
        }

        Map<String, StepResult.AgentOutput> outputs = new LinkedHashMap<>();
        for (var entry : futures.entrySet()) {
            try {
                AgentResponse resp = entry.getValue().getNow(AgentResponse.builder()
                        .output("未完成").status("failed").totalTokens(0).durationMs(0).build());
                outputs.put(entry.getKey(), new StepResult.AgentOutput(
                        resp.getOutput(), resp.getStatus(), resp.getDurationMs(), resp.getTotalTokens()));
            } catch (Exception e) {
                outputs.put(entry.getKey(), new StepResult.AgentOutput("执行异常", "failed", 0, 0));
            }
        }

        String merged = mergeParallelOutputs(outputs);
        long durationMs = System.currentTimeMillis() - start;
        boolean hasFailure = outputs.values().stream().anyMatch(o -> "failed".equals(o.getStatus()));
        String status = hasFailure ? "partial" : "success";

        return StepResult.parallel(merged, status, durationMs, outputs);
    }

    private String mergeParallelOutputs(Map<String, StepResult.AgentOutput> outputs) {
        StringBuilder sb = new StringBuilder();
        for (var entry : outputs.entrySet()) {
            sb.append("## ").append(entry.getKey()).append(" 的结果:\n");
            sb.append(entry.getValue().getOutput()).append("\n\n");
        }
        if (mergeClient != null) {
            try {
                String mergePrompt = "以下是多个 Agent 并行执行的结果，请整合为一个连贯的分析报告，保留各来源的关键信息：\n\n"
                        + sb;
                return mergeClient.prompt().user(mergePrompt).call().content();
            } catch (Exception e) {
                log.warn("LLM merge failed, falling back to concatenation: {}", e.getMessage());
            }
        }
        return sb.toString();
    }
}
