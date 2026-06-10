/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description MCP工具配置，按Agent绑定MCP Server工具
 */
package com.suke.agent.config;

import io.modelcontextprotocol.client.McpSyncClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableConfigurationProperties(McpBindingProperties.class)
public class McpToolConfig {

    private static final String CLIENT_NAME_SEPARATOR = " - ";

    private final McpBindingProperties bindingProps;

    public McpToolConfig(McpBindingProperties bindingProps) {
        this.bindingProps = bindingProps;
    }

    @Bean
    public Map<String, List<ToolCallback>> agentMcpTools(
            @Nullable List<McpSyncClient> mcpClients) {
        return buildAgentMcpTools(mcpClients != null ? mcpClients : List.of());
    }

    /**
     * Package-private for testing. Discovers MCP tools for each agent binding.
     * <p>
     * Uses lazy discovery: the returned lists are backed by
     * {@link LazyMcpToolList} which defers the actual listTools() call
     * until first access, preventing Spring startup from blocking on
     * unreachable MCP Servers.
     *
     * @param mcpClients available MCP sync clients from auto-configuration
     * @return map of agentName to list of ToolCallbacks
     */
    Map<String, List<ToolCallback>> buildAgentMcpTools(
            List<McpSyncClient> mcpClients) {
        Map<String, List<ToolCallback>> result = new HashMap<>();

        if (!bindingProps.isEnabled()) {
            log.info("MCP工具绑定已禁用");
            return result;
        }

        Map<String, McpSyncClient> clientMap = buildClientMap(mcpClients);

        for (Map.Entry<String, String> entry : bindingProps.getBindings().entrySet()) {
            String agentName = entry.getKey();
            String serverName = entry.getValue();

            McpSyncClient client = clientMap.get(serverName);
            if (client != null) {
                result.put(agentName, new LazyMcpToolList(client, serverName, agentName));
                log.info("Agent '{}' 已绑定 MCP Server '{}' (工具将在首次使用时发现)",
                        agentName, serverName);
            } else {
                result.put(agentName, List.of());
                log.warn("MCP Server '{}' 未找到可用连接, Agent '{}' 跳过 MCP 工具",
                        serverName, agentName);
            }
        }

        return result;
    }

    private Map<String, McpSyncClient> buildClientMap(List<McpSyncClient> mcpClients) {
        Map<String, McpSyncClient> clientMap = new HashMap<>();
        for (McpSyncClient client : mcpClients) {
            try {
                String connectionName = extractConnectionName(client);
                if (connectionName != null) {
                    clientMap.put(connectionName, client);
                    log.debug("发现 MCP Client: {}", connectionName);
                }
            } catch (Exception e) {
                log.warn("读取 MCP Client 信息失败: {}", e.getMessage());
            }
        }
        return clientMap;
    }

    /**
     * Extracts the server connection name from the McpSyncClient.
     * <p>
     * Auto-configuration sets clientInfo.name to "spring-ai-mcp-client - {connectionName}".
     * We extract the connectionName portion after the separator.
     */
    @Nullable
    String extractConnectionName(McpSyncClient client) {
        try {
            var clientInfo = client.getClientInfo();
            if (clientInfo == null) {
                return null;
            }
            String name = clientInfo.name();
            if (name == null || name.isEmpty()) {
                return null;
            }
            int separatorIdx = name.indexOf(CLIENT_NAME_SEPARATOR);
            if (separatorIdx >= 0) {
                return name.substring(separatorIdx + CLIENT_NAME_SEPARATOR.length());
            }
            // Fallback: name format doesn't match expected convention
            log.warn("MCP Client name '{}' 不符合预期格式 '{}{}{connectionName}', "
                    + "binding 匹配可能失败", name, "spring-ai-mcp-client", CLIENT_NAME_SEPARATOR);
            return name;
        } catch (Exception e) {
            log.debug("无法提取 MCP Client 连接名: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Lazily discovers MCP tools on first access, avoiding blocking
     * Spring startup with synchronous network calls.
     */
    static class LazyMcpToolList extends java.util.AbstractList<ToolCallback> {

        private final McpSyncClient client;
        private final String serverName;
        private final String agentName;
        private volatile List<ToolCallback> delegate;

        LazyMcpToolList(McpSyncClient client, String serverName, String agentName) {
            this.client = client;
            this.serverName = serverName;
            this.agentName = agentName;
        }

        private List<ToolCallback> resolve() {
            if (delegate == null) {
                synchronized (this) {
                    if (delegate == null) {
                        delegate = discoverTools();
                    }
                }
            }
            return delegate;
        }

        private List<ToolCallback> discoverTools() {
            try {
                SyncMcpToolCallbackProvider provider = SyncMcpToolCallbackProvider.builder()
                        .mcpClients(client)
                        .build();
                ToolCallback[] callbacks = provider.getToolCallbacks();
                log.info("Agent '{}' 从 MCP Server '{}' 发现 {} 个工具",
                        agentName, serverName, callbacks.length);
                return List.of(callbacks);
            } catch (Exception e) {
                log.warn("MCP Server '{}' 工具发现失败, Agent '{}' 将只使用内置工具: {}",
                        serverName, agentName, e.getMessage());
                return List.of();
            }
        }

        @Override
        public ToolCallback get(int index) {
            return resolve().get(index);
        }

        @Override
        public int size() {
            return resolve().size();
        }
    }
}
