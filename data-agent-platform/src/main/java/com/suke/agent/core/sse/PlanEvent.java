/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 计划创建事件
 */
package com.suke.agent.core.sse;

import java.util.List;

public record PlanEvent(String planId, List<PlanStepSummary> steps, String summary) implements SseEvent {
    @Override
    public String type() { return "plan_created"; }

    public record PlanStepSummary(int stepIndex, String agentName, String input) {}
}
