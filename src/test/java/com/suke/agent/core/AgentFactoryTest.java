package com.suke.agent.core;

import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentFactoryTest {

    private AgentFactory factory;
    private ChatModel chatModel;
    private BaseCheckpointSaver saver;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        saver = mock(BaseCheckpointSaver.class);

        Object dummyTool = new Object() {
            @org.springframework.ai.tool.annotation.Tool(description = "test")
            public String testTool(String input) { return "ok"; }
        };

        Agent fakeCustomAgent = mock(Agent.class);

        List<AgentSpec> specs = List.of(
                AgentSpec.react("react_agent", "React Agent", "prompt",
                        List.of("other"), List.of(dummyTool)),
                AgentSpec.custom("custom_agent", "Custom Agent",
                        List.of(), () -> fakeCustomAgent)
        );

        factory = new AgentFactory(chatModel, saver, specs);
    }

    @Test
    void buildReactAgentReturnsAgent() {
        Agent agent = factory.build("react_agent");
        assertNotNull(agent);
        assertInstanceOf(ReactAgent.class, agent);
    }

    @Test
    void buildCustomAgentReturnsAgent() {
        Agent agent = factory.build("custom_agent");
        assertNotNull(agent);
        assertFalse(agent instanceof ReactAgent,
                "Custom agent should not be a ReactAgent");
    }

    @Test
    void buildUnknownAgentThrows() {
        assertThrows(IllegalArgumentException.class, () -> factory.build("nonexistent"));
    }

    @Test
    void getToolsReturnsCorrectTools() {
        List<ToolCallback> tools = factory.getTools("react_agent");
        assertNotNull(tools);
        assertFalse(tools.isEmpty());
    }

    @Test
    void getToolsForCustomReturnsEmpty() {
        List<ToolCallback> tools = factory.getTools("custom_agent");
        assertNotNull(tools);
        assertTrue(tools.isEmpty());
    }

    @Test
    void getToolsForUnknownThrows() {
        assertThrows(IllegalArgumentException.class, () -> factory.getTools("nonexistent"));
    }

    @Test
    void buildDescriptorReturnsCompleteDescriptor() {
        AgentDescriptor descriptor = factory.buildDescriptor("react_agent");
        assertEquals("react_agent", descriptor.getName());
        assertEquals("React Agent", descriptor.getDescription());
        assertEquals("prompt", descriptor.getPrompt());
        assertNotNull(descriptor.getTools());
        assertFalse(descriptor.getTools().isEmpty());
        assertEquals(List.of("other"), descriptor.getHandoffs());
        assertNotNull(descriptor.getAgent());
    }

    @Test
    void agentTypesReturnsAllNames() {
        Set<String> types = factory.agentTypes();
        assertEquals(2, types.size());
        assertTrue(types.contains("react_agent"));
        assertTrue(types.contains("custom_agent"));
    }

    @Test
    void buildDescriptorForUnknownThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> factory.buildDescriptor("nonexistent"));
    }
}
