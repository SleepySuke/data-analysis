/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 工作内存，管理会话级短期上下文信息
 */

package com.suke.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WorkingMemory {

    private static final String KEY_PREFIX = "working_memory:";
    private static final long DEFAULT_TTL_MINUTES = 30;

    private final RedissonClient redisson;

    public WorkingMemory(RedissonClient redisson) {
        this.redisson = redisson;
    }

    /**
     * 写入工作记忆。每个 key 独立拥有 30 分钟 TTL，put 时仅刷新当前 key 的过期时间，
     * 不会续期同一 sessionId 下的其他 key。
     *
     * 设计理由：RMapCache 的 entry-level TTL 保证不活跃的 key 自动淘汰，
     * 避免因单次 put 而让已过期的陈旧数据被意外保留。
     * 如需会话级整体续期，应由外层（如 AgentSessionManager）统一管理 session 生命周期。
     */
    public void put(String sessionId, String key, String value) {
        if (sessionId == null || key == null) return;
        try {
            RMapCache<String, String> map = getMap(sessionId);
            map.put(key, value, DEFAULT_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("WorkingMemory put failed for session {}: {}", sessionId, e.getMessage());
        }
    }

    public String get(String sessionId, String key) {
        if (sessionId == null || key == null) return null;
        try {
            return getMap(sessionId).get(key);
        } catch (Exception e) {
            log.warn("WorkingMemory get failed: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, String> getAll(String sessionId) {
        if (sessionId == null) return Map.of();
        try {
            return new HashMap<>(getMap(sessionId).readAllMap());
        } catch (Exception e) {
            log.warn("WorkingMemory getAll failed: {}", e.getMessage());
            return Map.of();
        }
    }

    public void clear(String sessionId) {
        if (sessionId == null) return;
        try {
            getMap(sessionId).clear();
        } catch (Exception e) {
            log.warn("WorkingMemory clear failed: {}", e.getMessage());
        }
    }

    public String buildContext(String sessionId) {
        Map<String, String> all = getAll(sessionId);
        if (all.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        sb.append("[工作记忆]\n");
        all.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        return sb.toString();
    }

    private RMapCache<String, String> getMap(String sessionId) {
        return redisson.getMapCache(KEY_PREFIX + sessionId);
    }
}
