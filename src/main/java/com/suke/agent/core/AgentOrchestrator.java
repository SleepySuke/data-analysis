package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.suke.agent.trace.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AgentOrchestrator {

    private final AgentRegistry agentRegistry;
    private final AgentTraceService traceService;
    private final HandoffManager handoffManager;

    public AgentOrchestrator(AgentRegistry agentRegistry,
                              AgentTraceService traceService,
                              HandoffManager handoffManager) {
        this.agentRegistry = agentRegistry;
        this.traceService = traceService;
        this.handoffManager = handoffManager;
    }

    /**
     * 直接调用指定Agent
     */
    public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
        AgentDescriptor descriptor = agentRegistry.getDescriptor(agentName);

        String traceId = traceService.generateTraceId();
        if (sessionId == null) {
            sessionId = "sess-" + traceId;
        }
        long startTime = System.currentTimeMillis();

        log.info("[Trace:{}] Direct call to agent: {}", traceId, agentName);

        List<AgentStep> steps = new ArrayList<>();
        List<HandoffRecord> handoffs = new ArrayList<>();
        String finalOutput = null;
        String status = "success";
        int totalTokens = 0;

        try {
            // 获取Agent实例（从descriptor中获取预先构建的ReactAgent）
            ReactAgent agent = descriptor.getAgent();
            if (agent == null) {
                throw new IllegalStateException("Agent instance not found: " + agentName);
            }

            AssistantMessage response = agent.call(message);
            finalOutput = response.getText();

            AgentStep step = AgentStep.builder()
                    .stepIndex(0)
                    .type("reasoning")
                    .content("Agent call completed")
                    .build();
            steps.add(step);

        } catch (Exception e) {
            log.error("[Trace:{}] Agent execution failed: {}", traceId, e.getMessage(), e);
            status = "failed";
            finalOutput = "Agent执行失败: " + e.getMessage();

            AgentStep errorStep = AgentStep.builder()
                    .stepIndex(steps.size())
                    .type("error")
                    .content(e.getMessage())
                    .build();
            steps.add(errorStep);
        }

        long durationMs = System.currentTimeMillis() - startTime;

        // 收集handoff记录
        if (sessionId != null) {
            handoffs.addAll(handoffManager.getHandoffHistory(sessionId));
        }

        // 异步保存Trace
        AgentTrace trace = AgentTrace.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .userId(userId)
                .entryType("direct")
                .targetAgent(agentName)
                .steps(steps)
                .handoffs(handoffs)
                .totalTokens(totalTokens)
                .totalDurationMs((int) durationMs)
                .finalOutput(finalOutput)
                .status(status)
                .build();
        traceService.saveTraceAsync(trace);

        return AgentResponse.builder()
                .output(finalOutput)
                .traceId(traceId)
                .handoffs(handoffs)
                .totalTokens(totalTokens)
                .durationMs(durationMs)
                .status(status)
                .build();
    }

    /**
     * 自动路由（Supervisor选择Agent）
     */
    public AgentResponse autoRoute(String message, Long userId, String sessionId) {
        // 简单路由规则：基于关键词判断
        String targetAgent = routeByKeywords(message);
        log.info("Auto-route: '{}' → {}", message, targetAgent);
        return directCall(targetAgent, message, userId, sessionId);
    }

    private String routeByKeywords(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("抓取") || lower.contains("爬取") || lower.contains("网页") || lower.contains("url")) {
            return "web_scraper";
        }
        if (lower.contains("sql") || lower.contains("数据库") || lower.contains("查询")) {
            return "sql_analyst";
        }
        if (lower.contains("清洗") || lower.contains("缺失值") || lower.contains("异常值") || lower.contains("去重")) {
            return "data_cleaner";
        }
        return "data_analyst";
    }
}
