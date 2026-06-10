/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 数据库Schema查询工具，查询表结构信息
 */

package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class SchemaIntrospectTool {

    private final JdbcTemplate jdbcTemplate;
    private final String cachedDatabaseName;

    public SchemaIntrospectTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        String dbName = null;
        try {
            dbName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.warn("Failed to detect database name at startup, queries may fail: {}", e.getMessage());
        }
        this.cachedDatabaseName = dbName != null ? dbName : "";
    }

    @Tool(description = "查询数据库表结构。支持list_tables列出所有表，describe_table查看表字段详情")
    public String introspectSchema(
            @ToolParam(description = "操作：list_tables 或 describe_table") String operation,
            @ToolParam(description = "表名，describe_table 时必填") String tableName) {

        if (operation == null || operation.isBlank()) {
            return CsvUtils.errorJson("操作类型不能为空");
        }

        return switch (operation.toLowerCase()) {
            case "list_tables" -> listTables();
            case "describe_table" -> describeTable(tableName);
            default -> CsvUtils.errorJson("不支持的操作: " + operation + "，支持: list_tables, describe_table");
        };
    }

    private String listTables() {
        String dbName = getDatabaseName();
        List<Map<String, Object>> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_TYPE = 'BASE TABLE'",
                dbName
        );

        JSONObject result = new JSONObject();
        result.put("success", true);
        JSONArray tableArr = new JSONArray();
        for (Map<String, Object> row : tables) {
            tableArr.add(row.get("TABLE_NAME"));
        }
        result.put("tables", tableArr);
        result.put("count", tableArr.size());
        return result.toJSONString();
    }

    private String describeTable(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return CsvUtils.errorJson("表名不能为空");
        }

        String dbName = getDatabaseName();
        List<Map<String, Object>> columns = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE, COLUMN_COMMENT, COLUMN_KEY " +
                        "FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ? " +
                        "ORDER BY ORDINAL_POSITION",
                dbName, tableName
        );

        if (columns.isEmpty()) {
            return CsvUtils.errorJson("表不存在或无权限访问: " + tableName);
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("tableName", tableName);

        JSONArray columnsArr = new JSONArray();
        for (Map<String, Object> col : columns) {
            JSONObject colObj = new JSONObject();
            colObj.put("name", col.get("COLUMN_NAME"));
            colObj.put("type", col.get("DATA_TYPE"));
            colObj.put("nullable", "YES".equals(col.get("IS_NULLABLE")));
            colObj.put("comment", col.get("COLUMN_COMMENT"));
            colObj.put("key", col.get("COLUMN_KEY"));
            columnsArr.add(colObj);
        }
        result.put("columns", columnsArr);
        return result.toJSONString();
    }

    private String getDatabaseName() {
        if (cachedDatabaseName != null && !cachedDatabaseName.isEmpty()) {
            return cachedDatabaseName;
        }
        try {
            return jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        } catch (Exception e) {
            log.error("Unable to determine database name: {}", e.getMessage());
            return "";
        }
    }

}
