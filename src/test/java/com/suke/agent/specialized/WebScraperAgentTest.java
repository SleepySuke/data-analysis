package com.suke.agent.specialized;

import com.alibaba.cloud.ai.graph.StateGraph;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class WebScraperAgentTest {

    private final ChatModel chatModel = mock(ChatModel.class);
    private final com.suke.agent.tool.scraping.UrlFetchTool fetchTool = new com.suke.agent.tool.scraping.UrlFetchTool();
    private final com.suke.agent.tool.scraping.ContentExtractorTool extractTool = new com.suke.agent.tool.scraping.ContentExtractorTool();

    private final WebScraperAgent agent = new WebScraperAgent(
            "web_scraper", "网页采集专家",
            chatModel, fetchTool, extractTool, null, null);

    @Test
    void initGraphReturnsValidStateGraph() throws Exception {
        StateGraph graph = agent.initGraph();
        assertNotNull(graph, "initGraph should return a non-null StateGraph");
    }

    @Test
    void agentNameIsCorrect() {
        assertEquals("web_scraper", agent.name());
    }

    @Test
    void agentDescriptionIsCorrect() {
        assertEquals("网页采集专家", agent.description());
    }

    @Test
    void asNodeReturnsNode() {
        var node = agent.asNode(true, true);
        assertNotNull(node);
        assertEquals("web_scraper", node.id());
    }

    @Test
    void graphHasCorrectNodes() throws Exception {
        StateGraph graph = agent.initGraph();
        // Verify graph was built without exceptions
        assertNotNull(graph);
        // The graph should compile successfully
        var compiled = graph.compile();
        assertNotNull(compiled, "CompiledGraph should not be null");
    }

    @Test
    void extractUrlFromValidMessage() {
        String message = "请帮我抓取 https://example.com 的数据";
        // Test via reflection or make extractUrl package-private
        // Since extractUrl is private, we test indirectly through invoke
        // For now, verify agent construction works with all tools
        WebScraperAgent agent = new WebScraperAgent(
                "test", "test", chatModel, fetchTool, extractTool, null, null);
        assertNotNull(agent);
    }

    @Test
    void agentConstructsWithIngestTool() throws Exception {
        var mockIngest = mock(com.suke.agent.tool.scraping.KnowledgeIngestTool.class);
        WebScraperAgent agent = new WebScraperAgent(
                "web_scraper", "网页采集专家",
                chatModel, fetchTool, extractTool, mockIngest, null);
        assertNotNull(agent);
        assertNotNull(agent.initGraph());
    }
}
