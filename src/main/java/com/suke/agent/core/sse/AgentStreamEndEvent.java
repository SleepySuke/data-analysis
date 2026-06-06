/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description Agent流结束标记事件（内部使用，不发送到前端）
 */
package com.suke.agent.core.sse;

public record AgentStreamEndEvent() implements SseEvent {
    public static final AgentStreamEndEvent INSTANCE = new AgentStreamEndEvent();

    @Override
    public String type() { return "agent_stream_end"; }
}
