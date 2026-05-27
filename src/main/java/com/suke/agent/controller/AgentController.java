package com.suke.agent.controller;

import com.suke.agent.core.AgentOrchestrator;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.core.AgentResponse;
import com.suke.common.Result;
import com.suke.context.UserContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;

    public AgentController(AgentOrchestrator orchestrator, AgentRegistry agentRegistry) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
    }

    @PostMapping("/{agentName}/chat")
    public Result<AgentResponse> directCall(
            @PathVariable String agentName,
            @RequestBody AgentChatRequest request) {
        log.info("Direct call to agent: {}, message length: {}", agentName,
                request.getMessage() != null ? request.getMessage().length() : 0);

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.error("消息不能为空");
        }

        if (!agentRegistry.exists(agentName)) {
            return Result.error("Agent不存在: " + agentName);
        }

        try {
            Long userId = UserContext.getCurrentId();
            AgentResponse response = orchestrator.directCall(
                    agentName, request.getMessage(), userId, request.getSessionId());
            return Result.success(response);
        } catch (Exception e) {
            log.error("Agent调用失败: {}", e.getMessage(), e);
            return Result.error("Agent执行失败: " + e.getMessage());
        }
    }

    @PostMapping("/chat")
    public Result<AgentResponse> autoRoute(@RequestBody AgentChatRequest request) {
        log.info("Auto route, message length: {}",
                request.getMessage() != null ? request.getMessage().length() : 0);

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.error("消息不能为空");
        }

        try {
            Long userId = UserContext.getCurrentId();
            AgentResponse response = orchestrator.autoRoute(
                    request.getMessage(), userId, request.getSessionId());
            return Result.success(response);
        } catch (Exception e) {
            log.error("Agent路由失败: {}", e.getMessage(), e);
            return Result.error("Agent执行失败: " + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<String>> listAgents() {
        return Result.success(agentRegistry.allAgentNames());
    }

    @Data
    public static class AgentChatRequest {
        private String message;
        private Long userId;
        private String sessionId;
    }
}
