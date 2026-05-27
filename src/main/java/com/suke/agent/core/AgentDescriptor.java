package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.Builder;
import lombok.Data;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

@Data
@Builder
public class AgentDescriptor {
    private String name;
    private String description;
    private String prompt;
    private List<ToolCallback> tools;
    private List<String> handoffs;
    private ReactAgent agent;
}
