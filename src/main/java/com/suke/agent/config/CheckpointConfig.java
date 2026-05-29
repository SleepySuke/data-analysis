/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 检查点配置，配置Redis和内存检查点存储
 */

package com.suke.agent.config;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class CheckpointConfig {

    @Bean
    public BaseCheckpointSaver checkpointSaver(RedissonClient redissonClient) {
        try {
            BaseCheckpointSaver saver = RedisSaver.builder()
                    .redisson(redissonClient)
                    .build();
            log.info("CheckpointSaver: using RedisSaver");
            return saver;
        } catch (Exception e) {
            log.error("RedisSaver init failed, falling back to MemorySaver (sessions will be lost on restart): {}", e.getMessage());
            return MemorySaver.builder().build();
        }
    }
}
