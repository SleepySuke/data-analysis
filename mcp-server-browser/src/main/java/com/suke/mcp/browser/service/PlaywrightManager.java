/**
 * @author 自然醒
 */
package com.suke.mcp.browser.service;

import com.microsoft.playwright.*;
import com.suke.mcp.browser.config.BrowserConfig;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightManager {
    private static final Logger log = LoggerFactory.getLogger(PlaywrightManager.class);
    private final BrowserConfig config;
    private Playwright playwright;
    private Browser browser;

    public PlaywrightManager(BrowserConfig config) {
        this.config = config;
    }

    public synchronized Browser getBrowser() {
        if (browser == null || !browser.isConnected()) {
            playwright = Playwright.create();
            browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(config.isHeadless()));
            log.info("Playwright browser launched (headless={})", config.isHeadless());
        }
        return browser;
    }

    public BrowserContext createContext() {
        return getBrowser().newContext(new Browser.NewContextOptions()
                .setViewportSize(1920, 1080));
    }

    @PreDestroy
    public synchronized void close() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
        log.info("Playwright resources released");
    }
}
