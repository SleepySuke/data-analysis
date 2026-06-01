package com.suke.agent.hook;

import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProfileFirstHookTest {

    private ProfileFirstHook hook;
    private ToolCallHandler handler;

    @BeforeEach
    void setUp() {
        hook = new ProfileFirstHook();
        handler = mock(ToolCallHandler.class);
        when(handler.call(any())).thenAnswer(inv -> {
            ToolCallRequest req = inv.getArgument(0);
            return ToolCallResponse.of("ok", req.getToolName(), req.getToolCallId());
        });
    }

    private ToolInterceptor getInterceptor() {
        List<ToolInterceptor> interceptors = hook.getToolInterceptors();
        assertEquals(1, interceptors.size());
        return interceptors.get(0);
    }

    @Test
    void allowsProfileFirst() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("profileData").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertFalse(response.isError());
        verify(handler).call(request);
    }

    @Test
    void blocksCleanBeforeProfile() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("handleMissingValues").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertTrue(response.isError());
        verify(handler, never()).call(any());
    }

    @Test
    void allowsCleanAfterProfile() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest profileRequest = ToolCallRequest.builder()
                .toolName("profileData").arguments("{}").toolCallId("1").build();
        interceptor.interceptToolCall(profileRequest, handler);

        ToolCallRequest cleanRequest = ToolCallRequest.builder()
                .toolName("handleMissingValues").arguments("{}").toolCallId("2").build();
        ToolCallResponse response = interceptor.interceptToolCall(cleanRequest, handler);

        assertFalse(response.isError());
        verify(handler, times(2)).call(any());
    }

    @Test
    void blocksOutlierBeforeProfile() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("detectOutliers").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertTrue(response.isError());
    }

    @Test
    void allowsScriptAnytime() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("executeScript").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertFalse(response.isError());
        verify(handler).call(request);
    }

    @Test
    void independentRequestsAreIsolatedViaBeforeAgent() {
        ToolInterceptor interceptor = getInterceptor();

        // Request A: profile then clean → pass
        ToolCallRequest profileA = ToolCallRequest.builder()
                .toolName("profileData").arguments("{}").toolCallId("a1").build();
        interceptor.interceptToolCall(profileA, handler);
        ToolCallRequest cleanA = ToolCallRequest.builder()
                .toolName("handleMissingValues").arguments("{}").toolCallId("a2").build();
        ToolCallResponse responseA = interceptor.interceptToolCall(cleanA, handler);
        assertFalse(responseA.isError(), "Request A should pass after profile");

        // Simulate beforeAgent() — clears state for new request
        hook.calledTools.get().clear();

        // Request B: clean without profile → must be blocked
        ToolCallRequest cleanB = ToolCallRequest.builder()
                .toolName("handleMissingValues").arguments("{}").toolCallId("b1").build();
        ToolCallResponse responseB = interceptor.interceptToolCall(cleanB, handler);
        assertTrue(responseB.isError(), "Request B must be blocked — state reset by beforeAgent");
    }

    @Test
    void afterAgentCleansUpThreadLocal() {
        ToolInterceptor interceptor = getInterceptor();

        hook.calledTools.get().add("profileData");
        assertFalse(hook.calledTools.get().isEmpty(), "State should exist before cleanup");

        hook.calledTools.remove();
        assertTrue(hook.calledTools.get().isEmpty(), "ThreadLocal should be clean after afterAgent");
    }

    @Test
    void threadSafetyAcrossConcurrentRequests() throws Exception {
        ToolInterceptor interceptor = getInterceptor();
        int threadCount = 10;
        Thread[] threads = new Thread[threadCount];
        boolean[] results = new boolean[threadCount];

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                hook.calledTools.get().clear();
                try {
                    if (idx % 2 == 1) {
                        ToolCallRequest profileReq = ToolCallRequest.builder()
                                .toolName("profileData").arguments("{}")
                                .toolCallId("t" + idx + "-profile").build();
                        interceptor.interceptToolCall(profileReq, handler);
                    }

                    ToolCallRequest cleanReq = ToolCallRequest.builder()
                            .toolName("handleMissingValues").arguments("{}")
                            .toolCallId("t" + idx + "-clean").build();
                    ToolCallResponse response = interceptor.interceptToolCall(cleanReq, handler);

                    results[idx] = (idx % 2 == 1) ? !response.isError() : response.isError();
                } finally {
                    hook.calledTools.remove();
                }
            });
        }

        for (Thread t : threads) t.start();
        for (Thread t : threads) t.join();

        for (int i = 0; i < threadCount; i++) {
            if (i % 2 == 1) {
                assertTrue(results[i], "Thread " + i + " (with profile first) should pass");
            } else {
                assertTrue(results[i], "Thread " + i + " (without profile) should be blocked");
            }
        }
    }
}
