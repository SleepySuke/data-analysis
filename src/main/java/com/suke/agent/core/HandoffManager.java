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
        log.info("Handoff recorded: {} → {} (reason: {})",
                request.fromAgent(), request.toAgent(), request.reason());
        return record;
    }

    boolean isCyclicHandoff(List<HandoffRecord> history, String from, String to) {
        int roundTrips = 0;
        for (HandoffRecord r : history) {
            if ((r.getFromAgent().equals(from) && r.getToAgent().equals(to))
                    || (r.getFromAgent().equals(to) && r.getToAgent().equals(from))) {
                roundTrips++;
            }
        }
        return roundTrips >= 2;
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
