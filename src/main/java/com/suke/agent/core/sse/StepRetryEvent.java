/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description 步骤重试事件
 */
package com.suke.agent.core.sse;

public record StepRetryEvent(int stepIndex, int retryCount, String reason) implements SseEvent {
    @Override
    public String type() { return "step_retry"; }
}
