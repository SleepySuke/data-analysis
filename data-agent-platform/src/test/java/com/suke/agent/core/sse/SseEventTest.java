/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description SseEvent 事件模型测试
 */
package com.suke.agent.core.sse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SseEventTest {

    @Test
    void tokenEventType() {
        TokenEvent event = new TokenEvent("hello");
        assertEquals("token", event.type());
        assertEquals("hello", event.content());
    }

    @Test
    void toolCallEventType() {
        ToolCallEvent event = new ToolCallEvent("csv_analysis", "data.csv");
        assertEquals("tool_call", event.type());
        assertEquals("csv_analysis", event.tool());
        assertEquals("data.csv", event.input());
    }

    @Test
    void toolResultEventType() {
        ToolResultEvent event = new ToolResultEvent("csv_analysis", "100 rows", 120);
        assertEquals("tool_result", event.type());
        assertEquals("csv_analysis", event.tool());
        assertEquals("100 rows", event.output());
        assertEquals(120, event.durationMs());
    }

    @Test
    void handoffEventType() {
        HandoffEvent event = new HandoffEvent("data_analyst", "data_cleaner", "数据有缺失");
        assertEquals("handoff", event.type());
        assertEquals("data_analyst", event.from());
        assertEquals("data_cleaner", event.to());
        assertEquals("数据有缺失", event.reason());
    }

    @Test
    void doneEventType() {
        DoneEvent event = new DoneEvent("trace-123", 1500, 3200);
        assertEquals("done", event.type());
        assertEquals("trace-123", event.traceId());
        assertEquals(1500, event.totalTokens());
        assertEquals(3200, event.durationMs());
    }

    @Test
    void errorEventType() {
        ErrorEvent event = new ErrorEvent("执行失败");
        assertEquals("error", event.type());
        assertEquals("执行失败", event.message());
    }

    @Test
    void agentStreamEndEventType() {
        AgentStreamEndEvent event = AgentStreamEndEvent.INSTANCE;
        assertEquals("agent_stream_end", event.type());
    }

    @Test
    void sealedInterfaceAllowsOnlyPermits() {
        SseEvent token = new TokenEvent("x");
        SseEvent tool = new ToolCallEvent("t", "i");
        SseEvent result = new ToolResultEvent("t", "o", 1);
        SseEvent handoff = new HandoffEvent("a", "b", "r");
        SseEvent done = new DoneEvent("id", 0, 0);
        SseEvent error = new ErrorEvent("e");
        SseEvent end = AgentStreamEndEvent.INSTANCE;

        assertNotNull(token);
        assertNotNull(tool);
        assertNotNull(result);
        assertNotNull(handoff);
        assertNotNull(done);
        assertNotNull(error);
        assertNotNull(end);
    }
}
