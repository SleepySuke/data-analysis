/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent描述符，封装Agent的名称、工具、handoff配置
 */

package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.Agent;
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
    private Agent agent;
}
