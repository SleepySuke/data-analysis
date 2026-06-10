/**
 * @author 自然醒
 * @version 1.1
 * @date 2026-06-02
 * @description Agent间任务转交管理器，提供校验（validate）和记录（record）两个独立操作。
 *              实际执行由 AgentOrchestrator 负责。
 */
package com.suke.agent.core;

import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.HandoffRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class HandoffManager {

    private static final int MAX_HANDOFFS = 5;
    private static final long SESSION_TTL_MS = TimeUnit.MINUTES.toMillis(30);

    private final AgentRegistry agentRegistry;
    private final AgentTraceService traceService;

    private final Map<String, List<HandoffRecord>> handoffHistory = new ConcurrentHashMap<>();
    private final Map<String, Long> sessionLastAccess = new ConcurrentHashMap<>();

    public HandoffManager(AgentRegistry agentRegistry, AgentTraceService traceService) {
        this.agentRegistry = agentRegistry;
        this.traceService = traceService;
    }

    public void validate(List<HandoffRecord> history, HandoffRequest request) {
        AgentDescriptor fromDescriptor = agentRegistry.getDescriptor(request.fromAgent());

        if (fromDescriptor.getHandoffs() == null
                || !fromDescriptor.getHandoffs().contains(request.toAgent())) {
            throw new IllegalArgumentException(
                    "Agent " + request.fromAgent() + " cannot handoff to " + request.toAgent()
                            + ": not in handoffs list");
        }

        if (history.size() >= MAX_HANDOFFS) {
            throw new IllegalStateException(
                    "Maximum handoff limit (" + MAX_HANDOFFS + ") exceeded");
        }

        if (isCyclicHandoff(history, request.fromAgent(), request.toAgent())) {
            throw new IllegalStateException(
                    "Cyclic handoff detected: "
                            + buildHandoffChain(history, request.fromAgent(), request.toAgent()));
        }
    }

    public HandoffRecord record(HandoffRequest request, String sessionId) {
        HandoffRecord record = HandoffRecord.builder()
                .fromAgent(request.fromAgent())
                .toAgent(request.toAgent())
                .reason(request.reason())
                .context(request.context())
                .timestamp(System.currentTimeMillis())
                .build();

        handoffHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(record);
        sessionLastAccess.put(sessionId, System.currentTimeMillis());
        log.info("Handoff recorded: {} → {} (reason: {})",
                request.fromAgent(), request.toAgent(), request.reason());
        return record;
    }

    /**
     * 检测 handoff 是否构成无意义循环。
     * 构建完整 Agent 访问链，如果即将形成的链中出现同一对 Agent 连续往返 ≥2 次，视为循环。
     * 三角流转 A→B→C→A→B 不算循环（非连续往返），由 MAX_HANDOFFS 兜底。
     */
    boolean isCyclicHandoff(List<HandoffRecord> history, String from, String to) {
        // 构建 (fromAgent, toAgent) 对的序列
        List<String[]> pairs = new ArrayList<>();
        for (HandoffRecord r : history) {
            pairs.add(new String[]{r.getFromAgent(), r.getToAgent()});
        }
        pairs.add(new String[]{from, to});

        // 统计同一对（双向）在相邻位置出现的连续次数
        // 如果同一对 Agent 之间出现 ≥3 次转交（即 ≥2 次完整往返），视为循环
        int consecutiveRoundTrips = 0;
        for (int i = 1; i < pairs.size(); i++) {
            String[] prev = pairs.get(i - 1);
            String[] curr = pairs.get(i);
            // 检查是否为往返：上一对的 to 是当前对的 from，上一对的 from 是当前对的 to
            if (prev[1].equals(curr[0]) && prev[0].equals(curr[1])) {
                consecutiveRoundTrips++;
            } else {
                consecutiveRoundTrips = 0;
            }
        }
        return consecutiveRoundTrips >= 2;
    }

    public List<HandoffRecord> getHandoffHistory(String sessionId) {
        sessionLastAccess.put(sessionId, System.currentTimeMillis());
        return Collections.unmodifiableList(
                handoffHistory.getOrDefault(sessionId, Collections.emptyList()));
    }

    public void clearSession(String sessionId) {
        handoffHistory.remove(sessionId);
        sessionLastAccess.remove(sessionId);
    }

    /**
     * 清理超过 TTL 的过期 session，由定时任务调用。
     */
    public void evictExpiredSessions() {
        long now = System.currentTimeMillis();
        int evicted = 0;
        Iterator<Map.Entry<String, Long>> it = sessionLastAccess.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> entry = it.next();
            if (now - entry.getValue() > SESSION_TTL_MS) {
                handoffHistory.remove(entry.getKey());
                it.remove();
                evicted++;
            }
        }
        if (evicted > 0) {
            log.info("Evicted {} expired handoff sessions", evicted);
        }
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
