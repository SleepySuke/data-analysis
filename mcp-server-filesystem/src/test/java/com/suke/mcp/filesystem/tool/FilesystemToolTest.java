/**
 * @author 自然醒
 */
package com.suke.mcp.filesystem.tool;

import com.suke.mcp.filesystem.service.FileService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FilesystemToolTest {
    private FileService fileService;
    private FilesystemTool tool;

    @BeforeEach
    void setUp() {
        fileService = mock(FileService.class);
        tool = new FilesystemTool(fileService);
    }

    @Test
    void readFile_delegatesToService() {
        Map<String, Object> expected = Map.of("content", "hello", "lineCount", 1, "truncated", false);
        when(fileService.readFile("test.txt", null, null)).thenReturn(expected);
        assertEquals(expected, tool.readFile("test.txt", null, null));
    }

    @Test
    void writeFile_delegatesToService() {
        Map<String, Object> expected = Map.of("path", "out.txt", "bytesWritten", 5);
        when(fileService.writeFile("out.txt", "hello", null)).thenReturn(expected);
        assertEquals(expected, tool.writeFile("out.txt", "hello", null));
    }

    @Test
    void listDirectory_delegatesToService() {
        Map<String, Object> expected = Map.of("entries", java.util.List.of(), "total", 0);
        when(fileService.listDirectory(".", false)).thenReturn(expected);
        assertEquals(expected, tool.listDirectory(".", false));
    }

    @Test
    void fileInfo_delegatesToService() {
        Map<String, Object> expected = Map.of("name", "test.txt", "size", 100L, "isDirectory", false);
        when(fileService.fileInfo("test.txt")).thenReturn(expected);
        assertEquals(expected, tool.fileInfo("test.txt"));
    }
}
