/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 长期记忆存储，基于数据库的用户偏好和行为记录
 */

package com.suke.agent.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.memory.model.InteractionLog;
import com.suke.agent.memory.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class LongTermMemoryStore {

    private final UserProfileMapper userProfileMapper;
    private final InteractionLogMapper interactionLogMapper;
    private final UserProfileInjector profileInjector;

    public LongTermMemoryStore(UserProfileMapper userProfileMapper,
                                InteractionLogMapper interactionLogMapper,
                                UserProfileInjector profileInjector) {
        this.userProfileMapper = userProfileMapper;
        this.interactionLogMapper = interactionLogMapper;
        this.profileInjector = profileInjector;
    }

    public String buildMemoryContext(Long userId, String agentName) {
        UserProfile profile = getProfile(userId);
        if (profile == null) {
            return "";
        }
        List<String> recentTopics = getRecentTopics(userId, 5);
        return profileInjector.inject(profile, agentName, recentTopics);
    }

    public String buildMemoryContext(Long userId) {
        return buildMemoryContext(userId, null);
    }

    public UserProfile getProfile(Long userId) {
        return userProfileMapper.selectOne(
                new LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId));
    }

    public void updateProfile(Long userId, UserProfile profile) {
        UserProfile existing = getProfile(userId);
        profile.setUserId(userId);
        if (existing == null) {
            userProfileMapper.insert(profile);
        } else {
            profile.setId(existing.getId());
            userProfileMapper.updateById(profile);
        }
    }

    public List<String> getRecentTopics(Long userId, int limit) {
        try {
            List<String> topics = interactionLogMapper.selectRecentTopics(userId, limit);
            return topics != null ? topics : new ArrayList<>();
        } catch (Exception e) {
            log.warn("Failed to query recent topics for user {}: {}", userId, e.getMessage());
            return new ArrayList<>();
        }
    }

    public void trackInteraction(Long userId, String sessionId, String agentName,
                                  String intent, String topic,
                                  int tokensUsed, int durationMs) {
        try {
            InteractionLog logEntry = new InteractionLog();
            logEntry.setUserId(userId);
            logEntry.setSessionId(sessionId);
            logEntry.setAgentName(agentName);
            logEntry.setIntent(intent);
            logEntry.setTopic(topic);
            logEntry.setTokensUsed(tokensUsed);
            logEntry.setDurationMs(durationMs);
            interactionLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("Failed to track interaction for user {}: {}", userId, e.getMessage());
        }
    }
}
