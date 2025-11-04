package com.suke.utils;

import com.suke.common.ErrorCode;
import com.suke.exception.BaseException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author 自然醒
 * @version 1.0
 */
//Redis工具类
@Slf4j
@Component
public class RedisUtils {

    @Autowired
    private RedissonClient redissonClient;


    /**
     * 限流操作 根据用户id进行限流
     *
     * @param key
     */
    public void doRateLimit(String key) {
        log.info("限流的用户id：{}", key);
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(key);
        //设置限流策略
        //每秒2个请求，连续的请求，最多只有1个请求被允许通过
        //RateType.OVERALL表示速率限制作用于整个令牌桶,限制所有请求的速率
        rateLimiter.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);
        //尝试获取令牌
        boolean acquire = rateLimiter.tryAcquire(1);
        if (!acquire) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUEST_ERROR.getMessage());
        }
    }

}
