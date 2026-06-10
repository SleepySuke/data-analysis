/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent注册中心，管理所有已注册的Agent描述符
 */

package com.suke.agent.core;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AgentRegistry {

    private final Map<String, AgentDescriptor> agents = new ConcurrentHashMap<>();

    public void register(AgentDescriptor descriptor) {
        if (descriptor.getName() == null || descriptor.getName().isBlank()) {
            throw new IllegalArgumentException("Agent name must not be null or blank");
        }
        AgentDescriptor existing = agents.putIfAbsent(descriptor.getName(), descriptor);
        if (existing != null) {
            throw new IllegalStateException("Agent already registered: " + descriptor.getName());
        }
        log.info("Registered agent: {}", descriptor.getName());
    }

    public AgentDescriptor getDescriptor(String name) {
        AgentDescriptor descriptor = agents.get(name);
        if (descriptor == null) {
            throw new IllegalArgumentException("Agent not found: " + name);
        }
        return descriptor;
    }

    public List<String> allAgentNames() {
        return List.copyOf(agents.keySet());
    }

    public boolean exists(String name) {
        return agents.containsKey(name);
    }
}
