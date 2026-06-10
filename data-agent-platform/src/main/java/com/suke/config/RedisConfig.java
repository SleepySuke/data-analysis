package com.suke.config;

import lombok.Data;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.util.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Redis配置，基于Redisson的分布式锁和数据操作
 */
//redis 配置
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisConfig {

    private Integer database;

    private String password;

    private String host;

    private Integer port;


    /**
     * 创建RedissonClient
     * @return
     */
    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database);
        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }
        return Redisson.create(config);
    }
}
