/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description Agent对话服务接口，解耦 PlanExecutor 与 AgentOrchestrator 的循环依赖
 */
package com.suke.agent.core;

import com.suke.agent.core.sse.SseEvent;
import reactor.core.publisher.Flux;

public interface AgentChatService {
    AgentResponse directCall(String agentName, String message, Long userId, String sessionId);

    Flux<SseEvent> streamingCall(String agentName, String message, Long userId, String sessionId);
}
