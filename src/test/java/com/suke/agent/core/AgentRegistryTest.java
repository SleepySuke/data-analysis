package com.suke.agent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AgentRegistryTest {

    private AgentRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
    }

    private AgentDescriptor buildDescriptor(String name) {
        return AgentDescriptor.builder()
                .name(name)
                .description("Test agent: " + name)
                .prompt("You are " + name)
                .tools(Collections.emptyList())
                .handoffs(Collections.emptyList())
                .build();
    }

    @Test
    void registerAndGetDescriptor() {
        registry.register(buildDescriptor("data_analyst"));

        AgentDescriptor descriptor = registry.getDescriptor("data_analyst");

        assertEquals("data_analyst", descriptor.getName());
        assertEquals("Test agent: data_analyst", descriptor.getDescription());
    }

    @Test
    void registerDuplicateThrows() {
        registry.register(buildDescriptor("data_analyst"));

        assertThrows(IllegalStateException.class, () ->
                registry.register(buildDescriptor("data_analyst")));
    }

    @Test
    void getNonExistentThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                registry.getDescriptor("non_existent"));
    }

    @Test
    void allAgentNames() {
        registry.register(buildDescriptor("data_analyst"));
        registry.register(buildDescriptor("web_scraper"));

        List<String> names = registry.allAgentNames();

        assertEquals(2, names.size());
        assertTrue(names.contains("data_analyst"));
        assertTrue(names.contains("web_scraper"));
    }

    @Test
    void registerWithNullNameThrows() {
        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name(null)
                .description("no name")
                .prompt("empty")
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(descriptor));
    }

    @Test
    void registerWithBlankNameThrows() {
        AgentDescriptor descriptor = AgentDescriptor.builder()
                .name("  ")
                .description("blank name")
                .prompt("empty")
                .build();

        assertThrows(IllegalArgumentException.class, () -> registry.register(descriptor));
    }

    @Test
    void existsReturnsTrueForRegistered() {
        registry.register(buildDescriptor("data_analyst"));

        assertTrue(registry.exists("data_analyst"));
        assertFalse(registry.exists("unknown"));
    }
}
