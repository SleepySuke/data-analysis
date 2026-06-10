/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.config;

import com.suke.mcp.filesystem.service.PathSafetyChecker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PathSafetyConfig {
    @Bean
    public PathSafetyChecker pathSafetyChecker(FilesystemConfig config) {
        return new PathSafetyChecker(config.getRootDir());
    }
}
