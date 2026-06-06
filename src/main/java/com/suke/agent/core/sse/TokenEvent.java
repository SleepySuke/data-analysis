/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description LLM token增量输出事件
 */
package com.suke.agent.core.sse;

public record TokenEvent(String content) implements SseEvent {
    @Override
    public String type() { return "token"; }
}
