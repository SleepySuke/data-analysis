/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 执行计划模型
 */
package com.suke.agent.domain.entity;

import com.suke.agent.core.models.PlanStatus;
import com.suke.agent.core.models.StepStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionPlan {
    private String planId;
    private String originalGoal;
    private List<PlanStep> steps;
    @Builder.Default
    private int currentStepIndex = 0;
    private String planSummary;
    @Builder.Default
    private int replanCount = 0;
    @Builder.Default
    private PlanStatus status = PlanStatus.PLANNING;
    private String failureReason;

    public PlanStep currentStep() {
        if (steps == null || currentStepIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentStepIndex);
    }

    public boolean hasMoreSteps() {
        return steps != null && currentStepIndex < steps.size();
    }

    public void advanceStep() {
        if (hasMoreSteps()) {
            currentStep().setStatus(StepStatus.PASS);
            currentStepIndex++;
        }
    }

    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
    }

    public void markFailed(String reason) {
        this.status = PlanStatus.FAILED;
        this.failureReason = reason;
    }

    public void incrementReplanCount() {
        this.replanCount++;
    }

    public void insertSteps(int atIndex, List<PlanStep> newSteps) {
        if (!(steps instanceof ArrayList)) {
            steps = new ArrayList<>(steps != null ? steps : List.of());
        }
        for (int i = 0; i < newSteps.size(); i++) {
            steps.add(atIndex + i, newSteps.get(i));
        }
        for (int i = 0; i < steps.size(); i++) {
            steps.get(i).setStepIndex(i);
        }
    }
}
