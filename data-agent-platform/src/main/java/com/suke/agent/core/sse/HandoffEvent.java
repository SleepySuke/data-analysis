/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description Agent间Handoff转交事件
 */
package com.suke.agent.core.sse;

public record HandoffEvent(String from, String to, String reason) implements SseEvent {
    @Override
    public String type() { return "handoff"; }
}
