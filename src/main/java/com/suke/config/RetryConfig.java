package com.suke.config;

import com.github.rholder.retry.*;
import com.suke.exception.AIDockingException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author 自然醒
 * @version 1.0
 */
//重试机制
@Configuration
@Slf4j
public class RetryConfig {

    @Bean(name = "aiAnalyzeRetryer")
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


    @Bean(name = "syncAnalyzeRetryer")
    public Retryer<String> syncAnalyzeRetryer() {
        return RetryerBuilder.<String>newBuilder()
                // 同步分析的重试器，配置更激进的重试策略
                .retryIfException(e ->
                        e.getMessage().contains("网络") ||
                                e.getMessage().contains("超时") ||
                                e.getMessage().contains("繁忙")
                )
                // 不重试token超限
                .retryIfException(throwable -> {
                    if (throwable instanceof AIDockingException) {
                        String message = throwable.getMessage();
                        return !(message != null &&
                                (message.contains("token") ||
                                        message.contains("Token")));
                    }
                    return true;
                })
                .retryIfResult(StringUtils::isBlank)
                // 更短的等待时间，因为同步方法用户等待
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
                // 更少的重试次数
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
                .withRetryListener(new RetryListener() {
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        if (attempt.hasException()) {
                            log.warn("同步分析第{}次重试失败: {}",
                                    attempt.getAttemptNumber(),
                                    attempt.getExceptionCause().getMessage());
                        }
                    }
                })
                .build();
    }

    /**
     * 判断是否为可重试的网络异常
     */
    private boolean isRetryableNetworkException(Throwable throwable) {
        // 检查异常链中的每个异常
        Throwable cause = throwable;
        while (cause != null) {
            // 1. Netty超时异常
            if (cause instanceof ReadTimeoutException ||
                    cause instanceof TimeoutException ||
                    cause instanceof io.netty.handler.timeout.WriteTimeoutException) {
                log.info("检测到Netty超时异常，触发重试: {}", cause.getClass().getSimpleName());
                return true;
            }

            // 2. Java标准超时异常
            if (cause instanceof SocketTimeoutException ||
                    cause instanceof java.net.ConnectException ||
                    cause instanceof java.net.UnknownHostException) {
                log.info("检测到Java网络异常，触发重试: {}", cause.getClass().getSimpleName());
                return true;
            }

            // 3. Spring资源访问异常（通常是IO异常包装）
            if (cause instanceof ResourceAccessException) {
                log.info("检测到ResourceAccessException，触发重试");
                return true;
            }

            // 4. 检查异常消息中的关键词
            String message = cause.getMessage();
            if (message != null) {
                String lowerMsg = message.toLowerCase();
                if (lowerMsg.contains("timeout") ||
                        lowerMsg.contains("read timeout") ||
                        lowerMsg.contains("connect timeout") ||
                        lowerMsg.contains("connection") ||
                        lowerMsg.contains("io exception") ||
                        lowerMsg.contains("network") ||
                        lowerMsg.contains("socket") ||
                        lowerMsg.contains("繁忙") ||
                        lowerMsg.contains("超时")) {
                    log.info("检测到关键词'{}'，触发重试", lowerMsg);
                    return true;
                }
            }

            cause = cause.getCause();
        }

        return false;
    }
}
