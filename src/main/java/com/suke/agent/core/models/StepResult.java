/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 步骤执行结果封装
 */
package com.suke.agent.core.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {

    private String output;
    private String status;
    private long durationMs;
    private Map<String, AgentOutput> outputs;
    private boolean parallel;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AgentOutput {
        private String output;
        private String status;
        private long durationMs;
        private int totalTokens;
    }

    public static StepResult sequential(String output, String status, long durationMs) {
        return StepResult.builder()
                .output(output)
                .status(status)
                .durationMs(durationMs)
                .parallel(false)
                .build();
    }

    public static StepResult parallel(String mergedOutput, String status, long durationMs,
                                       Map<String, AgentOutput> outputs) {
        return StepResult.builder()
                .output(mergedOutput)
                .status(status)
                .durationMs(durationMs)
                .outputs(outputs)
                .parallel(true)
                .build();
    }

    public static StepResult failed(String reason) {
        return StepResult.builder()
                .output(reason)
                .status("failed")
                .durationMs(0)
                .parallel(false)
                .build();
    }

    public boolean isPartialFailure() {
        if (!parallel || outputs == null) return false;
        return outputs.values().stream().anyMatch(o -> "failed".equals(o.getStatus()));
    }

    public int getTotalTokens() {
        if (outputs == null || outputs.isEmpty()) return 0;
        return outputs.values().stream().mapToInt(AgentOutput::getTotalTokens).sum();
    }
}
