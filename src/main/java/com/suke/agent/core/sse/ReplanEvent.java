/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 重规划事件
 */
package com.suke.agent.core.sse;

import java.util.List;

public record ReplanEvent(String reason, List<PlanStepSummary> newSteps) implements SseEvent {
    @Override
    public String type() { return "replan"; }

    public record PlanStepSummary(int stepIndex, String agentName, String input) {}
}
