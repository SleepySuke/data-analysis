/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户行为追踪器，记录交互日志和频繁主题
 */

package com.suke.agent.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.memory.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class UserBehaviorTracker {

    private static final int MAX_TOPICS = 10;
    private final UserProfileMapper userProfileMapper;

    public UserBehaviorTracker(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Async
    @Transactional
    public void updateFrequentTopics(Long userId, String topic) {
        if (topic == null || topic.isBlank()) {
            return;
        }
        try {
            UserProfile profile = userProfileMapper.selectOne(
                    new LambdaQueryWrapper<UserProfile>()
                            .eq(UserProfile::getUserId, userId));

            if (profile == null) {
                profile = new UserProfile();
                profile.setUserId(userId);
                profile.setFrequentTopics(buildNewTopicsJson(topic).toJSONString());
                userProfileMapper.insert(profile);
                return;
            }

            JSONArray topics = parseTopics(profile.getFrequentTopics());
            updateTopicCount(topics, topic);
            profile.setFrequentTopics(sortAndTrim(topics));
            userProfileMapper.updateById(profile);
        } catch (Exception e) {
            log.warn("Failed to update frequent topics for user {}: {}", userId, e.getMessage());
        }
    }

    private JSONArray buildNewTopicsJson(String topic) {
        JSONArray arr = new JSONArray();
        JSONObject entry = new JSONObject();
        entry.put("topic", topic);
        entry.put("count", 1);
        arr.add(entry);
        return arr;
    }

    private JSONArray parseTopics(String json) {
        if (json == null || json.isBlank()) {
            return new JSONArray();
        }
        try {
            return JSON.parseArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private void updateTopicCount(JSONArray topics, String topic) {
        for (int i = 0; i < topics.size(); i++) {
            JSONObject entry = topics.getJSONObject(i);
            if (topic.equals(entry.getString("topic"))) {
                entry.put("count", entry.getIntValue("count") + 1);
                return;
            }
        }
        JSONObject newEntry = new JSONObject();
        newEntry.put("topic", topic);
        newEntry.put("count", 1);
        topics.add(newEntry);
    }

    private String sortAndTrim(JSONArray topics) {
        topics.sort((a, b) -> ((JSONObject) b).getIntValue("count") - ((JSONObject) a).getIntValue("count"));
        JSONArray trimmed = new JSONArray();
        for (int i = 0; i < Math.min(topics.size(), MAX_TOPICS); i++) {
            trimmed.add(topics.get(i));
        }
        return trimmed.toJSONString();
    }
}
