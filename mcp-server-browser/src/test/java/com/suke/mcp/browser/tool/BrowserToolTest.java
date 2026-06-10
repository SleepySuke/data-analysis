/**
 * @author 自然醒
 */
package com.suke.mcp.browser.tool;

import com.suke.mcp.browser.service.BrowserService;
import com.suke.mcp.common.exception.ToolExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BrowserToolTest {
    private BrowserService browserService;
    private BrowserTool tool;

    @BeforeEach
    void setUp() {
        browserService = mock(BrowserService.class);
        tool = new BrowserTool(browserService);
    }

    @Test
    void navigateAndScrape_delegatesToService() {
        Map<String, Object> expected = Map.of("title", "Test", "content", "Hello", "url", "https://example.com", "truncated", false);
        when(browserService.navigateAndScrape("https://example.com", null, null)).thenReturn(expected);
        assertEquals(expected, tool.navigateAndScrape("https://example.com", null, null));
    }

    @Test
    void extractLinks_delegatesToService() {
        Map<String, Object> expected = Map.of("links", java.util.List.of(), "total", 0);
        when(browserService.extractLinks("https://example.com", null)).thenReturn(expected);
        assertEquals(expected, tool.extractLinks("https://example.com", null));
    }

    @Test
    void screenshot_delegatesToService() {
        Map<String, Object> expected = Map.of("imageBase64", "base64data", "sizeBytes", 1234, "url", "https://example.com");
        when(browserService.screenshot("https://example.com", null, null)).thenReturn(expected);
        assertEquals(expected, tool.screenshot("https://example.com", null, null));
    }

    @Test
    void invalidUrl_throwsException() {
        when(browserService.navigateAndScrape("not-a-url", null, null))
                .thenThrow(new ToolExecutionException("browser", "Invalid URL: must start with http:// or https://"));
        assertThrows(ToolExecutionException.class,
                () -> tool.navigateAndScrape("not-a-url", null, null));
    }
}
