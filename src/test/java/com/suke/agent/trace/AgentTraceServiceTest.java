package com.suke.agent.trace;

import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentTraceServiceTest {

    @Mock
    private AgentTraceMapper traceMapper;

    private AgentTraceService traceService;

    @BeforeEach
    void setUp() {
        traceService = new AgentTraceService(traceMapper);
    }

    @Test
    void generateTraceIdReturnsNonBlank() {
        String traceId = traceService.generateTraceId();

        assertNotNull(traceId);
        assertFalse(traceId.isBlank());
        assertEquals(32, traceId.length());
    }

    @Test
    void saveTraceCallsInsert() {
        AgentTrace trace = AgentTrace.builder()
                .traceId("abc123")
                .sessionId("sess1")
                .userId(1L)
                .targetAgent("data_analyst")
                .build();

        traceService.saveTrace(trace);

        verify(traceMapper).insert((AgentTrace) any());
    }

    @Test
    void saveEmptyTraceIdSkips() {
        AgentTrace trace = AgentTrace.builder()
                .traceId("")
                .build();

        traceService.saveTrace(trace);

        verify(traceMapper, never()).insert((AgentTrace) any());
    }

    @Test
    void saveNullTraceIdSkips() {
        AgentTrace trace = AgentTrace.builder().build();

        traceService.saveTrace(trace);

        verify(traceMapper, never()).insert((AgentTrace) any());
    }

    @Test
    void saveTraceHandlesDbError() {
        when(traceMapper.insert((AgentTrace) any())).thenThrow(new RuntimeException("DB error"));

        AgentTrace trace = AgentTrace.builder()
                .traceId("abc123")
                .build();

        assertDoesNotThrow(() -> traceService.saveTrace(trace));
    }
}
