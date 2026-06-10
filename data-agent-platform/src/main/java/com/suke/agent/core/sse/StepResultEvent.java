/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 步骤结果事件
 */
package com.suke.agent.core.sse;

public record StepResultEvent(int stepIndex, String status, long durationMs) implements SseEvent {
    @Override
    public String type() { return "step_result"; }
}
