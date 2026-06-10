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

class SchemaFirstHookTest {

    private SchemaFirstHook hook;
    private ToolCallHandler handler;

    @BeforeEach
    void setUp() {
        hook = new SchemaFirstHook();
        handler = mock(ToolCallHandler.class);
        when(handler.call(any())).thenAnswer(inv -> {
            ToolCallRequest req = inv.getArgument(0);
            return ToolCallResponse.of("ok", req.getToolName(), req.getToolCallId());
        });
    }

    private ToolInterceptor getInterceptor() {
        // Simulates framework behavior: getToolInterceptors() called once, cached
        List<ToolInterceptor> interceptors = hook.getToolInterceptors();
        assertEquals(1, interceptors.size());
        return interceptors.get(0);
    }

    @Test
    void allowsSchemaIntrospectFirst() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("introspectSchema").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertFalse(response.isError());
        verify(handler).call(request);
    }

    @Test
    void blocksSqlBeforeSchema() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest sqlRequest = ToolCallRequest.builder()
                .toolName("executeSql").arguments("{\"sql\":\"SELECT 1\"}").toolCallId("2").build();
        ToolCallResponse response = interceptor.interceptToolCall(sqlRequest, handler);

        assertTrue(response.isError());
        verify(handler, never()).call(any());
    }

    @Test
    void allowsSqlAfterSchema() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest schemaRequest = ToolCallRequest.builder()
                .toolName("introspectSchema").arguments("{}").toolCallId("1").build();
        interceptor.interceptToolCall(schemaRequest, handler);

        ToolCallRequest sqlRequest = ToolCallRequest.builder()
                .toolName("executeSql").arguments("{\"sql\":\"SELECT 1\"}").toolCallId("2").build();
        ToolCallResponse response = interceptor.interceptToolCall(sqlRequest, handler);

        assertFalse(response.isError());
        verify(handler, times(2)).call(any());
    }

    @Test
    void allowsResultInterpreterAny() {
        ToolInterceptor interceptor = getInterceptor();
        ToolCallRequest request = ToolCallRequest.builder()
                .toolName("interpretResult").arguments("{}").toolCallId("1").build();
        ToolCallResponse response = interceptor.interceptToolCall(request, handler);

        assertFalse(response.isError());
        verify(handler).call(request);
    }

    @Test
    void independentRequestsAreIsolatedViaBeforeAgent() {
        // Use the SAME interceptor instance (simulates framework caching)
        ToolInterceptor interceptor = getInterceptor();

        // Request A: call schema, then sql → should pass
        ToolCallRequest schemaA = ToolCallRequest.builder()
                .toolName("introspectSchema").arguments("{}").toolCallId("a1").build();
        interceptor.interceptToolCall(schemaA, handler);
        ToolCallRequest sqlA = ToolCallRequest.builder()
                .toolName("executeSql").arguments("{}").toolCallId("a2").build();
        ToolCallResponse responseA = interceptor.interceptToolCall(sqlA, handler);
        assertFalse(responseA.isError(), "Request A should pass after schema");

        // Simulate beforeAgent() — clears state for new request
        hook.calledTools.get().clear();

        // Request B: sql without schema → should be blocked
        ToolCallRequest sqlB = ToolCallRequest.builder()
                .toolName("executeSql").arguments("{}").toolCallId("b1").build();
        ToolCallResponse responseB = interceptor.interceptToolCall(sqlB, handler);
        assertTrue(responseB.isError(), "Request B must be blocked — state reset by beforeAgent");
    }

    @Test
    void afterAgentCleansUpThreadLocal() {
        ToolInterceptor interceptor = getInterceptor();

        // Simulate a request cycle
        hook.calledTools.get().add("introspectSchema");
        assertFalse(hook.calledTools.get().isEmpty(), "State should exist before cleanup");

        // Simulate afterAgent cleanup
        hook.calledTools.remove();

        // After remove, a new get() should return fresh empty set
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
                // Each thread simulates beforeAgent → intercept calls → afterAgent
                hook.calledTools.get().clear();
                try {
                    // Odd threads: call schema first, then sql → should pass
                    // Even threads: call sql directly → should be blocked
                    if (idx % 2 == 1) {
                        ToolCallRequest schemaReq = ToolCallRequest.builder()
                                .toolName("introspectSchema").arguments("{}")
                                .toolCallId("t" + idx + "-schema").build();
                        interceptor.interceptToolCall(schemaReq, handler);
                    }

                    ToolCallRequest sqlReq = ToolCallRequest.builder()
                            .toolName("executeSql").arguments("{}")
                            .toolCallId("t" + idx + "-sql").build();
                    ToolCallResponse response = interceptor.interceptToolCall(sqlReq, handler);

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
                assertTrue(results[i], "Thread " + i + " (with schema first) should pass");
            } else {
                assertTrue(results[i], "Thread " + i + " (without schema) should be blocked");
            }
        }
    }
}
