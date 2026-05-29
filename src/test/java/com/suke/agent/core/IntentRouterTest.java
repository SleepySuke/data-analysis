package com.suke.agent.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IntentRouterTest {

    private AgentRegistry registry;
    private ChatClient chatClient;
    private CallResponseSpec callResponse;
    private IntentRouter router;

    @BeforeEach
    void setUp() {
        registry = new AgentRegistry();
        registry.register(AgentDescriptor.builder()
                .name("data_analyst").prompt("test").build());
        registry.register(AgentDescriptor.builder()
                .name("web_scraper").prompt("test").build());

        chatClient = mock(ChatClient.class);
        var requestSpec = mock(ChatClientRequestSpec.class);
        callResponse = mock(CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(String.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponse);

        router = new IntentRouter(chatClient, registry);
    }

    @Nested
    @DisplayName("normalizeAgentName")
    class NormalizeTest {

        @Test
        @DisplayName("简单名称直接返回")
        void normalize_simpleName() {
            assertEquals("data_analyst", router.normalizeAgentName("data_analyst"));
        }

        @Test
        @DisplayName("去除 markdown 格式")
        void normalize_markdownFormat() {
            assertEquals("data_analyst", router.normalizeAgentName("**data_analyst**\n"));
        }

        @Test
        @DisplayName("去除序号前缀")
        void normalize_numberedPrefix() {
            assertEquals("data_analyst", router.normalizeAgentName("1. data_analyst"));
        }

        @Test
        @DisplayName("去除引号和空格")
        void normalize_quotesAndSpaces() {
            assertEquals("data_analyst", router.normalizeAgentName("`data_analyst` "));
        }

        @Test
        @DisplayName("null 返回 null")
        void normalize_null_returnsNull() {
            assertNull(router.normalizeAgentName(null));
        }

        @Test
        @DisplayName("空白字符串返回 null")
        void normalize_blank_returnsNull() {
            assertNull(router.normalizeAgentName("   "));
        }
    }

    @Nested
    @DisplayName("fallbackByKeywords")
    class KeywordFallbackTest {

        @Test
        @DisplayName("SQL 关键词 → sql_analyst")
        void fallback_sql() {
            assertEquals("sql_analyst", router.fallbackByKeywords("查询数据库中的用户表"));
        }

        @Test
        @DisplayName("清洗关键词 → data_cleaner")
        void fallback_cleaner() {
            assertEquals("data_cleaner", router.fallbackByKeywords("帮我清洗数据，处理缺失值"));
        }

        @Test
        @DisplayName("网页关键词 → web_scraper")
        void fallback_scraper() {
            assertEquals("web_scraper", router.fallbackByKeywords("抓取这个URL的网页内容"));
        }

        @Test
        @DisplayName("默认 → data_analyst")
        void fallback_default() {
            assertEquals("data_analyst", router.fallbackByKeywords("帮我分析一下数据"));
        }
    }

    @Nested
    @DisplayName("LLM 路由")
    class LlmRoutingTest {

        @Test
        @DisplayName("LLM 返回有效 Agent 名称 → 直接路由")
        void route_llmReturnsValidAgent() {
            when(callResponse.content()).thenReturn("data_analyst");

            String result = router.route("分析这个CSV数据");

            assertEquals("data_analyst", result);
        }

        @Test
        @DisplayName("LLM 返回带格式的名称 → normalize 后路由")
        void route_llmReturnsFormatted() {
            when(callResponse.content()).thenReturn("**data_analyst**\n");

            String result = router.route("分析数据");

            assertEquals("data_analyst", result);
        }

        @Test
        @DisplayName("LLM 返回未注册的 Agent → fallback 关键词")
        void route_llmReturnsUnknown_fallback() {
            when(callResponse.content()).thenReturn("nonexistent_agent");

            String result = router.route("帮我查询数据库");

            assertEquals("sql_analyst", result);
        }

        @Test
        @DisplayName("LLM 抛异常 → fallback 关键词")
        void route_llmThrows_fallback() {
            when(callResponse.content()).thenThrow(new RuntimeException("API error"));

            String result = router.route("帮我查询数据库");

            assertEquals("sql_analyst", result);
        }

        @Test
        @DisplayName("LLM 返回 null → fallback 关键词")
        void route_llmReturnsNull_fallback() {
            when(callResponse.content()).thenReturn(null);

            String result = router.route("帮我分析数据");

            assertEquals("data_analyst", result);
        }
    }
}
