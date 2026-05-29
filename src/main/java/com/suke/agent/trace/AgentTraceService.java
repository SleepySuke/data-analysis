/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Agent追踪服务，异步持久化和查询追踪记录
 */

package com.suke.agent.trace;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AgentTraceService {

    private final AgentTraceMapper traceMapper;

    public AgentTraceService(AgentTraceMapper traceMapper) {
        this.traceMapper = traceMapper;
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void saveTrace(AgentTrace trace) {
        if (trace.getTraceId() == null || trace.getTraceId().isBlank()) {
            log.warn("Trace ID is empty, skipping save");
            return;
        }
        try {
            traceMapper.insert(trace);
            log.debug("Saved trace: {}", trace.getTraceId());
        } catch (Exception e) {
            log.error("Failed to save trace: {}", trace.getTraceId(), e);
        }
    }

    @Async
    public void saveTraceAsync(AgentTrace trace) {
        saveTrace(trace);
    }

    public AgentTrace getByTraceId(String traceId) {
        return traceMapper.selectOne(
                new LambdaQueryWrapper<AgentTrace>()
                        .eq(AgentTrace::getTraceId, traceId));
    }

    public List<AgentTrace> getRecentTraces(Long userId, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return traceMapper.selectList(
                new LambdaQueryWrapper<AgentTrace>()
                        .eq(AgentTrace::getUserId, userId)
                        .orderByDesc(AgentTrace::getCreateTime)
                        .last("LIMIT " + safeLimit));
    }
}
