package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.HandoffRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HandoffManager {

    private static final int MAX_HANDOFFS = 5;

    private final AgentRegistry agentRegistry;
    private final AgentTraceService traceService;

    private final Map<String, List<HandoffRecord>> handoffHistory = new ConcurrentHashMap<>();

    public HandoffManager(AgentRegistry agentRegistry, AgentTraceService traceService) {
        this.agentRegistry = agentRegistry;
        this.traceService = traceService;
    }

    public AgentResponse executeHandoff(String fromAgent, String toAgent, String reason,
                                         String context, Long userId, String sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("sessionId must not be null for handoff operations");
        }

        AgentDescriptor fromDescriptor = agentRegistry.getDescriptor(fromAgent);

        // 校验目标Agent是否在handoffs白名单中
        if (fromDescriptor.getHandoffs() == null || !fromDescriptor.getHandoffs().contains(toAgent)) {
            throw new IllegalArgumentException(
                    "Agent " + fromAgent + " cannot handoff to " + toAgent + ": not in handoffs list");
        }

        // 检查转交次数
        String key = sessionId;
        List<HandoffRecord> history = handoffHistory.computeIfAbsent(key, k -> new ArrayList<>());
        if (history.size() >= MAX_HANDOFFS) {
            throw new IllegalStateException("Maximum handoff limit (" + MAX_HANDOFFS + ") exceeded");
        }

        // 检查循环
        if (isCyclicHandoff(history, fromAgent, toAgent)) {
            throw new IllegalStateException("Cyclic handoff detected: " + buildHandoffChain(history, fromAgent, toAgent));
        }

        // 记录转交
        HandoffRecord record = HandoffRecord.builder()
                .fromAgent(fromAgent)
                .toAgent(toAgent)
                .reason(reason)
                .context(context)
                .timestamp(System.currentTimeMillis())
                .build();
        history.add(record);

        log.info("Handoff: {} → {} (reason: {})", fromAgent, toAgent, reason);

        // 构建转交输入
        String handoffInput = String.format("[转交自 %s, 原因: %s]\n%s", fromAgent, reason, context);

        // 调用目标Agent
        AgentDescriptor targetDescriptor = agentRegistry.getDescriptor(toAgent);
        long startTime = System.currentTimeMillis();

        try {
            // 通过ReactAgent调用
            // targetDescriptor中保存了ReactAgent实例，通过AgentOrchestrator调用
            // 这里返回HandoffResult让Orchestrator执行
            throw new UnsupportedOperationException("Handoff execution should be done through AgentOrchestrator");
        } catch (UnsupportedOperationException e) {
            // 重新抛出，这只是标记该方法不应该直接被调用
            throw e;
        }
    }

    boolean isCyclicHandoff(List<HandoffRecord> history, String from, String to) {
        // 检查是否出现 A→B→A（往返）
        for (HandoffRecord r : history) {
            if (r.getToAgent().equals(from) && r.getFromAgent().equals(to)) {
                return true;
            }
            if (r.getFromAgent().equals(to) && r.getToAgent().equals(from)) {
                return true;
            }
        }

        // 检查是否出现 A→B→C→A（环路）
        List<String> chain = new ArrayList<>();
        for (HandoffRecord r : history) {
            chain.add(r.getFromAgent());
        }
        chain.add(from);
        if (chain.contains(to)) {
            // to出现在链中，会形成环路
            int toIndex = chain.indexOf(to);
            List<String> subChain = chain.subList(toIndex, chain.size());
            if (subChain.contains(from)) {
                return true;
            }
        }

        return false;
    }

    public List<HandoffRecord> getHandoffHistory(String sessionId) {
        return Collections.unmodifiableList(
                handoffHistory.getOrDefault(sessionId, Collections.emptyList()));
    }

    public void clearSession(String sessionId) {
        handoffHistory.remove(sessionId);
    }

    private String buildHandoffChain(List<HandoffRecord> history, String from, String to) {
        StringBuilder sb = new StringBuilder();
        for (HandoffRecord r : history) {
            sb.append(r.getFromAgent()).append("→");
        }
        sb.append(from).append("→").append(to);
        return sb.toString();
    }
}
