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
}
