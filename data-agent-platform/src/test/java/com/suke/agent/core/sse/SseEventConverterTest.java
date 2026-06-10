/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description SseEventConverter 测试
 */
package com.suke.agent.core.sse;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SseEventConverterTest {

    @Test
    void streamingModelOutputEmitsTokenEvent() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.AGENT_MODEL_STREAMING);
        when(streaming.chunk()).thenReturn("你好");

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(streaming).collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertInstanceOf(TokenEvent.class, events.get(0));
        assertEquals("你好", ((TokenEvent) events.get(0)).content());
    }

    @Test
    void streamingModelOutputEmptyChunkEmitsNothing() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.AGENT_MODEL_STREAMING);
        when(streaming.chunk()).thenReturn("");

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(streaming).collectList().block();

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void streamingToolOutputEmitsToolCallEvent() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.AGENT_TOOL_STREAMING);
        when(streaming.chunk()).thenReturn("{\"query\": \"SELECT *\"}");
        when(streaming.node()).thenReturn("tool_node:sql_execution");

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(streaming).collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertInstanceOf(ToolCallEvent.class, events.get(0));
        assertEquals("sql_execution", ((ToolCallEvent) events.get(0)).tool());
    }

    @Test
    void streamingToolFinishedEmitsToolResultEvent() {
        // First, a tool streaming event to set up state
        StreamingOutput<?> toolStreaming = mock(StreamingOutput.class);
        when(toolStreaming.getOutputType()).thenReturn(OutputType.AGENT_TOOL_STREAMING);
        when(toolStreaming.chunk()).thenReturn("{}");
        when(toolStreaming.node()).thenReturn("tool_node:csv_analysis");

        SseEventConverter converter = new SseEventConverter();
        converter.convert(toolStreaming).collectList().block();

        // Then, tool finished
        StreamingOutput<?> toolFinished = mock(StreamingOutput.class);
        when(toolFinished.getOutputType()).thenReturn(OutputType.AGENT_TOOL_FINISHED);
        when(toolFinished.chunk()).thenReturn("100 rows analyzed");
        when(toolFinished.node()).thenReturn("tool_node:csv_analysis");

        List<SseEvent> events = converter.convert(toolFinished).collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertInstanceOf(ToolResultEvent.class, events.get(0));
        assertEquals("csv_analysis", ((ToolResultEvent) events.get(0)).tool());
        assertEquals("100 rows analyzed", ((ToolResultEvent) events.get(0)).output());
    }

    @Test
    void graphNodeFinishedEmitsNothing() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(OutputType.GRAPH_NODE_FINISHED);

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(streaming).collectList().block();

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void plainNodeOutputExtractsTextFromState() {
        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage("分析结果")));
        NodeOutput output = NodeOutput.of("agent_node", "test_agent", new OverAllState(data), null);

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(output).collectList().block();

        assertNotNull(events);
        assertEquals(1, events.size());
        assertInstanceOf(TokenEvent.class, events.get(0));
        assertEquals("分析结果", ((TokenEvent) events.get(0)).content());
    }

    @Test
    void plainNodeOutputWithEmptyTextEmitsNothing() {
        Map<String, Object> data = new HashMap<>();
        data.put("messages", List.of(new AssistantMessage("")));
        NodeOutput output = NodeOutput.of("agent_node", "test_agent", new OverAllState(data), null);

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(output).collectList().block();

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }

    @Test
    void nullOutputTypeEmitsNothing() {
        StreamingOutput<?> streaming = mock(StreamingOutput.class);
        when(streaming.getOutputType()).thenReturn(null);

        SseEventConverter converter = new SseEventConverter();
        List<SseEvent> events = converter.convert(streaming).collectList().block();

        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
}
