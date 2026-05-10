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

@Configuration
@Slf4j
public class RetryConfig {

    @Bean(name = "aiAnalyzeRetryer")
    public Retryer<String> aiAnalyzeRetryer(){
        return RetryerBuilder.<String>newBuilder()
                .retryIfException(this::isRetryable)
                .retryIfResult(StringUtils::isBlank)
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                .withBlockStrategy(BlockStrategies.threadSleepStrategy())
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
                .retryIfException(this::isRetryable)
                .retryIfResult(StringUtils::isBlank)
                .withWaitStrategy(WaitStrategies.fixedWait(2, TimeUnit.SECONDS))
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

    private boolean isRetryable(Throwable throwable) {
        // Token超限不重试
        if (throwable instanceof AIDockingException) {
            String msg = throwable.getMessage();
            if (msg != null && (msg.contains("token") || msg.contains("Token"))) {
                return false;
            }
            // 其他 AIDockingException（网络/超时/繁忙）可重试
            return isRetryableNetworkException(throwable);
        }
        // 参数错误不重试
        String message = throwable.getMessage();
        if (message != null && message.contains("参数错误")) {
            return false;
        }
        // 只有网络类异常才重试
        return isRetryableNetworkException(throwable);
    }

    private boolean isRetryableNetworkException(Throwable throwable) {
        Throwable cause = throwable;
        while (cause != null) {
            if (cause instanceof ReadTimeoutException ||
                    cause instanceof TimeoutException ||
                    cause instanceof io.netty.handler.timeout.WriteTimeoutException) {
                return true;
            }
            if (cause instanceof SocketTimeoutException ||
                    cause instanceof java.net.ConnectException ||
                    cause instanceof java.net.UnknownHostException) {
                return true;
            }
            if (cause instanceof ResourceAccessException) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null) {
                String lowerMsg = message.toLowerCase();
                if (lowerMsg.contains("timeout") ||
                        lowerMsg.contains("connection") ||
                        lowerMsg.contains("io exception") ||
                        lowerMsg.contains("network") ||
                        lowerMsg.contains("socket") ||
                        lowerMsg.contains("繁忙") ||
                        lowerMsg.contains("超时")) {
                    return true;
                }
            }
            cause = cause.getCause();
        }
        return false;
    }
}
