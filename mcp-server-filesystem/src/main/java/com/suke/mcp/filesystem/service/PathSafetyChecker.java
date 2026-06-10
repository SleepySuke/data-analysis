/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.service;

import java.nio.file.Path;

public class PathSafetyChecker {
    private final Path rootDir;

    public PathSafetyChecker(String rootDir) {
        this.rootDir = Path.of(rootDir).toAbsolutePath().normalize();
    }

    public boolean isSafe(String filePath) {
        if (filePath == null) return false;
        try {
            Path resolved = resolveInternal(filePath);
            return resolved.startsWith(rootDir);
        } catch (Exception e) {
            return false;
        }
    }

    public Path resolve(String relativePath) {
        Path resolved = resolveInternal(relativePath);
        if (!resolved.startsWith(rootDir)) {
            throw new SecurityException("Path traversal detected: " + relativePath);
        }
        return resolved;
    }

    private Path resolveInternal(String relativePath) {
        return rootDir.resolve(relativePath).toAbsolutePath().normalize();
    }
}
