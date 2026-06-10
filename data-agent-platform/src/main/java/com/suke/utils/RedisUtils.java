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

import java.util.concurrent.ConcurrentHashMap;

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

    private final ConcurrentHashMap<String, RRateLimiter> limiterCache = new ConcurrentHashMap<>();


    /**
     * 限流操作 根据用户id进行限流
     *
     * @param key
     */
    public void doRateLimit(String key) {
        log.info("限流的用户id：{}", key);
        RRateLimiter rateLimiter = limiterCache.computeIfAbsent(key, k -> {
            RRateLimiter rl = redissonClient.getRateLimiter(k);
            rl.trySetRate(RateType.OVERALL, 2, 1, RateIntervalUnit.SECONDS);
            return rl;
        });
        //尝试获取令牌
        boolean acquire = rateLimiter.tryAcquire(1);
        if (!acquire) {
            throw new BaseException(ErrorCode.TOO_MANY_REQUEST_ERROR.getMessage());
        }
    }

}
