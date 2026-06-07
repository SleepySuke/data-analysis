/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 计划步骤模型
 */
package com.suke.agent.domain.entity;

import com.suke.agent.core.models.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {
    private int stepIndex;
    private String agentName;
    private String input;
    private String expectedOutput;
    private String actualOutput;
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
}
