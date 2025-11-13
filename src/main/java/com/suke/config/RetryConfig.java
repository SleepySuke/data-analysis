package com.suke.config;

import com.github.rholder.retry.*;
import com.suke.exception.AIDockingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * @author 自然醒
 * @version 1.0
 */
//重试机制
@Configuration
@Slf4j
public class RetryConfig {

    @Bean
    public Retryer<String> aiAnalyzeRetryer(){
        return RetryerBuilder.<String>newBuilder()
                // 只在特定可重试异常时重试
                .retryIfExceptionOfType(AIDockingException.class)
                .retryIfException(e ->
                        e.getMessage().contains("网络") ||
                                e.getMessage().contains("超时") ||
                                e.getMessage().contains("繁忙")
                )
                // 不重试客户端错误
                .retryIfException(e -> !e.getMessage().contains("参数错误"))
                // 等待策略：固定间隔2秒
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
                // 停止策略：最多重试2次（总共3次尝试）
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                //阻塞策略：线程睡眠
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                //重试监听器
                .withRetryListener(new RetryListener(){
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if(attempt.hasException()){
                            log.warn("重试第{}次，异常信息：{}",attempt.getAttemptNumber(),attempt.getExceptionCause().getMessage());
                        } else if (attempt.hasResult()) {
                            log.info("第{}次重试成功，结果：{}",attempt.getAttemptNumber(),attempt.getResult());
                        }
                    }
                })
                .build();
    }
}
