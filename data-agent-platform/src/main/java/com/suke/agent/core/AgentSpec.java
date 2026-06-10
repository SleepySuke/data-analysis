/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description Agent创建规格，封装Agent的所有配置信息
 */

package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;

import java.util.List;
import java.util.function.Supplier;

public record AgentSpec(
        String name,
        String description,
        String prompt,
        List<String> handoffs,
        List<Object> toolInstances,
        List<Hook> hooks,
        AgentType type,
        Supplier<Agent> customBuilder
) {
    public enum AgentType { REACT, CUSTOM }

    public static AgentSpec react(String name, String description, String prompt,
                                  List<String> handoffs, List<Object> tools,
                                  Hook... hooks) {
        return new AgentSpec(name, description, prompt, handoffs, tools,
                hooks.length > 0 ? List.of(hooks) : null, AgentType.REACT, null);
    }

    public static AgentSpec custom(String name, String description,
                                   List<String> handoffs,
                                   Supplier<Agent> builder) {
        return new AgentSpec(name, description, null, handoffs, null,
                null, AgentType.CUSTOM, builder);
    }

    public AgentSpec {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Agent name must not be null or blank");
        }
        if (type == AgentType.REACT && (toolInstances == null || toolInstances.isEmpty())) {
            throw new IllegalArgumentException("ReactAgent must have at least one tool: " + name);
        }
    }
}
