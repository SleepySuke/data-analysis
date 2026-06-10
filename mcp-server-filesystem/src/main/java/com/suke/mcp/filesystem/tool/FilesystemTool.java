/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.tool;

import com.suke.mcp.filesystem.service.FileService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FilesystemTool {
    private final FileService fileService;

    public FilesystemTool(FileService fileService) {
        this.fileService = fileService;
    }

    @Tool(description = "Read the contents of a text file. Supports encoding and line limit options.")
    public Map<String, Object> readFile(
            @ToolParam(description = "Path to the file relative to workspace root.") String path,
            @ToolParam(description = "File encoding. Default UTF-8.", required = false) String encoding,
            @ToolParam(description = "Maximum number of lines to read.", required = false) Integer maxLines) {
        return fileService.readFile(path, encoding, maxLines);
    }

    @Tool(description = "Write content to a file. Creates parent directories if needed.")
    public Map<String, Object> writeFile(
            @ToolParam(description = "Path to the file relative to workspace root.") String path,
            @ToolParam(description = "Content to write.") String content,
            @ToolParam(description = "Whether to create parent directories. Default false.", required = false) Boolean createDirs) {
        return fileService.writeFile(path, content, createDirs);
    }

    @Tool(description = "List contents of a directory.")
    public Map<String, Object> listDirectory(
            @ToolParam(description = "Path to the directory relative to workspace root.") String path,
            @ToolParam(description = "Whether to list recursively. Default false.", required = false) Boolean recursive) {
        return fileService.listDirectory(path, recursive);
    }

    @Tool(description = "Get metadata about a file or directory.")
    public Map<String, Object> fileInfo(
            @ToolParam(description = "Path to the file or directory.") String path) {
        return fileService.fileInfo(path);
    }
}
