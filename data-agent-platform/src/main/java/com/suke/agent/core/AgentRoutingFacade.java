/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description Agent路由门面，封装复杂意图路由决策（Plan-Execute vs Direct）
 */
package com.suke.agent.core;

import com.suke.agent.core.sse.SseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Slf4j
@Component
public class AgentRoutingFacade {

    private final AgentChatService chatService;
    private final PlanExecutor planExecutor;
    private final IntentRouter intentRouter;

    public AgentRoutingFacade(@Qualifier("agentChatService")AgentChatService chatService,
                              PlanExecutor planExecutor,
                              IntentRouter intentRouter) {
        this.chatService = chatService;
        this.planExecutor = planExecutor;
        this.intentRouter = intentRouter;
    }

    public AgentResponse autoRouteWithPlan(String message, Long userId, String sessionId) {
        IntentRouter.IntentResult intent = intentRouter.routeWithComplexity(message);
        log.info("Auto-route with plan: '{}' → {} (complex={})", message, intent.agentName(), intent.complex());

        if (intent.complex()) {
            return planExecutor.executeLoop(message, userId, sessionId);
        }
        return chatService.directCall(intent.agentName(), message, userId, sessionId);
    }

    public Flux<SseEvent> streamingRouteWithPlan(String message, Long userId, String sessionId) {
        IntentRouter.IntentResult intent = intentRouter.routeWithComplexity(message);
        log.info("Streaming auto-route: '{}' → {} (complex={})", message, intent.agentName(), intent.complex());

        if (intent.complex()) {
            return planExecutor.executeLoopStream(message, userId, sessionId);
        }
        return chatService.streamingCall(intent.agentName(), message, userId, sessionId);
    }
}
