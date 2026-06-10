/**
 * @author 自然醒
 */
package com.suke.mcp.database.service;

import com.suke.mcp.common.exception.ToolExecutionException;
import com.suke.mcp.database.config.DatabaseConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class QueryService {
    private static final Logger log = LoggerFactory.getLogger(QueryService.class);
    private final JdbcTemplate jdbc;
    private final SqlSafetyChecker safetyChecker;
    private final DatabaseConfig config;

    public QueryService(JdbcTemplate jdbc, SqlSafetyChecker safetyChecker, DatabaseConfig config) {
        this.jdbc = jdbc;
        this.safetyChecker = safetyChecker;
        this.config = config;
    }

    public Map<String, Object> executeQuery(String sql, Integer maxRows) {
        int limit = maxRows != null ? Math.min(maxRows, config.getMaxRows()) : config.getMaxRows();
        if (!safetyChecker.isSafe(sql)) {
            throw new ToolExecutionException("execute_query", "Unsafe SQL rejected: only SELECT/EXPLAIN/SHOW/DESCRIBE allowed");
        }
        try {
            jdbc.setQueryTimeout(config.getQueryTimeoutSeconds());
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            boolean truncated = rows.size() > limit;
            List<Map<String, Object>> limited = truncated ? rows.subList(0, limit) : rows;
            return Map.of(
                    "columns", limited.isEmpty() ? List.of() : new ArrayList<>(limited.get(0).keySet()),
                    "rows", limited,
                    "totalRows", rows.size(),
                    "truncated", truncated
            );
        } catch (ToolExecutionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Query execution failed: {}", e.getMessage());
            throw new ToolExecutionException("execute_query", "Query failed: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> listTables(String schema) {
        String sql = schema != null
                ? "SELECT TABLE_NAME as name, TABLE_SCHEMA as schema_name, TABLE_TYPE as type FROM information_schema.TABLES WHERE TABLE_SCHEMA = ?"
                : "SELECT TABLE_NAME as name, TABLE_SCHEMA as schema_name, TABLE_TYPE as type FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()";
        return schema != null ? jdbc.queryForList(sql, schema) : jdbc.queryForList(sql);
    }

    public List<Map<String, Object>> describeTable(String tableName) {
        String sql = "SELECT COLUMN_NAME as name, COLUMN_TYPE as type, IS_NULLABLE as nullable, COLUMN_KEY as `key`, COLUMN_COMMENT as comment FROM information_schema.COLUMNS WHERE TABLE_NAME = ? AND TABLE_SCHEMA = DATABASE() ORDER BY ORDINAL_POSITION";
        return jdbc.queryForList(sql, tableName);
    }
}
