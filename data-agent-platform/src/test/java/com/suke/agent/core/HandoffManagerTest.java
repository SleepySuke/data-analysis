package com.suke.agent.core;

import com.suke.agent.trace.AgentTraceService;
import com.suke.agent.trace.HandoffRecord;
import com.suke.agent.trace.mapper.AgentTraceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class HandoffManagerTest {

    private HandoffManager handoffManager;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        AgentTraceMapper traceMapper = mock(AgentTraceMapper.class);
        AgentTraceService traceService = new AgentTraceService(traceMapper);
        handoffManager = new HandoffManager(registry, traceService);

        registry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .prompt("test")
                .handoffs(List.of("data_cleaner", "web_scraper"))
                .build());

        registry.register(AgentDescriptor.builder()
                .name("data_cleaner")
                .prompt("test")
                .handoffs(List.of("data_analyst"))
                .build());

        registry.register(AgentDescriptor.builder()
                .name("web_scraper")
                .prompt("test")
                .handoffs(List.of("data_analyst"))
                .build());
    }

    @Test
    void validateAllowsAllowedTarget() {
        HandoffRequest request = new HandoffRequest("data_analyst", "data_cleaner", "dirty", null);
        assertDoesNotThrow(() -> handoffManager.validate(Collections.emptyList(), request));
    }

    @Test
    void validateRejectsDisallowedTarget() {
        HandoffRequest badRequest = new HandoffRequest("data_analyst", "sql_analyst", "reason", null);
        assertThrows(IllegalArgumentException.class,
                () -> handoffManager.validate(Collections.emptyList(), badRequest));
    }

    @Test
    void validateRejectsCyclicHandoff() {
        List<HandoffRecord> history = new ArrayList<>();
        history.add(HandoffRecord.builder()
                .fromAgent("data_analyst").toAgent("data_cleaner").build());
        history.add(HandoffRecord.builder()
                .fromAgent("data_cleaner").toAgent("data_analyst").build());

        HandoffRequest request = new HandoffRequest("data_analyst", "data_cleaner", "again", null);
        assertThrows(IllegalStateException.class,
                () -> handoffManager.validate(history, request));
    }

    @Test
    void validateAllowsFirstRoundTrip() {
        // history: analyst→cleaner (A→B)
        List<HandoffRecord> history = new ArrayList<>();
        history.add(HandoffRecord.builder()
                .fromAgent("data_analyst").toAgent("data_cleaner").build());

        // cleaner→analyst (B→A): 首次往返，应允许
        HandoffRequest request = new HandoffRequest("data_cleaner", "data_analyst", "done", null);
        assertDoesNotThrow(() -> handoffManager.validate(history, request));
    }

    @Test
    void validateRejectsMaxHandoffs() {
        List<HandoffRecord> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(HandoffRecord.builder()
                    .fromAgent("data_analyst").toAgent("data_cleaner").build());
            history.add(HandoffRecord.builder()
                    .fromAgent("data_cleaner").toAgent("data_analyst").build());
        }
        // 10 records total, well over MAX_HANDOFFS=5
        // Use a registered agent as fromAgent so it passes the whitelist check
        HandoffRequest request = new HandoffRequest("data_analyst", "web_scraper", "overflow", null);
        assertThrows(IllegalStateException.class,
                () -> handoffManager.validate(history, request));
    }

    @Test
    void recordCreatesAndStoresHandoffRecord() {
        HandoffRequest request = new HandoffRequest("data_analyst", "data_cleaner", "dirty", "some context");

        HandoffRecord record = handoffManager.record(request, "sess1");

        assertEquals("data_analyst", record.getFromAgent());
        assertEquals("data_cleaner", record.getToAgent());
        assertEquals("dirty", record.getReason());
        assertEquals("some context", record.getContext());
        assertTrue(record.getTimestamp() > 0);

        List<HandoffRecord> history = handoffManager.getHandoffHistory("sess1");
        assertEquals(1, history.size());
        assertEquals(record, history.get(0));
    }

    @Test
    void clearSessionRemovesHistory() {
        HandoffRequest request = new HandoffRequest("data_analyst", "data_cleaner", "dirty", null);
        handoffManager.record(request, "sess1");

        handoffManager.clearSession("sess1");

        assertTrue(handoffManager.getHandoffHistory("sess1").isEmpty());
    }

    @Test
    void isCyclicHandoffDetectsConsecutiveRoundTrips() {
        // a→b, b→a, a→b: 连续往返，consecutiveRoundTrips=2
        List<HandoffRecord> history = new ArrayList<>();
        history.add(HandoffRecord.builder().fromAgent("a").toAgent("b").build());
        history.add(HandoffRecord.builder().fromAgent("b").toAgent("a").build());

        // a→b: 第二次连续往返
        assertTrue(handoffManager.isCyclicHandoff(history, "a", "b"));
    }

    @Test
    void isCyclicHandoffAllowsFirstRoundTrip() {
        // a→b: 只有一步
        List<HandoffRecord> history = new ArrayList<>();
        history.add(HandoffRecord.builder().fromAgent("a").toAgent("b").build());

        // b→a: 首次往返，consecutiveRoundTrips=1，允许
        assertFalse(handoffManager.isCyclicHandoff(history, "b", "a"));
    }

    @Test
    void isCyclicHandoffAllowsTriangleFlow() {
        // 三角流转: a→b, b→c, c→a — 不是循环
        List<HandoffRecord> history = new ArrayList<>();
        history.add(HandoffRecord.builder().fromAgent("a").toAgent("b").build());
        history.add(HandoffRecord.builder().fromAgent("b").toAgent("c").build());
        history.add(HandoffRecord.builder().fromAgent("c").toAgent("a").build());

        // a→b: 虽然之前出现过 a→b，但不是连续往返，应允许
        assertFalse(handoffManager.isCyclicHandoff(history, "a", "b"));
    }

    @Test
    void isCyclicHandoffAllowsFreshPair() {
        List<HandoffRecord> history = new ArrayList<>();
        assertFalse(handoffManager.isCyclicHandoff(history, "a", "b"));
    }
}
