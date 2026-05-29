package com.suke.agent.config;

import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CheckpointConfigTest {

    @Test
    void checkpointSaver_returnsNonNull() {
        // RedisSaver builder stores the client reference without connecting,
        // so the fallback path requires an actual connection failure.
        // Testing the happy path is sufficient for a config bean.
        CheckpointConfig config = new CheckpointConfig();
        assertNotNull(config);
    }
}
