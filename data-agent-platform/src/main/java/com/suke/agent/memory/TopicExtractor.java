/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 主题提取器，基于语义聚类提取用户关注主题
 */

package com.suke.agent.memory;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.memory.mapper.UserProfileMapper;
import com.suke.agent.memory.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
public class TopicExtractor {

    private static final int MAX_TOPICS = 10;
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final int LABEL_MAX_LENGTH = 20;

    private final EmbeddingModel embeddingModel;
    private final UserProfileMapper userProfileMapper;

    public TopicExtractor(EmbeddingModel embeddingModel, UserProfileMapper userProfileMapper) {
        this.embeddingModel = embeddingModel;
        this.userProfileMapper = userProfileMapper;
    }

    /**
     * 提取用户消息的语义 topic，与已有 topic 做向量聚类。
     * 返回匹配或新建的 topic 标签。
     */
    public String extractAndMatch(Long userId, String message) {
        if (message == null || message.isBlank()) {
            return null;
        }

        float[] newEmbedding = computeEmbedding(message);
        if (newEmbedding == null) {
            return extractLabel(message);
        }

        UserProfile profile = userProfileMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<UserProfile>()
                        .eq(UserProfile::getUserId, userId));

        String existingJson = (profile != null && profile.getFrequentTopics() != null)
                ? profile.getFrequentTopics() : "[]";

        String updatedJson = matchOrCreateTopic(existingJson, newEmbedding, extractLabel(message));

        if (profile != null) {
            profile.setFrequentTopics(updatedJson);
            userProfileMapper.updateById(profile);
        }

        // 从更新后的 JSON 中提取最终 topic
        JSONArray topics = JSON.parseArray(updatedJson);
        return topics.getJSONObject(topics.size() - 1).getString("topic");
    }

    public String matchOrCreateTopic(String existingJson, float[] newEmbedding, String label) {
        JSONArray topics;
        try {
            topics = JSON.parseArray(existingJson);
            if (topics == null) topics = new JSONArray();
        } catch (Exception e) {
            topics = new JSONArray();
        }

        // 寻找最相似的已有 topic
        double maxSimilarity = -1;
        int bestMatchIdx = -1;
        for (int i = 0; i < topics.size(); i++) {
            JSONObject entry = topics.getJSONObject(i);
            JSONArray embArr = entry.getJSONArray("embedding");
            if (embArr == null) continue;
            float[] existingEmb = toFloatArray(embArr);
            double sim = cosineSimilarity(newEmbedding, existingEmb);
            if (sim > maxSimilarity) {
                maxSimilarity = sim;
                bestMatchIdx = i;
            }
        }

        if (maxSimilarity >= SIMILARITY_THRESHOLD && bestMatchIdx >= 0) {
            // 归入已有 topic，count++
            topics.getJSONObject(bestMatchIdx).put("count",
                    topics.getJSONObject(bestMatchIdx).getIntValue("count") + 1);
        } else {
            // 创建新 topic
            JSONObject newEntry = new JSONObject();
            newEntry.put("topic", label);
            newEntry.put("count", 1);
            newEntry.put("embedding", toJSONArray(newEmbedding));

            if (topics.size() >= MAX_TOPICS) {
                // 已达上限：替换 count 最低的
                int minIdx = findMinCountIndex(topics);
                topics.set(minIdx, newEntry);
            } else {
                topics.add(newEntry);
            }
        }

        // 安全兜底：防止历史数据超过上限
        trimTopics(topics);

        return topics.toJSONString();
    }

    public static String extractLabel(String message) {
        if (message == null || message.isBlank()) return "";
        if (message.length() <= LABEL_MAX_LENGTH) return message;
        return message.substring(0, LABEL_MAX_LENGTH) + "...";
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0;
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private float[] computeEmbedding(String text) {
        if (embeddingModel == null) return null;
        try {
            EmbeddingResponse response = embeddingModel.embedForResponse(List.of(text));
            if (response != null && !response.getResults().isEmpty()) {
                return response.getResults().get(0).getOutput();
            }
        } catch (Exception e) {
            log.warn("Embedding computation failed: {}", e.getMessage());
        }
        return null;
    }

    private float[] toFloatArray(JSONArray arr) {
        float[] result = new float[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            result[i] = arr.getFloatValue(i);
        }
        return result;
    }

    private JSONArray toJSONArray(float[] arr) {
        JSONArray ja = new JSONArray(arr.length);
        for (float v : arr) ja.add(v);
        return ja;
    }

    private void trimTopics(JSONArray topics) {
        while (topics.size() > MAX_TOPICS) {
            int minIdx = findMinCountIndex(topics);
            topics.remove(minIdx);
        }
    }

    private int findMinCountIndex(JSONArray topics) {
        int minIdx = 0;
        int minCount = Integer.MAX_VALUE;
        for (int i = 0; i < topics.size(); i++) {
            int count = topics.getJSONObject(i).getIntValue("count");
            if (count < minCount) {
                minCount = count;
                minIdx = i;
            }
        }
        return minIdx;
    }
}
