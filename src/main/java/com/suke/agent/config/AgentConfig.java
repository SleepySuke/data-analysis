/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description Agent配置类，通过AgentFactory统一注册所有Agent实例
 */

package com.suke.agent.config;

import com.suke.agent.core.AgentChatService;
import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentFactory;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.core.PipelineExecutor;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableConfigurationProperties(PipelineProperties.class)
public class AgentConfig {

    private final AgentRegistry agentRegistry;
    private final AgentFactory agentFactory;

    private ExecutorService planExecutorService;
    private ExecutorService parallelExecutorService;

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

    @Bean
    @Qualifier("planExecutor")
    public ExecutorService planExecutor() {
        planExecutorService = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = new Thread(r, "plan-executor");
                    t.setDaemon(true);
                    return t;
                });
        return planExecutorService;
    }

    @Bean
    @Qualifier("parallelExecutor")
    public ExecutorService parallelExecutor(PipelineProperties pipelineProperties) {
        parallelExecutorService = Executors.newFixedThreadPool(
                pipelineProperties.getMaxParallelAgents(),
                r -> {
                    Thread t = new Thread(r, "parallel-executor");
                    t.setDaemon(true);
                    return t;
                });
        return parallelExecutorService;
    }

    @PreDestroy
    public void shutdown() {
        shutdownExecutor(planExecutorService, "planExecutor");
        shutdownExecutor(parallelExecutorService, "parallelExecutor");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null) return;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("{} did not terminate in 10s, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    @Bean
    public PipelineExecutor pipelineExecutor(
            @Qualifier("agentChatService") AgentChatService chatService,
            @Qualifier("deepseekClient") ChatClient deepseekClient,
            @Qualifier("parallelExecutor") ExecutorService parallelExecutor,
            PipelineProperties pipelineProperties) {
        return new PipelineExecutor(
                chatService,
                deepseekClient,
                parallelExecutor,
                pipelineProperties.getMaxParallelAgents(),
                pipelineProperties.getParallelTimeoutSeconds());
    }
}
