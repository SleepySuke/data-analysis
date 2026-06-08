/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description SSE事件密封接口
 */
package com.suke.agent.core.sse;

public sealed interface SseEvent permits
        TokenEvent, ToolCallEvent, ToolResultEvent,
        HandoffEvent, DoneEvent, ErrorEvent, AgentStreamEndEvent,
        PlanEvent, StepStartEvent, StepResultEvent, StepRetryEvent, ReplanEvent,
        ParallelStepStartEvent, ParallelStepResultEvent {
    String type();
}
