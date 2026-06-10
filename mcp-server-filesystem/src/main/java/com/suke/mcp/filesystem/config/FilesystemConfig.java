/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.filesystem")
public class FilesystemConfig {
    private String rootDir = "./workspace";
    private long maxFileSize = 10485760; // 10MB
    private int maxLines = 10000;

    public String getRootDir() { return rootDir; }
    public void setRootDir(String rootDir) { this.rootDir = rootDir; }
    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }
    public int getMaxLines() { return maxLines; }
    public void setMaxLines(int maxLines) { this.maxLines = maxLines; }
}
