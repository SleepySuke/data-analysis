/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 步骤开始事件
 */
package com.suke.agent.core.sse;

public record StepStartEvent(int stepIndex, String agent, String input) implements SseEvent {
    @Override
    public String type() { return "step_start"; }
}
