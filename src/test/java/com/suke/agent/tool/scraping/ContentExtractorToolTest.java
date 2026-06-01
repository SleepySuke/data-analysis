package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentExtractorToolTest {

    private final ContentExtractorTool tool = new ContentExtractorTool();

    @Test
    void extractArticleReturnsTitleAndContent() {
        String html = """
            <html><head><title>Test Page</title></head>
            <body><article><p>This is article content.</p><p>Second paragraph.</p></article></body></html>
            """;
        String result = tool.extractContent(html, "article");
        JSONObject json = JSON.parseObject(result);

        assertEquals("Test Page", json.getString("title"));
        assertTrue(json.getString("content").contains("This is article content"));
        assertTrue(json.getString("content").contains("Second paragraph"));
    }

    @Test
    void extractTableReturnsStructuredData() {
        String html = """
            <html><body>
            <table>
              <tr><th>Name</th><th>Age</th></tr>
              <tr><td>Alice</td><td>25</td></tr>
              <tr><td>Bob</td><td>30</td></tr>
            </table>
            </body></html>
            """;
        String result = tool.extractContent(html, "table");
        JSONObject json = JSON.parseObject(result);

        JSONArray tables = json.getJSONArray("tables");
        assertEquals(1, tables.size());

        JSONObject table = tables.getJSONObject(0);
        JSONArray headers = table.getJSONArray("headers");
        assertEquals(2, headers.size());
        assertEquals("Name", headers.getString(0));
        assertEquals("Age", headers.getString(1));

        JSONArray rows = table.getJSONArray("rows");
        assertEquals(2, rows.size());
        assertEquals("Alice", rows.getJSONArray(0).getString(0));
    }

    @Test
    void extractAllReturnsBothContentAndTables() {
        String html = """
            <html><head><title>Full Page</title></head>
            <body><article><p>Content here.</p></article>
            <table><tr><th>Col</th></tr><tr><td>Val</td></tr></table>
            </body></html>
            """;
        String result = tool.extractContent(html, "all");
        JSONObject json = JSON.parseObject(result);

        assertEquals("Full Page", json.getString("title"));
        assertNotNull(json.getString("content"));
        assertNotNull(json.getJSONArray("tables"));
        assertEquals(1, json.getJSONArray("tables").size());
    }

    @Test
    void extractFromEmptyHtmlReturnsError() {
        String result = tool.extractContent("", "all");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void extractFromNullHtmlReturnsError() {
        String result = tool.extractContent(null, "all");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void extractMultipleTables() {
        String html = """
            <html><body>
            <table><tr><th>A</th></tr><tr><td>1</td></tr></table>
            <table><tr><th>B</th></tr><tr><td>2</td></tr></table>
            </body></html>
            """;
        String result = tool.extractContent(html, "table");
        JSONObject json = JSON.parseObject(result);
        assertEquals(2, json.getJSONArray("tables").size());
    }

    @Test
    void extractContentWithNullExtractType() {
        // null extractType defaults to "all" per source code
        String html = """
            <html><head><title>Default Test</title></head>
            <body><article><p>Article text.</p></article>
            <table><tr><th>Col</th></tr><tr><td>Val</td></tr></table>
            </body></html>
            """;
        String result = tool.extractContent(html, null);
        JSONObject json = JSON.parseObject(result);

        // Should behave as "all" — both content and tables present
        assertTrue(json.getBooleanValue("success"));
        assertEquals("Default Test", json.getString("title"));
        assertNotNull(json.getString("content"), "Expected content field with null extractType (defaults to all)");
        assertNotNull(json.getJSONArray("tables"), "Expected tables field with null extractType (defaults to all)");
        assertEquals(1, json.getJSONArray("tables").size());
    }
}
