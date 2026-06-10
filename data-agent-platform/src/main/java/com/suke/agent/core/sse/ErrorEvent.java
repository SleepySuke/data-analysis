/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description 错误事件
 */
package com.suke.agent.core.sse;

public record ErrorEvent(String message) implements SseEvent {
    @Override
    public String type() { return "error"; }
}
