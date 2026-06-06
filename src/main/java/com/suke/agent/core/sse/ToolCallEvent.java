/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description 工具调用开始事件
 */
package com.suke.agent.core.sse;

public record ToolCallEvent(String tool, String input) implements SseEvent {
    @Override
    public String type() { return "tool_call"; }
}
