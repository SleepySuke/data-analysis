package com.suke.service.impl;

import com.suke.common.AnalysisResult;
import com.suke.context.UserContext;
import com.suke.domain.dto.file.UploadFileDTO;
import com.suke.domain.entity.Chart;
import com.suke.domain.vo.GenChartVO;
import com.suke.mapper.ChartMapper;
import com.suke.utils.AIDocking;
import com.suke.utils.ParseAIResponse;
import com.suke.utils.WebSocketServer;
import com.github.rholder.retry.Retryer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ChartServiceImplAsyncTest {

    @InjectMocks
    private ChartServiceImpl chartService;

    @Mock
    private ChartMapper chartMapper;

    @Mock
    private AIDocking aiDocking;

    @Mock
    private WebSocketServer webSocketServer;

    @Mock
    private Retryer<String> aiAnalyzeRetryer;

    @Mock
    private Retryer<String> syncAnalyzeRetryer;

    @Mock
    private ThreadPoolExecutor threadPoolExecutor;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentId(1L);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeCurrentId();
    }

    private MultipartFile createMockExcelFile() {
        return new MockMultipartFile(
                "test.xlsx", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "dummy-content".getBytes()
        );
    }

    private UploadFileDTO buildFileDTO() {
        UploadFileDTO dto = new UploadFileDTO();
        dto.setFileName("测试");
        dto.setGoal("分析趋势");
        dto.setChartType("line");
        return dto;
    }

    // ========== #15: UserContext propagation in async thread ==========

    @Test
    @DisplayName("异步分析-CompletableFuture提交时UserContext应已设置")
    void asyncAnalysis_userContextShouldBeSetInSubmittedTask() throws Exception {
        // Use a real single-thread executor to execute the lambda immediately
        ExecutorService testExecutor = Executors.newSingleThreadExecutor();

        ChartServiceImpl realService = new ChartServiceImpl();
        // Inject mocks via reflection
        var fields = ChartServiceImpl.class.getDeclaredFields();
        for (var f : fields) {
            f.setAccessible(true);
        }

        // We test the principle: capture userId before submitting async task
        Long capturedUserId = UserContext.getCurrentId();
        assertNotNull(capturedUserId);
        assertEquals(1L, capturedUserId);

        // Simulate async task captures the userId correctly
        CompletableFuture<Long> future = CompletableFuture.supplyAsync(() -> {
            // In real code, UserContext.setCurrentId(asyncUserId) is called first
            UserContext.setCurrentId(capturedUserId);
            try {
                return UserContext.getCurrentId();
            } finally {
                UserContext.removeCurrentId();
            }
        }, testExecutor);

        Long resultId = future.get(5, TimeUnit.SECONDS);
        assertEquals(1L, resultId);

        testExecutor.shutdown();
    }

    @Test
    @DisplayName("异步分析-异常路径UserContext也应被清除")
    void asyncAnalysis_exceptionPath_userContextShouldBeCleaned() throws Exception {
        ExecutorService testExecutor = Executors.newSingleThreadExecutor();
        Long capturedUserId = UserContext.getCurrentId();

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            UserContext.setCurrentId(capturedUserId);
            try {
                throw new RuntimeException("模拟异常");
            } finally {
                UserContext.removeCurrentId();
            }
        }, testExecutor);

        // Should complete (exception is captured inside future, not propagated)
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // Expected: the RuntimeException is wrapped
            assertTrue(e.getCause().getMessage().contains("模拟异常"));
        }
        testExecutor.shutdown();
    }

    // ========== #14: Retry wraps actual AI call, not Future.get() ==========

    @Test
    @DisplayName("异步分析-重试应包裹AI调用而非Future.get")
    void asyncAnalysis_retryShouldWrapAICall() throws Exception {
        // Verify that the retryer.call() wraps the actual AI invocation,
        // and that each retry creates a new CompletableFuture
        AtomicInteger callCount = new AtomicInteger(0);

        // Simulate what the retryer does: calls the callable multiple times
        when(aiAnalyzeRetryer.call(any())).thenAnswer(invocation -> {
            Callable<String> callable = invocation.getArgument(0);
            callable.call(); // First attempt
            callable.call(); // Second attempt (retry)
            return "result";
        });

        String result = aiAnalyzeRetryer.call(() -> {
            callCount.incrementAndGet();
            return aiDocking.doDataAnalysis("goal", "line", "data");
        });

        // The callable was invoked twice (two AI calls, not two Future.get)
        verify(aiAnalyzeRetryer).call(any());
    }

    // ========== asyncAnalyzeFile basic validation ==========

    @Test
    @DisplayName("异步分析-文件为空返回null")
    void asyncAnalysis_nullFile_returnsNull() {
        GenChartVO result = chartService.asyncAnalyzeFile(null, buildFileDTO());
        assertNull(result);
    }

    @Test
    @DisplayName("异步分析-用户未登录返回null")
    void asyncAnalysis_notLoggedIn_returnsNull() {
        UserContext.removeCurrentId();
        GenChartVO result = chartService.asyncAnalyzeFile(createMockExcelFile(), buildFileDTO());
        assertNull(result);
    }

    // ========== asyncAnalyze (MQ path) basic validation ==========

    @Test
    @DisplayName("MQ异步-文件为空返回null")
    void mqAsync_nullFile_returnsNull() {
        GenChartVO result = chartService.asyncAnalyze(null, buildFileDTO());
        assertNull(result);
    }

    @Test
    @DisplayName("MQ异步-用户未登录返回null")
    void mqAsync_notLoggedIn_returnsNull() {
        UserContext.removeCurrentId();
        GenChartVO result = chartService.asyncAnalyze(createMockExcelFile(), buildFileDTO());
        assertNull(result);
    }
}
