/**
 * @author 自然醒
 */
package com.suke.mcp.browser.service;

import com.microsoft.playwright.*;
import com.suke.mcp.browser.config.BrowserConfig;
import com.suke.mcp.common.exception.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class BrowserService {
    private static final Logger log = LoggerFactory.getLogger(BrowserService.class);
    private static final Pattern URL_PATTERN = Pattern.compile("^https?://.*", Pattern.CASE_INSENSITIVE);
    private final PlaywrightManager manager;
    private final BrowserConfig config;

    public BrowserService(PlaywrightManager manager, BrowserConfig config) {
        this.manager = manager;
        this.config = config;
    }

    public Map<String, Object> navigateAndScrape(String url, String selector, Integer waitSeconds) {
        validateUrl(url);
        int wait = waitSeconds != null ? Math.min(waitSeconds, config.getMaxWaitSeconds()) : 0;

        try (BrowserContext ctx = manager.createContext()) {
            Page page = ctx.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(config.getDefaultTimeoutSeconds() * 1000.0));

            if (wait > 0) {
                page.waitForTimeout(wait * 1000L);
            }

            String content;
            if (selector != null && !selector.isBlank()) {
                ElementHandle el = page.querySelector(selector);
                content = el != null ? el.innerText() : "";
            } else {
                content = page.innerText("body");
            }

            boolean truncated = content.length() > config.getMaxContentLength();
            if (truncated) {
                content = content.substring(0, (int) config.getMaxContentLength());
            }

            return Map.of(
                    "title", page.title(),
                    "content", content,
                    "url", page.url(),
                    "truncated", truncated
            );
        } catch (PlaywrightException e) {
            throw new ToolExecutionException("navigate_and_scrape", "Browser error: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> extractLinks(String url, String pattern) {
        validateUrl(url);
        try (BrowserContext ctx = manager.createContext()) {
            Page page = ctx.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(config.getDefaultTimeoutSeconds() * 1000.0));

            List<ElementHandle> anchors = page.querySelectorAll("a[href]");
            Pattern filter = pattern != null ? Pattern.compile(pattern, Pattern.CASE_INSENSITIVE) : null;

            List<Map<String, String>> links = anchors.stream()
                    .map(a -> Map.of(
                            "text", a.innerText() != null ? a.innerText() : "",
                            "href", a.getAttribute("href") != null ? a.getAttribute("href") : ""
                    ))
                    .filter(link -> filter == null || filter.matcher(link.get("href")).find())
                    .collect(Collectors.toList());

            return Map.of("links", links, "total", links.size());
        } catch (PlaywrightException e) {
            throw new ToolExecutionException("extract_links", "Browser error: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> screenshot(String url, Boolean fullPage, String selector) {
        validateUrl(url);
        try (BrowserContext ctx = manager.createContext()) {
            Page page = ctx.newPage();
            page.navigate(url, new Page.NavigateOptions().setTimeout(config.getDefaultTimeoutSeconds() * 1000.0));

            byte[] bytes;
            if (selector != null && !selector.isBlank()) {
                ElementHandle el = page.querySelector(selector);
                bytes = el != null ? el.screenshot() : new byte[0];
            } else {
                bytes = page.screenshot(new Page.ScreenshotOptions().setFullPage(fullPage != null && fullPage));
            }

            String base64 = Base64.getEncoder().encodeToString(bytes);
            return Map.of(
                    "imageBase64", base64,
                    "sizeBytes", bytes.length,
                    "url", page.url()
            );
        } catch (PlaywrightException e) {
            throw new ToolExecutionException("screenshot", "Browser error: " + e.getMessage(), e);
        }
    }

    private void validateUrl(String url) {
        if (url == null || !URL_PATTERN.matcher(url).matches()) {
            throw new ToolExecutionException("browser", "Invalid URL: must start with http:// or https://");
        }
    }
}
