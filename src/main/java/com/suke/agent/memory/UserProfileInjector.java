/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 用户画像注入器，将长期记忆注入Agent上下文
 */

package com.suke.agent.memory;

import com.suke.agent.memory.model.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Component
public class UserProfileInjector {

    // Agent → 需要注入的 profile 字段
    private static final Map<String, Set<String>> AGENT_FIELDS = Map.of(
            "data_analyst", Set.of("industry", "preferredCharts", "detailLevel", "reportStyle"),
            "sql_analyst", Set.of("industry", "expertise"),
            "data_cleaner", Set.of("industry"),
            "web_scraper", Set.of()
    );

    public String inject(UserProfile profile, String agentName, List<String> recentTopics) {
        if (profile == null) {
            return "";
        }

        Set<String> relevantFields = AGENT_FIELDS.getOrDefault(
                agentName != null ? agentName : "data_analyst",
                Set.of("industry", "preferredCharts", "detailLevel", "reportStyle"));

        if (relevantFields.isEmpty()) {
            return ""; // 该 agent 不需要用户画像
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[用户记忆]\n");

        if (relevantFields.contains("industry") && isPresent(profile.getIndustry())) {
            sb.append("行业: ").append(profile.getIndustry()).append("\n");
        }
        if (relevantFields.contains("expertise") && isPresent(profile.getExpertise())) {
            sb.append("专业领域: ").append(profile.getExpertise()).append("\n");
        }
        if (relevantFields.contains("preferredCharts") && isPresent(profile.getPreferredCharts())) {
            sb.append("偏好图表: ").append(profile.getPreferredCharts()).append("\n");
        }
        if (relevantFields.contains("detailLevel") && isPresent(profile.getDetailLevel())) {
            sb.append("详情级别: ").append(profile.getDetailLevel()).append("\n");
        }
        if (relevantFields.contains("reportStyle") && isPresent(profile.getReportStyle())) {
            sb.append("报告风格: ").append(profile.getReportStyle()).append("\n");
        }

        // 近期交互主题（来自 InteractionLog 查询）
        if (recentTopics != null && !recentTopics.isEmpty()) {
            sb.append("近期关注: ").append(String.join(", ", recentTopics)).append("\n");
        }

        return sb.toString();
    }

    // 兼容旧接口
    public String inject(UserProfile profile) {
        return inject(profile, null, null);
    }

    private boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}
