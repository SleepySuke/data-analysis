package com.suke.agent.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HandoffContextTest {

    @AfterEach
    void tearDown() {
        HandoffContext.clear();
    }

    @Test
    void setAndGetPending() {
        HandoffRequest request = new HandoffRequest("analyst", "cleaner", "dirty data", null);
        HandoffContext.setPending(request);

        HandoffRequest result = HandoffContext.getPending();
        assertNotNull(result);
        assertEquals("analyst", result.fromAgent());
        assertEquals("cleaner", result.toAgent());
        assertEquals("dirty data", result.reason());
    }

    @Test
    void clearRemovesPending() {
        HandoffContext.setPending(new HandoffRequest("a", "b", "r", null));
        HandoffContext.clear();
        assertNull(HandoffContext.getPending());
    }

    @Test
    void setCurrentAgent() {
        HandoffContext.setCurrentAgent("data_analyst");
        assertEquals("data_analyst", HandoffContext.getCurrentAgent());
    }

    @Test
    void clearRemovesCurrentAgent() {
        HandoffContext.setCurrentAgent("data_analyst");
        HandoffContext.clear();
        assertNull(HandoffContext.getCurrentAgent());
    }

    @Test
    void threadLocalIsolation() throws Exception {
        HandoffContext.setCurrentAgent("main_agent");
        HandoffContext.setPending(new HandoffRequest("a", "b", "r", null));

        Thread t = new Thread(() -> {
            assertNull(HandoffContext.getCurrentAgent());
            assertNull(HandoffContext.getPending());

            HandoffContext.setCurrentAgent("thread_agent");
            HandoffContext.setPending(new HandoffRequest("x", "y", "z", null));
        });
        t.start();
        t.join();

        assertEquals("main_agent", HandoffContext.getCurrentAgent());
        assertEquals("a", HandoffContext.getPending().fromAgent());
    }
}
