/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description MCP Agent绑定配置属性
 */
package com.suke.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "agent.mcp")
public class McpBindingProperties {

    private boolean enabled = false;

    private Map<String, String> bindings = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Map<String, String> getBindings() {
        return bindings;
    }

    public void setBindings(Map<String, String> bindings) {
        this.bindings = bindings;
    }

    @Nullable
    public String getServerForAgent(String agentName) {
        return bindings.get(agentName);
    }
}
