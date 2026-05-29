/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 追踪控制器，查询Agent执行追踪记录
 */

package com.suke.agent.controller;

import com.suke.agent.trace.AgentTrace;
import com.suke.agent.trace.AgentTraceService;
import com.suke.common.Result;
import com.suke.context.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/agent/traces")
public class TraceController {

    private final AgentTraceService traceService;

    public TraceController(AgentTraceService traceService) {
        this.traceService = traceService;
    }

    @GetMapping
    public Result<List<AgentTrace>> getRecentTraces(
            @RequestParam(defaultValue = "20") int limit) {
        Long userId = UserContext.getCurrentId();
        return Result.success(traceService.getRecentTraces(userId, limit));
    }

    @GetMapping("/{traceId}")
    public Result<AgentTrace> getTrace(@PathVariable String traceId) {
        AgentTrace trace = traceService.getByTraceId(traceId);
        if (trace == null) {
            return Result.error("Trace不存在: " + traceId);
        }
        return Result.success(trace);
    }
}
