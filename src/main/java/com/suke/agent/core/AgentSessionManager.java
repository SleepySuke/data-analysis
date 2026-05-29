/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 会话管理器，基于内存的ConcurrentHashMap会话存储
 */

package com.suke.agent.core;

import com.suke.agent.domain.vo.AgentSessionVO;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AgentSessionManager {

    private final Map<String, AgentSessionVO> sessions = new ConcurrentHashMap<>();

    public AgentSessionVO createSession() {
        String sessionId = "session-" + UUID.randomUUID().toString().substring(0, 8);
        AgentSessionVO session = new AgentSessionVO(
                sessionId,
                "新对话",
                Instant.now().toString()
        );
        sessions.put(sessionId, session);
        return session;
    }

    public List<AgentSessionVO> listSessions() {
        return List.copyOf(sessions.values());
    }

    public boolean exists(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }
}
