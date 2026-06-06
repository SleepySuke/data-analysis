/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent控制器，提供SSE流式对话和同步对话接口
 */

package com.suke.agent.controller;

import com.suke.agent.core.*;
import com.suke.agent.core.sse.*;
import com.suke.agent.domain.dto.AgentChatDTO;
import com.suke.agent.domain.vo.*;
import com.suke.common.Result;
import com.suke.context.UserContext;
import com.suke.utils.FileUtils;
import com.suke.utils.RedisUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final AgentSessionManager sessionManager;
    private final RedisUtils redisUtils;
    private final IntentRouter intentRouter;

    public AgentController(AgentOrchestrator orchestrator, AgentRegistry agentRegistry,
                           AgentSessionManager sessionManager, RedisUtils redisUtils,
                           IntentRouter intentRouter) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
        this.sessionManager = sessionManager;
        this.redisUtils = redisUtils;
        this.intentRouter = intentRouter;
    }

    // ========== Session 管理 ==========

    @PostMapping("/session")
    public Result<AgentSessionVO> createSession() {
        log.info("创建新会话");
        return Result.success(sessionManager.createSession());
    }

    @GetMapping("/session")
    public Result<List<AgentSessionVO>> listSessions() {
        log.info("查询会话列表");
        return Result.success(sessionManager.listSessions());
    }

    // ========== SSE 流式对话（前端对接主入口） ==========

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(
            @RequestParam String sessionId,
            @RequestParam String message,
            @RequestParam(required = false) MultipartFile[] files) {

        log.info("SSE流式对话, sessionId: {}, message length: {}, files: {}",
                sessionId,
                message != null ? message.length() : 0,
                files != null ? files.length : 0);

        SseEmitter emitter = new SseEmitter(180_000L);

        try {
            Long userId = UserContext.getCurrentId();
            redisUtils.doRateLimit("agent:rate:" + userId);

            String enrichedMessage = enrichWithFiles(message, files);
            String targetAgent = intentRouter.route(enrichedMessage);

            log.info("SSE流式对话路由到: {}, userId: {}", targetAgent, userId);

            Disposable subscription = orchestrator
                    .streamingCall(targetAgent, enrichedMessage, userId, sessionId)
                    .filter(event -> !(event instanceof AgentStreamEndEvent))
                    .subscribe(
                            event -> {
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name(event.type())
                                            .data(event, MediaType.APPLICATION_JSON));
                                } catch (Exception e) {
                                    log.warn("SSE发送失败，客户端可能已断开: {}", e.getMessage());
                                    try { emitter.complete(); } catch (Exception ignored) {}
                                }
                            },
                            error -> {
                                log.error("SSE流异常: {}", error.getMessage());
                                try {
                                    emitter.send(SseEmitter.event()
                                            .name("error")
                                            .data(new ErrorEvent("连接异常"), MediaType.APPLICATION_JSON));
                                } catch (Exception ignored) {}
                                emitter.complete();
                            },
                            () -> {
                                log.info("SSE流式对话完成, sessionId: {}", sessionId);
                                emitter.complete();
                            }
                    );

            emitter.onCompletion(subscription::dispose);
            emitter.onTimeout(() -> {
                log.warn("SSE连接超时, sessionId: {}", sessionId);
                subscription.dispose();
                emitter.complete();
            });
            emitter.onError(e -> {
                log.warn("SSE连接错误: {}", e.getMessage());
                subscription.dispose();
            });

        } catch (Exception e) {
            log.error("SSE流式对话初始化失败: {}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data(new ErrorEvent("初始化失败"), MediaType.APPLICATION_JSON));
            } catch (Exception ignored) {}
            emitter.complete();
        }

        return emitter;
    }

    // ========== 同步对话（保留兼容） ==========

    @PostMapping("/{agentName}/chat")
    public Result<AgentResponseVO> directCall(
            @PathVariable String agentName,
            @RequestBody AgentChatDTO request) {
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
            redisUtils.doRateLimit("agent:rate:" + userId);
            AgentResponse response = orchestrator.directCall(
                    agentName, request.getMessage(), userId, request.getSessionId());
            return Result.success(toVO(response));
        } catch (Exception e) {
            log.error("Agent调用失败: {}", e.getMessage(), e);
            return Result.error("Agent执行失败");
        }
    }

    @PostMapping("/chat")
    public Result<AgentResponseVO> autoRoute(@RequestBody AgentChatDTO request) {
        log.info("Auto route, message length: {}",
                request.getMessage() != null ? request.getMessage().length() : 0);

        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return Result.error("消息不能为空");
        }

        try {
            Long userId = UserContext.getCurrentId();
            redisUtils.doRateLimit("agent:rate:" + userId);
            AgentResponse response = orchestrator.autoRoute(
                    request.getMessage(), userId, request.getSessionId());
            return Result.success(toVO(response));
        } catch (Exception e) {
            log.error("Agent路由失败: {}", e.getMessage(), e);
            return Result.error("Agent执行失败");
        }
    }

    @GetMapping("/list")
    public Result<List<String>> listAgents() {
        return Result.success(agentRegistry.allAgentNames());
    }

    private String enrichWithFiles(String message, MultipartFile[] files) {
        if (files == null || files.length == 0) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        for (MultipartFile file : files) {
            try {
                String originalName = file.getOriginalFilename();
                log.info("解析上传文件: {}", originalName);
                String csvData;
                if (originalName != null && (originalName.endsWith(".xlsx") || originalName.endsWith(".xls"))) {
                    csvData = FileUtils.excelToCsv(file);
                } else {
                    csvData = new String(file.getBytes());
                }
                sb.append("\n\n[上传文件: ").append(originalName).append("]\n");
                sb.append(csvData);
                log.info("文件解析完成: {}, 数据长度: {}", originalName, csvData.length());
            } catch (Exception e) {
                log.warn("文件解析失败: {}", e.getMessage());
                sb.append("\n\n[文件解析失败: ").append(file.getOriginalFilename()).append("]");
            }
        }
        return sb.toString();
    }

    private AgentResponseVO toVO(AgentResponse response) {
        return new AgentResponseVO()
                .setOutput(response.getOutput())
                .setTraceId(response.getTraceId())
                .setTotalTokens(response.getTotalTokens())
                .setDurationMs(response.getDurationMs())
                .setStatus(response.getStatus())
                .setHandoffAgents(response.getHandoffs() != null
                        ? response.getHandoffs().stream()
                        .map(h -> h.getToAgent()).collect(Collectors.toList())
                        : List.of());
    }
}
