package com.suke.agent.tool.scraping;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrlFetchToolTest {

    private final UrlFetchTool tool = new UrlFetchTool();

    @Test
    void fetchValidUrlReturnsContent() {
        String result = tool.fetchUrl("https://example.com", 30);
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getString("html").length() > 0);
        assertEquals("https://example.com", json.getString("url"));
    }

    @Test
    void fetchInvalidUrlReturnsError() {
        String result = tool.fetchUrl("not-a-url", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void fetchMalformedUrlReturnsError() {
        String result = tool.fetchUrl("ftp://invalid.scheme", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("HTTP") || json.getString("error").contains("https"));
    }

    @Test
    void fetchEmptyUrlReturnsError() {
        String result = tool.fetchUrl("", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void fetchNullUrlReturnsError() {
        String result = tool.fetchUrl(null, 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void fetchLocalhostBlockedBySSRF() {
        String result = tool.fetchUrl("http://127.0.0.1/admin", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("内网") || json.getString("error").contains("保留"));
    }

    @Test
    void fetchPrivateIpBlockedBySSRF() {
        String result = tool.fetchUrl("http://192.168.1.1/admin", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("内网") || json.getString("error").contains("保留"));
    }

    @Test
    void fetchLinkLocalBlockedBySSRF() {
        String result = tool.fetchUrl("http://169.254.169.254/metadata", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("内网") || json.getString("error").contains("保留"));
    }

    @Test
    void fetchMissingHostReturnsError() {
        String result = tool.fetchUrl("http:///path-only", 10);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }
}
