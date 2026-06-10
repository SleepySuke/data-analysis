/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 上下文压缩器，在token超限时智能压缩对话历史
 */

package com.suke.agent.memory;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Slf4j
@Component
public class ContextCompressor {

    private static final double CHARS_PER_TOKEN_ENGLISH = 4.0;
    private static final double CHARS_PER_TOKEN_CHINESE = 1.5;

    private Function<List<Message>, String> summaryFn;
    private int maxTokens;
    private int keepRecentRounds;

    @Autowired
    public ContextCompressor(@Qualifier("deepseekClient") ChatClient summaryClient,
                             @Value("${agent.context.max-tokens:8000}") int maxTokens,
                             @Value("${agent.context.keep-recent-rounds:3}") int keepRecentRounds) {
        this.summaryFn = summaryClient != null
                ? (msgs) -> summaryClient.prompt().user(buildSummaryPrompt(msgs)).call().content()
                : null;
        this.maxTokens = maxTokens;
        this.keepRecentRounds = keepRecentRounds;
    }

    public int estimateTokensForText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : text.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) Math.ceil(chineseChars / CHARS_PER_TOKEN_CHINESE + otherChars / CHARS_PER_TOKEN_ENGLISH);
    }

    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int totalTokens = 0;
        for (Message msg : messages) {
            String text = msg.getText();
            if (text != null) {
                totalTokens += estimateTokensForText(text);
            }
        }
        return totalTokens;
    }

    public List<Message> compressWithSummary(List<Message> messages, int tokenBudget) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }

        int estimated = estimateTokens(messages);
        if (estimated <= tokenBudget) {
            return messages;
        }

        log.info("Context compression triggered: estimated {} tokens, budget {}", estimated, tokenBudget);

        int messagesToKeep = keepRecentRounds * 2;
        if (messages.size() <= messagesToKeep) {
            return messages;
        }

        List<Message> systemMessages = new ArrayList<>();
        List<Message> nonSystem = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                systemMessages.add(msg);
            } else {
                nonSystem.add(msg);
            }
        }

        int splitIdx = Math.max(0, nonSystem.size() - messagesToKeep);
        List<Message> toSummarize = nonSystem.subList(0, splitIdx);
        List<Message> recent = nonSystem.subList(splitIdx, nonSystem.size());

        String summary = generateSummary(toSummarize);

        List<Message> result = new ArrayList<>(systemMessages);
        if (summary != null && !summary.isBlank()) {
            result.add(new SystemMessage("[对话历史摘要] " + summary));
        }
        result.addAll(recent);

        log.info("Compressed from {} to {} messages (summary: {} chars)",
                messages.size(), result.size(), summary != null ? summary.length() : 0);
        return result;
    }

    private String generateSummary(List<Message> messages) {
        if (summaryFn == null) {
            log.debug("No summary function available, falling back to truncation");
            return null;
        }
        try {
            return summaryFn.apply(messages);
        } catch (Exception e) {
            log.warn("Summary generation failed, falling back to truncation: {}", e.getMessage());
            return null;
        }
    }

    private static String buildSummaryPrompt(List<Message> messages) {
        StringBuilder sb = new StringBuilder("请用中文简洁总结以下对话的要点（不超过200字）：\n\n");
        for (Message msg : messages) {
            String role = (msg instanceof UserMessage) ? "用户" : "助手";
            sb.append(role).append(": ").append(msg.getText()).append("\n");
        }
        return sb.toString();
    }

    public List<Message> compressIfNeeded(List<Message> messages, int tokenBudget) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int estimated = estimateTokens(messages);
        if (estimated <= tokenBudget) {
            return messages;
        }
        log.info("Context compression triggered: estimated {} tokens, budget {}", estimated, tokenBudget);
        return truncateToRecent(messages, keepRecentRounds);
    }

    public List<Message> truncateToRecent(List<Message> messages, int roundsToKeep) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int messagesToKeep = roundsToKeep * 2;
        if (messages.size() <= messagesToKeep) {
            return messages;
        }

        List<Message> result = new ArrayList<>();
        for (Message msg : messages) {
            if (msg instanceof SystemMessage) {
                result.add(msg);
            }
        }

        int startIdx = messages.size() - messagesToKeep;
        for (int i = startIdx; i < messages.size(); i++) {
            Message msg = messages.get(i);
            if (!(msg instanceof SystemMessage)) {
                result.add(msg);
            }
        }

        log.info("Truncated context from {} to {} messages", messages.size(), result.size());
        return result;
    }
}
