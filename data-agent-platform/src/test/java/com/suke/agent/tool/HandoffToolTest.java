/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-02
 * @description HandoffTool 白名单校验和 HandoffContext 记录测试
 */
package com.suke.agent.tool;

import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.core.HandoffContext;
import com.suke.agent.core.HandoffRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HandoffToolTest {

    private HandoffTool handoffTool;
    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .prompt("test")
                .handoffs(List.of("data_cleaner"))
                .build());
        registry.register(AgentDescriptor.builder()
                .name("data_cleaner")
                .prompt("test")
                .handoffs(List.of("data_analyst"))
                .build());
        registry.register(AgentDescriptor.builder()
                .name("web_scraper")
                .prompt("test")
                .build());
        handoffTool = new HandoffTool(registry);
    }

    @AfterEach
    void tearDown() {
        HandoffContext.clear();
    }

    @Test
    void handoffToAllowedTargetRecordsRequest() {
        HandoffContext.setCurrentAgent("data_analyst");
        String result = handoffTool.handoff("data_cleaner", "数据缺失率30%");

        assertTrue(result.contains("data_cleaner"));
        HandoffRequest pending = HandoffContext.getPending();
        assertNotNull(pending);
        assertEquals("data_analyst", pending.fromAgent());
        assertEquals("data_cleaner", pending.toAgent());
        assertEquals("数据缺失率30%", pending.reason());
    }

    @Test
    void handoffToDisallowedTargetReturnsError() {
        HandoffContext.setCurrentAgent("data_analyst");
        String result = handoffTool.handoff("web_scraper", "reason");

        assertTrue(result.contains("错误"));
        assertNull(HandoffContext.getPending());
    }

    @Test
    void handoffToNonExistentTargetReturnsError() {
        HandoffContext.setCurrentAgent("data_analyst");
        String result = handoffTool.handoff("non_existent", "reason");

        assertTrue(result.contains("错误"));
        assertNull(HandoffContext.getPending());
    }

    @Test
    void handoffWithNoCurrentAgentReturnsError() {
        String result = handoffTool.handoff("data_cleaner", "reason");

        assertTrue(result.contains("错误"));
        assertNull(HandoffContext.getPending());
    }

    @Test
    void handoffWithNoHandoffsConfiguredReturnsError() {
        HandoffContext.setCurrentAgent("web_scraper");
        String result = handoffTool.handoff("data_analyst", "reason");

        assertTrue(result.contains("错误"));
        assertNull(HandoffContext.getPending());
    }
}
