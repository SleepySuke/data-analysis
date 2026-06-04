/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent编排器，负责路由、调用、消息增强、trace追踪
 */

package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.suke.agent.memory.ConversationHistoryManager;
import com.suke.agent.memory.LongTermMemoryStore;
import com.suke.agent.memory.TopicExtractor;
import com.suke.agent.memory.UserBehaviorTracker;
import com.suke.agent.memory.WorkingMemory;
import com.suke.agent.skill.SkillManager;
import com.suke.agent.trace.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class AgentOrchestrator {

    private final AgentRegistry agentRegistry;
    private final AgentTraceService traceService;
    private final HandoffManager handoffManager;
    private final LongTermMemoryStore memoryStore;
    private final SkillManager skillManager;
    private final UserBehaviorTracker behaviorTracker;
    private final ConversationHistoryManager historyManager;
    private final TopicExtractor topicExtractor;
    private final WorkingMemory workingMemory;
    private final IntentRouter intentRouter;

    public AgentOrchestrator(AgentRegistry agentRegistry,
                              AgentTraceService traceService,
                              HandoffManager handoffManager,
                              LongTermMemoryStore memoryStore,
                              SkillManager skillManager,
                              UserBehaviorTracker behaviorTracker,
                              ConversationHistoryManager historyManager,
                              TopicExtractor topicExtractor,
                              WorkingMemory workingMemory,
                              IntentRouter intentRouter) {
        this.agentRegistry = agentRegistry;
        this.traceService = traceService;
        this.handoffManager = handoffManager;
        this.memoryStore = memoryStore;
        this.skillManager = skillManager;
        this.behaviorTracker = behaviorTracker;
        this.historyManager = historyManager;
        this.topicExtractor = topicExtractor;
        this.workingMemory = workingMemory;
        this.intentRouter = intentRouter;
    }

    public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
        agentRegistry.getDescriptor(agentName); // 校验 agent 存在

        String traceId = traceService.generateTraceId();
        if (sessionId == null) {
            sessionId = "sess-" + traceId;
        }
        long startTime = System.currentTimeMillis();

        log.info("[Trace:{}] Direct call to agent: {}", traceId, agentName);

        List<AgentStep> steps = new ArrayList<>();
        List<HandoffRecord> allHandoffs = new ArrayList<>();
        String finalOutput = null;
        String status = "success";
        int totalTokens = 0;
        String currentAgentName = agentName;

        try {
            while (true) {
                HandoffContext.clear();
                HandoffContext.setCurrentAgent(currentAgentName);

                AgentDescriptor currentDescriptor = agentRegistry.getDescriptor(currentAgentName);
                Agent agent = currentDescriptor.getAgent();
                if (agent == null) {
                    throw new IllegalStateException("Agent instance not found: " + currentAgentName);
                }

                // Turn-aware enrichment: only inject full context on first turn (no prior handoffs)
                boolean firstTurn = historyManager.isFirstTurn(sessionId);
                String enrichedMessage;
                if (firstTurn && allHandoffs.isEmpty()) {
                    enrichedMessage = enrichMessage(message, currentAgentName, userId, sessionId, currentDescriptor);
                } else {
                    enrichedMessage = prependWorkingMemory(message, sessionId);
                }

                java.util.Optional<NodeOutput> outputOpt = agent.invokeAndGetOutput(enrichedMessage,
                        RunnableConfig.builder().threadId(sessionId).build());

                String agentOutput = "";
                if (outputOpt.isPresent()) {
                    NodeOutput output = outputOpt.get();
                    agentOutput = extractOutputText(output);
                    Usage usage = output.tokenUsage();
                    if (usage != null && usage.getTotalTokens() != null) {
                        totalTokens += usage.getTotalTokens();
                    }
                }

                AgentStep step = AgentStep.builder()
                        .stepIndex(steps.size())
                        .type(allHandoffs.isEmpty() ? "reasoning" : "handoff_continue")
                        .content("Agent " + currentAgentName + " call completed")
                        .build();
                steps.add(step);

                // Check for pending handoff
                HandoffRequest pending = HandoffContext.getPending();
                if (pending == null) {
                    finalOutput = agentOutput;
                    break;
                }

                // Validate handoff
                handoffManager.validate(allHandoffs, pending);

                // Record handoff
                HandoffRecord record = handoffManager.record(pending, sessionId);
                allHandoffs.add(record);

                log.info("[Trace:{}] Handoff: {} → {} (reason: {})",
                        traceId, pending.fromAgent(), pending.toAgent(), pending.reason());

                // Prepare next iteration
                message = String.format(
                        "[转交自 %s, 原因: %s]\n%s",
                        pending.fromAgent(), pending.reason(), agentOutput);
                currentAgentName = pending.toAgent();
            }

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
        } finally {
            HandoffContext.clear();
        }

        long durationMs = System.currentTimeMillis() - startTime;

        AgentTrace trace = AgentTrace.builder()
                .traceId(traceId)
                .sessionId(sessionId)
                .userId(userId)
                .entryType("direct")
                .targetAgent(agentName)
                .steps(steps)
                .handoffs(allHandoffs)
                .totalTokens(totalTokens)
                .totalDurationMs((int) durationMs)
                .finalOutput(finalOutput)
                .status(status)
                .build();
        traceService.saveTraceAsync(trace);

        // Async behavior tracking
        if (userId != null) {
            String topic = TopicExtractor.extractLabel(message);
            behaviorTracker.updateFrequentTopics(userId, topic);
            memoryStore.trackInteraction(userId, sessionId, agentName,
                    "direct", topic, totalTokens, (int) durationMs);
        }

        return AgentResponse.builder()
                .output(finalOutput)
                .traceId(traceId)
                .handoffs(allHandoffs)
                .totalTokens(totalTokens)
                .durationMs(durationMs)
                .status(status)
                .sourceAgent(currentAgentName)
                .build();
    }

    public AgentResponse autoRoute(String message, Long userId, String sessionId) {
        String targetAgent = intentRouter.route(message);
        log.info("Auto-route: '{}' → {}", message, targetAgent);
        return directCall(targetAgent, message, userId, sessionId);
    }

    String enrichMessage(String message, String agentName, Long userId, String sessionId, AgentDescriptor descriptor) {
        StringBuilder enriched = new StringBuilder();

        if (userId != null) {
            String memoryContext = memoryStore.buildMemoryContext(userId, agentName);
            if (!memoryContext.isEmpty()) {
                enriched.append(memoryContext).append("\n");
            }
        }

        // 工作记忆（首轮和后续轮次都注入）
        String workingContext = workingMemory.buildContext(sessionId);
        if (workingContext != null && !workingContext.isEmpty()) {
            enriched.append(workingContext).append("\n");
        }

        String skillPrompt = skillManager.buildMetadataPrompt(agentName, userId);
        if (!skillPrompt.isEmpty()) {
            enriched.append(skillPrompt).append("\n");
        }

        if (descriptor.getHandoffs() != null && !descriptor.getHandoffs().isEmpty()) {
            enriched.append("[可转交Agent]\n");
            for (String target : descriptor.getHandoffs()) {
                enriched.append("- ").append(target);
                AgentDescriptor targetDesc = agentRegistry.getDescriptor(target);
                if (targetDesc != null && targetDesc.getDescription() != null) {
                    enriched.append(": ").append(targetDesc.getDescription());
                }
                enriched.append("\n");
            }
            enriched.append("当需要其他Agent协作时，调用 handoff(agent_name, reason) 工具进行转交。\n");
            enriched.append("转交后当前推理会停止，由目标Agent继续处理。\n\n");
        }

        enriched.append(message);
        return enriched.toString();
    }

    private String prependWorkingMemory(String message, String sessionId) {
        String workingContext = workingMemory.buildContext(sessionId);
        if (workingContext == null || workingContext.isEmpty()) {
            return message;
        }
        return workingContext + "\n" + message;
    }

    private String extractOutputText(NodeOutput output) {
        Object messagesObj = output.state() != null ? output.state().data().get("messages") : null;
        if (messagesObj instanceof List<?> messages) {
            return messages.stream()
                    .filter(m -> m instanceof AssistantMessage)
                    .map(m -> ((AssistantMessage) m).getText())
                    .reduce((first, second) -> second)
                    .orElse("");
        }
        return "";
    }

}
