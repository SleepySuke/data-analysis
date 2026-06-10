/**
 * @author 自然醒
 */
package com.suke.mcp.browser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.browser")
public class BrowserConfig {
    private boolean headless = true;
    private int defaultTimeoutSeconds = 30;
    private long maxContentLength = 1048576; // 1MB
    private int maxWaitSeconds = 60;

    public boolean isHeadless() { return headless; }
    public void setHeadless(boolean headless) { this.headless = headless; }
    public int getDefaultTimeoutSeconds() { return defaultTimeoutSeconds; }
    public void setDefaultTimeoutSeconds(int defaultTimeoutSeconds) { this.defaultTimeoutSeconds = defaultTimeoutSeconds; }
    public long getMaxContentLength() { return maxContentLength; }
    public void setMaxContentLength(long maxContentLength) { this.maxContentLength = maxContentLength; }
    public int getMaxWaitSeconds() { return maxWaitSeconds; }
    public void setMaxWaitSeconds(int maxWaitSeconds) { this.maxWaitSeconds = maxWaitSeconds; }
}
