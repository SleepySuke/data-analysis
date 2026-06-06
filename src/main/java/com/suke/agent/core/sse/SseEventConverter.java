/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-06
 * @description 将 agent.stream() 返回的 NodeOutput/StreamingOutput 转换为 SseEvent 流
 */
package com.suke.agent.core.sse;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import org.springframework.ai.chat.messages.AssistantMessage;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SseEventConverter {

    private long toolCallStartTime = 0;
    private String currentToolName = null;

    /**
     * Convert a single NodeOutput to 0-N SseEvents.
     * Checks instanceof StreamingOutput to access getOutputType() and chunk().
     */
    public Flux<SseEvent> convert(NodeOutput output) {
        List<SseEvent> events = new ArrayList<>();

        if (output instanceof StreamingOutput<?> streaming) {
            convertStreaming(streaming, events);
        } else {
            // Plain NodeOutput (non-streaming) — try to extract text from state
            convertPlain(output, events);
        }

        return Flux.fromIterable(events);
    }

    private void convertStreaming(StreamingOutput<?> streaming, List<SseEvent> events) {
        OutputType type = streaming.getOutputType();
        if (type == null) return;

        switch (type) {
            case AGENT_MODEL_STREAMING -> {
                String chunk = streaming.chunk();
                if (chunk != null && !chunk.isEmpty()) {
                    events.add(new TokenEvent(chunk));
                }
            }
            case AGENT_TOOL_STREAMING -> {
                String toolName = extractToolName(streaming.node());
                String input = streaming.chunk();
                if (toolName != null) {
                    toolCallStartTime = System.currentTimeMillis();
                    currentToolName = toolName;
                    events.add(new ToolCallEvent(toolName, input != null ? input : ""));
                }
            }
            case AGENT_TOOL_FINISHED -> {
                String toolName = currentToolName != null ? currentToolName : extractToolName(streaming.node());
                String result = streaming.chunk();
                long duration = toolCallStartTime > 0 ? System.currentTimeMillis() - toolCallStartTime : 0;
                if (toolName != null) {
                    events.add(new ToolResultEvent(toolName, result != null ? result : "", duration));
                }
                currentToolName = null;
                toolCallStartTime = 0;
            }
            // AGENT_MODEL_FINISHED, AGENT_HOOK_*, GRAPH_NODE_* — ignore
            default -> {}
        }
    }

    private void convertPlain(NodeOutput output, List<SseEvent> events) {
        // For non-streaming NodeOutput, extract text from state messages
        if (output.state() == null) return;

        Map<String, Object> data = output.state().data();
        if (data == null) return;

        Object messagesObj = data.get("messages");
        if (!(messagesObj instanceof List<?> messages)) return;

        String lastText = messages.stream()
                .filter(m -> m instanceof AssistantMessage)
                .map(m -> ((AssistantMessage) m).getText())
                .reduce((first, second) -> second)
                .filter(text -> text != null && !text.isEmpty())
                .orElse(null);

        if (lastText != null) {
            events.add(new TokenEvent(lastText));
        }
    }

    private String extractToolName(String nodeName) {
        if (nodeName == null) return null;
        // Tool node names typically look like "tools" or "tool_node:toolName"
        if (nodeName.startsWith("tool_node:")) {
            return nodeName.substring("tool_node:".length());
        }
        return nodeName;
    }
}
