/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description 流式输出完成事件
 */
package com.suke.agent.core.sse;

public record DoneEvent(String traceId, int totalTokens, long durationMs) implements SseEvent {
    @Override
    public String type() { return "done"; }
}
