/**
 * @author 自然醒
 */
package com.suke.mcp.database.tool;

import com.suke.mcp.database.service.QueryService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class DatabaseTool {
    private final QueryService queryService;

    public DatabaseTool(QueryService queryService) {
        this.queryService = queryService;
    }

    @Tool(description = "Execute a read-only SQL query (SELECT/EXPLAIN/SHOW/DESCRIBE) against the database. Returns columns, rows, and metadata.")
    public Map<String, Object> executeQuery(
            @ToolParam(description = "The SQL query to execute. Only SELECT, EXPLAIN, SHOW, and DESCRIBE statements are allowed.") String sql,
            @ToolParam(description = "Maximum number of rows to return. Default 1000.", required = false) Integer maxRows) {
        return queryService.executeQuery(sql, maxRows);
    }

    @Tool(description = "List all tables in the database or a specific schema.")
    public List<Map<String, Object>> listTables(
            @ToolParam(description = "Schema name to filter by. If null, uses current database.", required = false) String schema) {
        return queryService.listTables(schema);
    }

    @Tool(description = "Get column definitions for a specific table.")
    public List<Map<String, Object>> describeTable(
            @ToolParam(description = "Name of the table to describe.") String tableName) {
        return queryService.describeTable(tableName);
    }

    @Tool(description = "Get sample rows from a table for quick preview.")
    public Map<String, Object> getSampleData(
            @ToolParam(description = "Name of the table.") String tableName,
            @ToolParam(description = "Number of rows to return. Default 10.", required = false) Integer limit) {
        int lim = limit != null ? limit : 10;
        String sql = "SELECT * FROM " + sanitizeIdentifier(tableName) + " LIMIT " + lim;
        return queryService.executeQuery(sql, lim);
    }

    private String sanitizeIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid table name: " + identifier);
        }
        return identifier;
    }
}
