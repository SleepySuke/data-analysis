/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description 工具调用结果事件
 */
package com.suke.agent.core.sse;

public record ToolResultEvent(String tool, String output, long durationMs) implements SseEvent {
    @Override
    public String type() { return "tool_result"; }
}
