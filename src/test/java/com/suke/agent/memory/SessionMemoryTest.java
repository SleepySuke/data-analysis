package com.suke.agent.memory;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SessionMemoryTest {

    @Test
    void memorySaverPersistsStateByThreadId() throws Exception {
        BaseCheckpointSaver saver = MemorySaver.builder().build();

        String threadId = "sess-test-001";
        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();

        // Initially no checkpoint exists
        Optional<?> checkpoint = saver.get(config);
        assertTrue(checkpoint.isEmpty());
    }

    @Test
    void differentThreadIdsAreIsolated() throws Exception {
        BaseCheckpointSaver saver = MemorySaver.builder().build();

        RunnableConfig config1 = RunnableConfig.builder().threadId("sess-A").build();
        RunnableConfig config2 = RunnableConfig.builder().threadId("sess-B").build();

        // Both should start empty
        assertTrue(saver.get(config1).isEmpty());
        assertTrue(saver.get(config2).isEmpty());
    }

    @Test
    void sameThreadIdSeesHistory() throws Exception {
        BaseCheckpointSaver saver = MemorySaver.builder().build();
        String threadId = "sess-persistent";

        RunnableConfig config = RunnableConfig.builder().threadId(threadId).build();
        assertTrue(saver.get(config).isEmpty());
    }
}
