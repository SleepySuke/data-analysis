/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 对话历史管理器，判断首轮和压缩决策
 */

package com.suke.agent.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ConversationHistoryManager {

    private final BaseCheckpointSaver checkpointSaver;
    private final ContextCompressor contextCompressor;
    private final int tokenBudget;

    public ConversationHistoryManager(BaseCheckpointSaver checkpointSaver,
                                       ContextCompressor contextCompressor,
                                       @Value("${agent.context.token-budget:8000}") int tokenBudget) {
        this.checkpointSaver = checkpointSaver;
        this.contextCompressor = contextCompressor;
        this.tokenBudget = tokenBudget;
    }

    public boolean isFirstTurn(String sessionId) {
        return getHistoryMessageCount(sessionId) == 0;
    }

    @SuppressWarnings("unchecked")
    public List<Message> getHistoryMessages(String sessionId) {
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        Optional<Checkpoint> checkpoint = checkpointSaver.get(config);
        if (checkpoint.isEmpty()) {
            return List.of();
        }

        Object messagesObj = checkpoint.get().getState().get("messages");
        if (messagesObj instanceof List<?> list) {
            return list.stream()
                    .filter(m -> m instanceof Message)
                    .map(m -> (Message) m)
                    .toList();
        }
        return List.of();
    }

    public int getHistoryMessageCount(String sessionId) {
        return getHistoryMessages(sessionId).size();
    }

    public boolean shouldCompress(String sessionId) {
        List<Message> history = getHistoryMessages(sessionId);
        if (history.isEmpty()) {
            return false;
        }
        return contextCompressor.estimateTokens(history) > tokenBudget;
    }

    public List<Message> compressHistory(String sessionId) {
        List<Message> history = getHistoryMessages(sessionId);
        if (history.isEmpty()) {
            return List.of();
        }
        return contextCompressor.compressWithSummary(history, tokenBudget);
    }
}
