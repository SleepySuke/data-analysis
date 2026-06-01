package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SchemaIntrospectToolTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SchemaIntrospectTool tool = new SchemaIntrospectTool(jdbcTemplate);

    @Test
    void listTablesReturnsUserTables() {
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("data_analysis");
        when(jdbcTemplate.queryForList(anyString(), eq("data_analysis")))
                .thenReturn(List.of(
                        Map.of("TABLE_NAME", "chart"),
                        Map.of("TABLE_NAME", "user")
                ));

        String result = tool.introspectSchema("list_tables", null);
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        JSONArray tables = json.getJSONArray("tables");
        assertEquals(2, tables.size());
        assertEquals("chart", tables.getString(0));
    }

    @Test
    void describeTableReturnsColumnInfo() {
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("data_analysis");
        when(jdbcTemplate.queryForList(anyString(), eq("data_analysis"), eq("chart")))
                .thenReturn(List.of(
                        Map.of("COLUMN_NAME", "id", "DATA_TYPE", "bigint", "IS_NULLABLE", "NO", "COLUMN_KEY", "PRI"),
                        Map.of("COLUMN_NAME", "name", "DATA_TYPE", "varchar", "IS_NULLABLE", "YES", "COLUMN_KEY", "")
                ));

        String result = tool.introspectSchema("describe_table", "chart");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals("chart", json.getString("tableName"));
        JSONArray columns = json.getJSONArray("columns");
        assertEquals(2, columns.size());

        JSONObject idCol = columns.getJSONObject(0);
        assertEquals("id", idCol.getString("name"));
        assertEquals("bigint", idCol.getString("type"));
    }

    @Test
    void invalidOperationReturnsError() {
        String result = tool.introspectSchema("drop_table", "chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void describeTableWithNullNameReturnsError() {
        String result = tool.introspectSchema("describe_table", null);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void introspectSchemaWithNullOperation() {
        // null operation triggers: "操作类型不能为空"
        String result = tool.introspectSchema(null, null);
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void describeTableWithBlankTableName() {
        // Blank tableName triggers: "表名不能为空"
        String result = tool.introspectSchema("describe_table", "   ");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void describeTableWithNonExistentTable() {
        when(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .thenReturn("data_analysis");
        // Table does not exist -> query returns empty list -> error response
        when(jdbcTemplate.queryForList(anyString(), eq("data_analysis"), eq("nonexistent_table")))
                .thenReturn(List.of());

        String result = tool.introspectSchema("describe_table", "nonexistent_table");
        JSONObject json = JSON.parseObject(result);

        // Source code: if columns.isEmpty() -> errorJson("表不存在或无权限访问: ...")
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("nonexistent_table"));
    }
}
