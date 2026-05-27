package com.suke.agent.core;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.suke.agent.trace.HandoffRecord;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private String output;
    private String traceId;
    private List<HandoffRecord> handoffs;
    private int totalTokens;
    private long durationMs;
    private String status;
}
