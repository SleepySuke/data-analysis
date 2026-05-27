package com.suke.agent.trace;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {
    private int stepIndex;
    private String type; // reasoning, tool_call, tool_result, handoff
    private String content;
    private String toolName;
    private String toolInput;
    private String toolOutput;
    private int tokensUsed;
    private long durationMs;
}
