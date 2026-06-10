/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-06-07
 * @description MCP绑定配置测试
 */
package com.suke.agent.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class McpBindingPropertiesTest {

    @Test
    void defaultBindingsIsEmpty() {
        McpBindingProperties props = new McpBindingProperties();
        assertTrue(props.getBindings().isEmpty());
    }

    @Test
    void bindingsMapCorrect() {
        McpBindingProperties props = new McpBindingProperties();
        props.setBindings(Map.of(
                "web_scraper", "browser",
                "data_cleaner", "filesystem",
                "sql_analyst", "database"
        ));
        assertEquals("browser", props.getBindings().get("web_scraper"));
        assertEquals("filesystem", props.getBindings().get("data_cleaner"));
        assertEquals("database", props.getBindings().get("sql_analyst"));
    }

    @Test
    void getServerForAgentReturnsCorrectServer() {
        McpBindingProperties props = new McpBindingProperties();
        props.setBindings(Map.of("web_scraper", "browser"));

        assertEquals("browser", props.getServerForAgent("web_scraper"));
    }

    @Test
    void getServerForAgentReturnsNullForUnknownAgent() {
        McpBindingProperties props = new McpBindingProperties();
        assertNull(props.getServerForAgent("data_analyst"));
    }

    @Test
    void isEnabledDefaultsToFalse() {
        McpBindingProperties props = new McpBindingProperties();
        assertFalse(props.isEnabled());
    }
}
