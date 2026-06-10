/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 数据保留调度器，定时清理过期交互日志
 */

package com.suke.agent.config;

import com.suke.agent.core.HandoffManager;
import com.suke.agent.memory.mapper.InteractionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataRetentionScheduler {

    private final InteractionLogMapper interactionLogMapper;
    private final HandoffManager handoffManager;

    @Scheduled(cron = "0 0 3 * * *")
    public void purgeOldInteractionLogs() {
        try {
            int deleted = interactionLogMapper.purgeOldLogs();
            if (deleted > 0) {
                log.info("Purged {} interaction logs older than 90 days", deleted);
            }
        } catch (Exception e) {
            log.error("Failed to purge old interaction logs", e);
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    public void evictExpiredHandoffSessions() {
        try {
            handoffManager.evictExpiredSessions();
        } catch (Exception e) {
            log.error("Failed to evict expired handoff sessions", e);
        }
    }
}
