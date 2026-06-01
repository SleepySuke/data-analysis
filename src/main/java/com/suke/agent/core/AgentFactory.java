/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description 统一Agent工厂，根据AgentSpec创建Agent实例
 */

package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AgentFactory {

    private final ChatModel chatModel;
    private final BaseCheckpointSaver saver;
    private final Map<String, AgentSpec> specs;

    public AgentFactory(
            @org.springframework.beans.factory.annotation.Qualifier("qwen") ChatModel chatModel,
            BaseCheckpointSaver saver,
            List<AgentSpec> specList) {
        this.chatModel = chatModel;
        this.saver = saver;
        this.specs = new LinkedHashMap<>();
        specList.forEach(spec -> this.specs.put(spec.name(), spec));
    }

    public Agent build(String agentName) {
        AgentSpec spec = resolveSpec(agentName);
        return switch (spec.type()) {
            case REACT -> buildReactAgent(spec);
            case CUSTOM -> buildCustomAgent(spec);
        };
    }

    public List<ToolCallback> getTools(String agentName) {
        AgentSpec spec = resolveSpec(agentName);
        if (spec.type() == AgentSpec.AgentType.CUSTOM || spec.toolInstances() == null) {
            return List.of();
        }
        return List.of(ToolCallbacks.from(spec.toolInstances().toArray()));
    }

    public AgentDescriptor buildDescriptor(String agentName) {
        AgentSpec spec = resolveSpec(agentName);
        return AgentDescriptor.builder()
                .name(spec.name())
                .description(spec.description())
                .prompt(spec.prompt())
                .tools(getTools(agentName))
                .handoffs(spec.handoffs())
                .agent(build(agentName))
                .build();
    }

    public Set<String> agentTypes() {
        return Collections.unmodifiableSet(specs.keySet());
    }

    private AgentSpec resolveSpec(String agentName) {
        AgentSpec spec = specs.get(agentName);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown agent: " + agentName);
        }
        return spec;
    }

    private Agent buildReactAgent(AgentSpec spec) {
        var builder = ReactAgent.builder()
                .name(spec.name())
                .description(spec.description())
                .model(chatModel)
                .instruction(spec.prompt())
                .tools(List.of(ToolCallbacks.from(spec.toolInstances().toArray())))
                .saver(saver);
        if (spec.hooks() != null && !spec.hooks().isEmpty()) {
            builder.hooks(spec.hooks().toArray(new Hook[0]));
        }
        return builder.build();
    }

    private Agent buildCustomAgent(AgentSpec spec) {
        try {
            return spec.customBuilder().get();
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to build custom agent: " + spec.name(), e);
        }
    }
}
