/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description McpToolConfig测试
 */
package com.suke.agent.config;

import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpToolConfigTest {

    @Test
    void disabledReturnsEmptyMap() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(false);

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of());

        assertTrue(result.isEmpty());
    }

    @Test
    void enabledWithNoClientsReturnsEmptyLists() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "browser"));

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of());

        assertTrue(result.containsKey("web_scraper"));
        assertTrue(result.get("web_scraper").isEmpty());
    }

    @Test
    void noBindingAgentNotInResult() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of());

        assertFalse(result.containsKey("data_analyst"));
        assertTrue(result.isEmpty());
    }

    @Test
    void unknownBindingServerReturnsEmptyTools() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "nonexistent_server"));

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of());

        assertTrue(result.containsKey("web_scraper"));
        assertTrue(result.get("web_scraper").isEmpty());
    }

    @Test
    void discoversToolsFromMatchingClient() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "browser"));

        McpSyncClient mockClient = mock(McpSyncClient.class);
        McpSchema.Implementation clientInfo =
                new McpSchema.Implementation("spring-ai-mcp-client - browser", "1.0");
        when(mockClient.getClientInfo()).thenReturn(clientInfo);

        var listToolsResult = mock(McpSchema.ListToolsResult.class);
        when(listToolsResult.tools()).thenReturn(List.of());
        when(mockClient.listTools()).thenReturn(listToolsResult);

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of(mockClient));

        assertTrue(result.containsKey("web_scraper"));
        // Lazy: listTools not called yet
        verify(mockClient, never()).listTools();
        // Trigger lazy discovery
        assertTrue(result.get("web_scraper").isEmpty());
        verify(mockClient, times(1)).listTools();
    }

    @Test
    void clientWithNoMatchingBindingIsIgnored() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "browser"));

        McpSyncClient mockClient = mock(McpSyncClient.class);
        McpSchema.Implementation clientInfo =
                new McpSchema.Implementation("spring-ai-mcp-client - filesystem", "1.0");
        when(mockClient.getClientInfo()).thenReturn(clientInfo);

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of(mockClient));

        assertTrue(result.containsKey("web_scraper"));
        assertTrue(result.get("web_scraper").isEmpty());
    }

    @Test
    void multipleBindingsWithDifferentClients() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of(
                "web_scraper", "browser",
                "file_agent", "filesystem"
        ));

        McpSyncClient browserClient = mock(McpSyncClient.class);
        when(browserClient.getClientInfo())
                .thenReturn(new McpSchema.Implementation("spring-ai-mcp-client - browser", "1.0"));
        var browserTools = mock(McpSchema.ListToolsResult.class);
        when(browserTools.tools()).thenReturn(List.of());
        when(browserClient.listTools()).thenReturn(browserTools);

        McpSyncClient fsClient = mock(McpSyncClient.class);
        when(fsClient.getClientInfo())
                .thenReturn(new McpSchema.Implementation("spring-ai-mcp-client - filesystem", "1.0"));
        var fsTools = mock(McpSchema.ListToolsResult.class);
        when(fsTools.tools()).thenReturn(List.of());
        when(fsClient.listTools()).thenReturn(fsTools);

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result =
                config.buildAgentMcpTools(List.of(browserClient, fsClient));

        assertTrue(result.containsKey("web_scraper"));
        assertTrue(result.containsKey("file_agent"));
        // Lazy: tools not discovered yet
        verify(browserClient, never()).listTools();
        verify(fsClient, never()).listTools();
    }

    @Test
    void clientThrowingExceptionReturnsEmptyTools() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "browser"));

        McpSyncClient mockClient = mock(McpSyncClient.class);
        when(mockClient.getClientInfo())
                .thenReturn(new McpSchema.Implementation("spring-ai-mcp-client - browser", "1.0"));
        when(mockClient.listTools()).thenThrow(new RuntimeException("Connection refused"));

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of(mockClient));

        assertTrue(result.containsKey("web_scraper"));
        // Lazy: exception deferred until first access
        assertTrue(result.get("web_scraper").isEmpty());
    }

    @Test
    void lazyDiscoveryNotCalledDuringBuild() {
        McpBindingProperties props = new McpBindingProperties();
        props.setEnabled(true);
        props.setBindings(Map.of("web_scraper", "browser"));

        McpSyncClient mockClient = mock(McpSyncClient.class);
        when(mockClient.getClientInfo())
                .thenReturn(new McpSchema.Implementation("spring-ai-mcp-client - browser", "1.0"));
        when(mockClient.listTools()).thenThrow(new RuntimeException("should not be called"));

        McpToolConfig config = new McpToolConfig(props);
        Map<String, List<ToolCallback>> result = config.buildAgentMcpTools(List.of(mockClient));

        // buildAgentMcpTools must NOT call listTools
        verify(mockClient, never()).listTools();
        // Only on access does discovery happen (and gracefully handle the exception)
        assertEquals(0, result.get("web_scraper").size());
    }
}
