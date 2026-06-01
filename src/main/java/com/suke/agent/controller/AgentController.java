/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent控制器，提供SSE流式对话和同步对话接口
 */

package com.suke.agent.controller;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.core.*;
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

import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final AgentRegistry agentRegistry;
    private final AgentSessionManager sessionManager;
    private final RedisUtils redisUtils;
    private final ExecutorService sseExecutor = new ThreadPoolExecutor(
            4, 32, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy());

    public AgentController(AgentOrchestrator orchestrator, AgentRegistry agentRegistry,
                           AgentSessionManager sessionManager, RedisUtils redisUtils) {
        this.orchestrator = orchestrator;
        this.agentRegistry = agentRegistry;
        this.sessionManager = sessionManager;
        this.redisUtils = redisUtils;
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

        sseExecutor.execute(() -> {
            try {
                Long userId = UserContext.getCurrentId();
                redisUtils.doRateLimit("agent:rate:" + userId);

                String enrichedMessage = enrichWithFiles(message, files);
                log.info("SSE流式对话开始处理, userId: {}", userId);

                AgentResponse response = orchestrator.autoRoute(
                        enrichedMessage, userId, sessionId);
                log.info("SSE流式对话Agent执行完成, traceId: {}", response.getTraceId());

                ArtifactAdapter.ParseResult parsed =
                        ArtifactAdapter.parse(response.getOutput(), "data_analyst");

                // 流式发送 token
                String content = parsed.content();
                log.info("SSE开始发送token, content length: {}", content.length());
                int chunkSize = 3;
                for (int i = 0; i < content.length(); i += chunkSize) {
                    String chunk = content.substring(i,
                            Math.min(i + chunkSize, content.length()));
                    emitter.send(SseEmitter.event()
                            .name("token")
                            .data(Map.of("content", chunk), MediaType.APPLICATION_JSON));
                    Thread.sleep(20);
                }

                // 发送 artifact
                log.info("SSE开始发送artifacts, count: {}", parsed.artifacts().size());
                for (ArtifactVO artifact : parsed.artifacts()) {
                    emitter.send(SseEmitter.event()
                            .name("artifact")
                            .data(artifact, MediaType.APPLICATION_JSON));
                    Thread.sleep(50);
                }

                emitter.send(SseEmitter.event().name("done").data("{}"));
                log.info("SSE流式对话完成, sessionId: {}", sessionId);
                emitter.complete();

            } catch (Exception e) {
                log.error("SSE流式对话失败: {}", e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(e.getMessage() != null ? e.getMessage() : "未知错误"));
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

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
            return Result.error("Agent执行失败: " + e.getMessage());
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
            return Result.error("Agent执行失败: " + e.getMessage());
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
