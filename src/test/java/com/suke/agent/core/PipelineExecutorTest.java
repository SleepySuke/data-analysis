/**
 * @author 自然醒
 */
package com.suke.agent.core;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.models.StepMode;
import com.suke.agent.core.models.StepResult;
import com.suke.agent.domain.entity.PlanStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PipelineExecutorTest {

    private PipelineExecutor pipelineExecutor;
    private MockChatService mockChatService;
    private ExecutorService executorService;
    private JSONObject fixture;

    @BeforeEach
    void setUp() throws Exception {
        mockChatService = new MockChatService();
        executorService = Executors.newFixedThreadPool(4);
        pipelineExecutor = new PipelineExecutor(mockChatService, null, executorService, 4, 60);

        // Load fixture
        try (InputStream is = getClass().getResourceAsStream("/fixture/pipeline_executor_expected.json")) {
            assertNotNull(is, "Fixture file not found");
            fixture = JSON.parseObject(new String(is.readAllBytes()));
        }
    }

    @Test
    void sequentialStepExecutesSingleAgent() {
        mockChatService.addResponse("data_analyst", AgentResponse.builder()
                .output(fixture.getJSONObject("sequentialStep").getString("output"))
                .status("success").totalTokens(100).durationMs(500).build());

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.SEQUENTIAL).agentName("data_analyst")
                .input("分析数据").expectedOutput("分析报告").build();

        StepResult result = pipelineExecutor.executeStep(step, 1L, "sess-1");

        JSONObject expected = fixture.getJSONObject("sequentialStep");
        assertEquals(expected.getString("output"), result.getOutput());
        assertEquals(expected.getString("status"), result.getStatus());
        assertFalse(result.isParallel());
        assertEquals(1, mockChatService.getCallCount("data_analyst"));
    }

    @Test
    void parallelStepExecutesMultipleAgents() {
        JSONObject expected = fixture.getJSONObject("parallelStepAllSuccess");
        JSONObject expectedOutputs = expected.getJSONObject("outputs");

        mockChatService.addResponse("web_scraper", AgentResponse.builder()
                .output(expectedOutputs.getJSONObject("web_scraper").getString("output"))
                .status("success").totalTokens(200).durationMs(800).build());
        mockChatService.addResponse("sql_analyst", AgentResponse.builder()
                .output(expectedOutputs.getJSONObject("sql_analyst").getString("output"))
                .status("success").totalTokens(150).durationMs(600).build());

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("web_scraper", "sql_analyst"))
                .input("获取多源数据").expectedOutput("整合报告").build();

        StepResult result = pipelineExecutor.executeStep(step, 1L, "sess-1");

        assertTrue(result.isParallel());
        assertNotNull(result.getOutputs());
        assertEquals(2, result.getOutputs().size());
        assertTrue(result.getOutputs().containsKey("web_scraper"));
        assertTrue(result.getOutputs().containsKey("sql_analyst"));
        assertEquals(expectedOutputs.getJSONObject("web_scraper").getString("output"),
                result.getOutputs().get("web_scraper").getOutput());
        assertEquals(expectedOutputs.getJSONObject("sql_analyst").getString("output"),
                result.getOutputs().get("sql_analyst").getOutput());
    }

    @Test
    void parallelMergesResults() {
        mockChatService.addResponse("a1", AgentResponse.builder()
                .output("A1结果").status("success").totalTokens(50).durationMs(100).build());
        mockChatService.addResponse("a2", AgentResponse.builder()
                .output("A2结果").status("success").totalTokens(50).durationMs(100).build());

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("a1", "a2"))
                .input("test").expectedOutput("result").build();

        StepResult result = pipelineExecutor.executeStep(step, 1L, "sess-1");

        assertNotNull(result.getOutput());
        assertTrue(result.getOutput().contains("A1结果"));
        assertTrue(result.getOutput().contains("A2结果"));
    }

    @Test
    void parallelPartialFailureHandled() {
        JSONObject expected = fixture.getJSONObject("parallelStepPartialFailure");

        mockChatService.addResponse("good_agent", AgentResponse.builder()
                .output("成功数据").status("success").totalTokens(50).durationMs(100).build());
        mockChatService.addResponse("bad_agent", AgentResponse.builder()
                .output("执行失败").status("failed").totalTokens(0).durationMs(50).build());

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("good_agent", "bad_agent"))
                .input("test").expectedOutput("result").build();

        StepResult result = pipelineExecutor.executeStep(step, 1L, "sess-1");

        assertTrue(result.isPartialFailure());
        assertEquals(expected.getString("status"), result.getStatus());
        assertTrue(result.getOutput().contains("成功数据"));
    }

    @Test
    void parallelTimeoutTerminates() {
        JSONObject expected = fixture.getJSONObject("parallelTimeout");

        MockChatService slowService = new MockChatService() {
            @Override
            public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
                try { Thread.sleep(5000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return AgentResponse.builder().output("迟到的结果").status("success").totalTokens(10).durationMs(5000).build();
            }
        };
        PipelineExecutor exec = new PipelineExecutor(slowService, null, Executors.newFixedThreadPool(2), 2, 1);

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("slow_agent"))
                .input("test").expectedOutput("result").build();

        StepResult result = exec.executeStep(step, 1L, "sess-1");

        assertEquals(expected.getString("status"), result.getStatus());
        assertTrue(result.getOutput().contains("超时"));
    }

    @Test
    void parallelMaxAgentsExceededThrows() {
        JSONObject expected = fixture.getJSONObject("maxAgentsExceeded");
        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("a1", "a2", "a3", "a4", "a5"))
                .input("test").expectedOutput("result").build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> pipelineExecutor.executeStep(step, 1L, "sess-1"));
        assertTrue(ex.getMessage().contains("5"));
        assertTrue(ex.getMessage().contains("4"));
    }

    @Test
    void sequentialExceptionReturnsFailed() {
        MockChatService failingService = new MockChatService() {
            @Override
            public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
                throw new RuntimeException("Agent error");
            }
        };
        PipelineExecutor exec = new PipelineExecutor(failingService, null, executorService, 4, 60);

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.SEQUENTIAL).agentName("bad_agent")
                .input("test").expectedOutput("result").build();

        StepResult result = exec.executeStep(step, 1L, "sess-1");
        assertEquals("failed", result.getStatus());
        assertTrue(result.getOutput().contains("步骤执行异常"));
    }

    @Test
    void parallelWithNullAgentNamesThrows() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(null)
                .input("test").expectedOutput("result").build();

        assertThrows(IllegalArgumentException.class,
                () -> pipelineExecutor.executeStep(step, 1L, "sess-1"));
    }

    @Test
    void parallelWithEmptyAgentNamesThrows() {
        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of())
                .input("test").expectedOutput("result").build();

        assertThrows(IllegalArgumentException.class,
                () -> pipelineExecutor.executeStep(step, 1L, "sess-1"));
    }

    @Test
    void totalTokensPropagatedFromParallelOutputs() {
        mockChatService.addResponse("a1", AgentResponse.builder()
                .output("A1").status("success").totalTokens(100).durationMs(50).build());
        mockChatService.addResponse("a2", AgentResponse.builder()
                .output("A2").status("success").totalTokens(200).durationMs(50).build());

        PlanStep step = PlanStep.builder()
                .stepIndex(0).mode(StepMode.PARALLEL)
                .agentNames(List.of("a1", "a2"))
                .input("test").expectedOutput("result").build();

        StepResult result = pipelineExecutor.executeStep(step, 1L, "sess-1");
        assertEquals(300, result.getTotalTokens());
    }

    static class MockChatService implements AgentChatService {
        private final ConcurrentHashMap<String, BlockingQueue<AgentResponse>> responses = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, AtomicInteger> callCounts = new ConcurrentHashMap<>();

        void addResponse(String agentName, AgentResponse response) {
            responses.computeIfAbsent(agentName, k -> new LinkedBlockingQueue<>()).add(response);
        }

        int getCallCount(String agentName) {
            return callCounts.getOrDefault(agentName, new AtomicInteger(0)).get();
        }

        @Override
        public AgentResponse directCall(String agentName, String message, Long userId, String sessionId) {
            callCounts.computeIfAbsent(agentName, k -> new AtomicInteger(0)).incrementAndGet();
            BlockingQueue<AgentResponse> queue = responses.get(agentName);
            if (queue != null && !queue.isEmpty()) {
                try { return queue.poll(100, TimeUnit.MILLISECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            return AgentResponse.builder().output("mock").status("success").totalTokens(10).durationMs(100).build();
        }

        @Override
        public reactor.core.publisher.Flux<com.suke.agent.core.sse.SseEvent> streamingCall(String agentName, String message, Long userId, String sessionId) {
            return reactor.core.publisher.Flux.empty();
        }
    }
}
