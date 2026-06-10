/**
 * @author 自然醒
 */
package com.suke.mcp.browser.tool;

import com.suke.mcp.browser.service.BrowserService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BrowserTool {
    private final BrowserService browserService;

    public BrowserTool(BrowserService browserService) {
        this.browserService = browserService;
    }

    @Tool(description = "Navigate to a URL and extract page content. Optionally wait for dynamic content and use CSS selector to extract specific elements.")
    public Map<String, Object> navigateAndScrape(
            @ToolParam(description = "The URL to navigate to.") String url,
            @ToolParam(description = "CSS selector to extract specific content. If null, extracts full page text.", required = false) String selector,
            @ToolParam(description = "Seconds to wait for dynamic content to load.", required = false) Integer waitSeconds) {
        return browserService.navigateAndScrape(url, selector, waitSeconds);
    }

    @Tool(description = "Extract all links from a web page. Optionally filter by URL pattern.")
    public Map<String, Object> extractLinks(
            @ToolParam(description = "The URL to extract links from.") String url,
            @ToolParam(description = "Regex pattern to filter links by URL.", required = false) String pattern) {
        return browserService.extractLinks(url, pattern);
    }

    @Tool(description = "Take a screenshot of a web page or a specific element.")
    public Map<String, Object> screenshot(
            @ToolParam(description = "The URL to screenshot.") String url,
            @ToolParam(description = "Whether to capture the full page. Default false.", required = false) Boolean fullPage,
            @ToolParam(description = "CSS selector to screenshot a specific element.", required = false) String selector) {
        return browserService.screenshot(url, fullPage, selector);
    }
}
