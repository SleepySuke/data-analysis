/**
 * @author 自然醒
 */
package com.suke.mcp.database.tool;

import com.suke.mcp.database.service.QueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseToolTest {
    private QueryService queryService;
    private DatabaseTool databaseTool;

    @BeforeEach
    void setUp() {
        queryService = mock(QueryService.class);
        databaseTool = new DatabaseTool(queryService);
    }

    @Test
    void executeQuery_delegatesToService() {
        Map<String, Object> expected = Map.of("columns", List.of("id"), "rows", List.of(), "totalRows", 0, "truncated", false);
        when(queryService.executeQuery("SELECT 1", null)).thenReturn(expected);
        assertEquals(expected, databaseTool.executeQuery("SELECT 1", null));
    }

    @Test
    void executeQuery_withMaxRows() {
        Map<String, Object> expected = Map.of("columns", List.of("id"), "rows", List.of(), "totalRows", 0, "truncated", false);
        when(queryService.executeQuery("SELECT 1", 100)).thenReturn(expected);
        databaseTool.executeQuery("SELECT 1", 100);
        verify(queryService).executeQuery("SELECT 1", 100);
    }

    @Test
    void listTables_delegatesToService() {
        List<Map<String, Object>> expected = List.of(Map.of("name", "users"));
        when(queryService.listTables(null)).thenReturn(expected);
        assertEquals(expected, databaseTool.listTables(null));
    }

    @Test
    void describeTable_delegatesToService() {
        List<Map<String, Object>> expected = List.of(Map.of("name", "id", "type", "bigint"));
        when(queryService.describeTable("users")).thenReturn(expected);
        assertEquals(expected, databaseTool.describeTable("users"));
    }

    @Test
    void getSampleData_withDefaultLimit() {
        Map<String, Object> expected = Map.of("columns", List.of("id"), "rows", List.of(), "totalRows", 0, "truncated", false);
        when(queryService.executeQuery("SELECT * FROM users LIMIT 10", 10)).thenReturn(expected);
        assertEquals(expected, databaseTool.getSampleData("users", null));
    }

    @Test
    void getSampleData_withCustomLimit() {
        Map<String, Object> expected = Map.of("columns", List.of("id"), "rows", List.of(), "totalRows", 0, "truncated", false);
        when(queryService.executeQuery("SELECT * FROM users LIMIT 5", 5)).thenReturn(expected);
        assertEquals(expected, databaseTool.getSampleData("users", 5));
    }

    @Test
    void getSampleData_invalidTableName_throws() {
        assertThrows(IllegalArgumentException.class, () -> databaseTool.getSampleData("drop table users", 10));
        assertThrows(IllegalArgumentException.class, () -> databaseTool.getSampleData("users; drop table x", 10));
    }
}
