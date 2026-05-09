package com.suke.config;

import com.github.rholder.retry.Retryer;
import com.suke.exception.AIDockingException;
import io.netty.handler.timeout.ReadTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.net.SocketTimeoutException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class RetryConfigTest {

    private final RetryConfig retryConfig = new RetryConfig();

    @Test
    @DisplayName("异步重试器-Token超限异常不应重试")
    void asyncRetryer_tokenExceeded_shouldNotRetry() {
        Retryer<String> retryer = retryConfig.aiAnalyzeRetryer();

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryer.call(() -> {
                    throw new AIDockingException("数据量过大导致token超限");
                })
        );
        assertInstanceOf(AIDockingException.class, ex.getCause());
    }

    @Test
    @DisplayName("同步重试器-Token超限异常不应重试")
    void syncRetryer_tokenExceeded_shouldNotRetry() {
        Retryer<String> retryer = retryConfig.syncAnalyzeRetryer();

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryer.call(() -> {
                    throw new AIDockingException("数据量过大导致Token超限");
                })
        );
        assertInstanceOf(AIDockingException.class, ex.getCause());
    }

    @Test
    @DisplayName("同步重试器-非网络异常不应重试（如NPE）")
    void syncRetryer_nonAiException_shouldNotRetryAll() {
        Retryer<String> retryer = retryConfig.syncAnalyzeRetryer();

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryer.call(() -> {
                    throw new NullPointerException("unexpected null");
                })
        );
        assertInstanceOf(NullPointerException.class, ex.getCause());
    }

    @Test
    @DisplayName("异步重试器-参数错误异常不应重试")
    void asyncRetryer_paramError_shouldNotRetry() {
        Retryer<String> retryer = retryConfig.aiAnalyzeRetryer();

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                retryer.call(() -> {
                    throw new RuntimeException("参数错误: invalid input");
                })
        );
        assertInstanceOf(RuntimeException.class, ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("参数错误"));
    }

    // ========== Positive retry cases ==========

    @Test
    @DisplayName("异步重试器-网络超时异常应重试并最终成功")
    void asyncRetryer_networkTimeout_shouldRetryAndSucceed() {
        Retryer<String> retryer = retryConfig.aiAnalyzeRetryer();
        AtomicInteger counter = new AtomicInteger(0);

        String result = assertDoesNotThrow(() ->
                retryer.call(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 3) {
                        throw new RuntimeException("connection timeout");
                    }
                    return "分析结果";
                })
        );
        assertEquals("分析结果", result);
        assertEquals(3, counter.get());
    }

    @Test
    @DisplayName("同步重试器-SocketTimeoutException应重试")
    void syncRetryer_socketTimeout_shouldRetry() {
        Retryer<String> retryer = retryConfig.syncAnalyzeRetryer();
        AtomicInteger counter = new AtomicInteger(0);

        String result = assertDoesNotThrow(() ->
                retryer.call(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        throw new RuntimeException(new SocketTimeoutException("read timed out"));
                    }
                    return "成功";
                })
        );
        assertEquals("成功", result);
        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("异步重试器-空白结果应触发重试")
    void asyncRetryer_blankResult_shouldRetry() {
        Retryer<String> retryer = retryConfig.aiAnalyzeRetryer();
        AtomicInteger counter = new AtomicInteger(0);

        String result = assertDoesNotThrow(() ->
                retryer.call(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        return "";
                    }
                    return "有效结果";
                })
        );
        assertEquals("有效结果", result);
        assertEquals(2, counter.get());
    }

    @Test
    @DisplayName("同步重试器-ResourceAccessException应重试")
    void syncRetryer_resourceAccess_shouldRetry() {
        Retryer<String> retryer = retryConfig.syncAnalyzeRetryer();
        AtomicInteger counter = new AtomicInteger(0);

        String result = assertDoesNotThrow(() ->
                retryer.call(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        throw new ResourceAccessException("I/O error on POST request");
                    }
                    return "成功";
                })
        );
        assertEquals("成功", result);
    }

    @Test
    @DisplayName("异步重试器-AIDockingException非token可重试")
    void asyncRetryer_aiDockingNonToken_shouldRetry() {
        Retryer<String> retryer = retryConfig.aiAnalyzeRetryer();
        AtomicInteger counter = new AtomicInteger(0);

        // AIDockingException with "network" in message should retry
        String result = assertDoesNotThrow(() ->
                retryer.call(() -> {
                    int attempt = counter.incrementAndGet();
                    if (attempt < 2) {
                        throw new AIDockingException("network error");
                    }
                    return "成功";
                })
        );
        assertEquals("成功", result);
        assertEquals(2, counter.get());
    }
}
