/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description LLM意图路由器，根据用户消息自动选择目标Agent
 */

package com.suke.agent.core;

import com.suke.agent.prompt.AgentPrompts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class IntentRouter {

    private final ChatClient routingClient;
    private final AgentRegistry agentRegistry;

    public IntentRouter(@Qualifier("deepseekClient") ChatClient routingClient,
                        AgentRegistry agentRegistry) {
        this.routingClient = routingClient;
        this.agentRegistry = agentRegistry;
    }

    public String route(String message) {
        String agentNames = String.join(", ", agentRegistry.allAgentNames());
        String prompt = AgentPrompts.SUPERVISOR
                + "\n\n当前已注册的Agent: " + agentNames
                + "\n\n用户消息: " + message
                + "\n\n请只回复Agent名称，不要任何解释。";

        try {
            String result = routingClient.prompt().user(prompt).call().content();
            String target = normalizeAgentName(result);
            if (target != null && agentRegistry.exists(target)) {
                log.info("LLM routed to: {}", target);
                return target;
            }
            log.warn("LLM returned unknown agent '{}', falling back to keywords", result);
        } catch (Exception e) {
            log.warn("LLM routing failed, falling back to keywords: {}", e.getMessage());
        }
        return fallbackByKeywords(message);
    }

    String normalizeAgentName(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String cleaned = raw.trim()
                .replaceAll("[`*\"'\\[\\]()]", "")
                .replaceAll("\\s+", "")
                .toLowerCase();
        // LLM may return "1. data_analyst" or "data_analyst（推荐）"
        String[] parts = cleaned.split("[.。、,，\\s]+");
        String candidate = parts[parts.length - 1];
        // Remove Chinese parenthetical suffix
        candidate = candidate.replaceAll("[（(].*[）)]", "");
        return candidate.isBlank() ? null : candidate;
    }

    String fallbackByKeywords(String message) {
        String lower = message.toLowerCase();
        if (lower.contains("抓取") || lower.contains("爬取") || lower.contains("网页") || lower.contains("url")) {
            return "web_scraper";
        }
        if (lower.contains("sql") || lower.contains("数据库") || lower.contains("查询")) {
            return "sql_analyst";
        }
        if (lower.contains("清洗") || lower.contains("缺失值") || lower.contains("异常值") || lower.contains("去重")) {
            return "data_cleaner";
        }
        return "data_analyst";
    }

    /**
     * 路由并判断意图复杂度。
     * 复杂特征：涉及 2+ Agent、多步骤（"先...然后...再..."）、复合分析
     */
    public IntentResult routeWithComplexity(String message) {
        String agent = route(message);

        boolean complex = isComplexByRules(message);
        String reason = complex ? "多步骤或复合意图" : "单步意图";

        return new IntentResult(agent, complex, reason);
    }

    private boolean isComplexByRules(String message) {
        if (message.contains("然后") || message.contains("接着") || message.contains("再")
                || message.contains("之后") || (message.contains("先") && message.contains("最后"))) {
            return true;
        }
        int agentKeywordCount = 0;
        String[] agentKeywords = {"清洗", "分析", "图表", "sql", "数据库", "抓取", "爬取"};
        for (String kw : agentKeywords) {
            if (message.toLowerCase().contains(kw)) agentKeywordCount++;
        }
        return agentKeywordCount >= 2;
    }

    public record IntentResult(String agentName, boolean complex, String reason) {}
}
