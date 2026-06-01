/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description Agent配置类，通过AgentFactory统一注册所有Agent实例
 */

package com.suke.agent.config;

import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentFactory;
import com.suke.agent.core.AgentRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AgentConfig {

    private final AgentRegistry agentRegistry;
    private final AgentFactory agentFactory;

    public AgentConfig(AgentRegistry agentRegistry, AgentFactory agentFactory) {
        this.agentRegistry = agentRegistry;
        this.agentFactory = agentFactory;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Agent Registry...");
        for (String agentType : agentFactory.agentTypes()) {
            try {
                AgentDescriptor descriptor = agentFactory.buildDescriptor(agentType);
                agentRegistry.register(descriptor);
                log.info("Registered agent: {}", agentType);
            } catch (Exception e) {
                log.error("Failed to register agent: {}", agentType, e);
            }
        }
        log.info("Agent Registry initialized with {} agents", agentRegistry.allAgentNames().size());
    }
}
