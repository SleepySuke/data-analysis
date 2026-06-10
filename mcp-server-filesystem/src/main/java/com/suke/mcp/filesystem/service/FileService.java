/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.service;

import com.suke.mcp.common.exception.ToolExecutionException;
import com.suke.mcp.filesystem.config.FilesystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Stream;

@Service
public class FileService {
    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private final PathSafetyChecker pathChecker;
    private final FilesystemConfig config;

    public FileService(PathSafetyChecker pathChecker, FilesystemConfig config) {
        this.pathChecker = pathChecker;
        this.config = config;
    }

    public Map<String, Object> readFile(String relativePath, String encoding, Integer maxLines) {
        Path path = pathChecker.resolve(relativePath);
        if (!Files.exists(path)) {
            throw new ToolExecutionException("read_file", "File not found: " + relativePath);
        }
        try {
            if (Files.size(path) > config.getMaxFileSize()) {
                throw new ToolExecutionException("read_file", "File too large: max " + config.getMaxFileSize() + " bytes");
            }
        } catch (IOException e) {
            throw new ToolExecutionException("read_file", "Failed to check file size: " + e.getMessage(), e);
        }
        Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
        int limit = maxLines != null ? Math.min(maxLines, config.getMaxLines()) : config.getMaxLines();
        try (Stream<String> lines = Files.lines(path, charset)) {
            List<String> content = lines.limit(limit + 1).toList();
            boolean truncated = content.size() > limit;
            return Map.of(
                    "content", String.join("\n", truncated ? content.subList(0, limit) : content),
                    "lineCount", truncated ? limit : content.size(),
                    "truncated", truncated
            );
        } catch (IOException e) {
            throw new ToolExecutionException("read_file", "Failed to read file: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> writeFile(String relativePath, String content, Boolean createDirs) {
        Path path = pathChecker.resolve(relativePath);
        if (createDirs != null && createDirs) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new ToolExecutionException("write_file", "Failed to create directories: " + e.getMessage(), e);
            }
        }
        try {
            Files.writeString(path, content);
            return Map.of("path", relativePath, "bytesWritten", content.getBytes().length);
        } catch (IOException e) {
            throw new ToolExecutionException("write_file", "Failed to write file: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> listDirectory(String relativePath, Boolean recursive) {
        Path dir = pathChecker.resolve(relativePath);
        if (!Files.isDirectory(dir)) {
            throw new ToolExecutionException("list_directory", "Not a directory: " + relativePath);
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try {
            int maxDepth = (recursive != null && recursive) ? Integer.MAX_VALUE : 1;
            Files.walkFileTree(dir, Set.of(), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String name = dir.relativize(file).toString();
                    entries.add(Map.of("name", (Object) name, "type", (Object) "file", "size", (Object) attrs.size()));
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                    if (!d.equals(dir)) {
                        String name = dir.relativize(d).toString();
                        entries.add(Map.of("name", (Object) name, "type", (Object) "directory", "size", (Object) 0L));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ToolExecutionException("list_directory", "Failed to list directory: " + e.getMessage(), e);
        }
        return Map.of("entries", entries, "total", entries.size());
    }

    public Map<String, Object> fileInfo(String relativePath) {
        Path path = pathChecker.resolve(relativePath);
        if (!Files.exists(path)) {
            throw new ToolExecutionException("file_info", "File not found: " + relativePath);
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
            return Map.of(
                    "name", path.getFileName().toString(),
                    "size", attrs.size(),
                    "isDirectory", attrs.isDirectory(),
                    "lastModified", attrs.lastModifiedTime().toString()
            );
        } catch (IOException e) {
            throw new ToolExecutionException("file_info", "Failed to read file info: " + e.getMessage(), e);
        }
    }
}
