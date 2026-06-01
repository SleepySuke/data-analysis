package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.Agent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentSpecTest {

    @Test
    void reactSpecCreation() {
        AgentSpec spec = AgentSpec.react(
                "test_agent", "测试Agent", "You are a test",
                List.of("other_agent"), List.of(new Object()));

        assertEquals("test_agent", spec.name());
        assertEquals("测试Agent", spec.description());
        assertEquals("You are a test", spec.prompt());
        assertEquals(List.of("other_agent"), spec.handoffs());
        assertEquals(1, spec.toolInstances().size());
        assertEquals(AgentSpec.AgentType.REACT, spec.type());
        assertNull(spec.customBuilder());
    }

    @Test
    void customSpecCreation() {
        Agent fakeAgent = mock(Agent.class);
        AgentSpec spec = AgentSpec.custom(
                "custom_agent", "自定义Agent",
                List.of("other_agent"),
                () -> fakeAgent);

        assertEquals("custom_agent", spec.name());
        assertEquals("自定义Agent", spec.description());
        assertEquals(AgentSpec.AgentType.CUSTOM, spec.type());
        assertNotNull(spec.customBuilder());
        assertSame(fakeAgent, spec.customBuilder().get());
    }

    @Test
    void reactSpecWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentSpec.react(null, "desc", "prompt",
                        List.of(), List.of(new Object())));
    }

    @Test
    void reactSpecWithBlankNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentSpec.react("  ", "desc", "prompt",
                        List.of(), List.of(new Object())));
    }

    @Test
    void reactSpecWithEmptyToolsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentSpec.react("agent", "desc", "prompt",
                        List.of(), List.of()));
    }

    @Test
    void customSpecWithNullNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AgentSpec.custom(null, "desc", List.of(), () -> mock(Agent.class)));
    }

    @Test
    void typeEnumValues() {
        assertEquals(2, AgentSpec.AgentType.values().length);
        assertNotNull(AgentSpec.AgentType.valueOf("REACT"));
        assertNotNull(AgentSpec.AgentType.valueOf("CUSTOM"));
    }

    private static Agent mock(Class<Agent> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
