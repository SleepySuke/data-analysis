/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 计划步骤模型
 */
package com.suke.agent.domain.entity;

import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {
    private int stepIndex;
    @Builder.Default
    private StepMode mode = StepMode.SEQUENTIAL;
    private String agentName;
    private List<String> agentNames;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private Map<String, String> outputs;
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;
    @Builder.Default
    private int retryCount = 0;
    @Builder.Default
    private long durationMs = 0;

    public void incrementRetryCount() {
        this.retryCount++;
        this.status = StepStatus.RETRY;
    }

    public boolean isParallel() {
        return mode == StepMode.PARALLEL;
    }
}
