package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SqlExecutionToolTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SqlExecutionTool tool = new SqlExecutionTool(jdbcTemplate);

    @Test
    void executeSelectReturnsResults() {
        when(jdbcTemplate.queryForList("SELECT * FROM chart LIMIT 100"))
                .thenReturn(List.of(
                        Map.of("id", 1, "name", "test_chart"),
                        Map.of("id", 2, "name", "test_chart2")
                ));

        String result = tool.executeSql("SELECT * FROM chart");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(2, json.getJSONArray("data").size());
    }

    @Test
    void executeSelectWithAutoLimit() {
        when(jdbcTemplate.queryForList("SELECT id, name FROM chart LIMIT 100"))
                .thenReturn(List.of());

        tool.executeSql("SELECT id, name FROM chart");
        verify(jdbcTemplate).queryForList("SELECT id, name FROM chart LIMIT 100");
    }

    @Test
    void executeSelectWithExistingLimit() {
        when(jdbcTemplate.queryForList("SELECT * FROM chart LIMIT 10"))
                .thenReturn(List.of());

        tool.executeSql("SELECT * FROM chart LIMIT 10");
        verify(jdbcTemplate).queryForList("SELECT * FROM chart LIMIT 10");
    }

    @Test
    void executeInsertRejected() {
        String result = tool.executeSql("INSERT INTO chart VALUES (1, 'test')");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertTrue(json.getString("error").contains("SELECT"));
    }

    @Test
    void executeUpdateRejected() {
        String result = tool.executeSql("UPDATE chart SET name='x'");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void executeDropRejected() {
        String result = tool.executeSql("DROP TABLE chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void executeDeleteRejected() {
        String result = tool.executeSql("DELETE FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void executeEmptySqlReturnsError() {
        String result = tool.executeSql("");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void executeNullSqlReturnsError() {
        String result = tool.executeSql(null);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void executeTruncateRejected() {
        String result = tool.executeSql("TRUNCATE TABLE chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void columnContainingDeleteKeywordNotBlocked() {
        when(jdbcTemplate.queryForList("SELECT order_delete_flag FROM chart LIMIT 100"))
                .thenReturn(List.of());

        String result = tool.executeSql("SELECT order_delete_flag FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"),
                "SELECT with column name containing 'delete' should not be blocked");
    }

    @Test
    void columnContainingUpdateKeywordNotBlocked() {
        when(jdbcTemplate.queryForList("SELECT last_update_time FROM chart LIMIT 100"))
                .thenReturn(List.of());

        String result = tool.executeSql("SELECT last_update_time FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"),
                "SELECT with column name containing 'update' should not be blocked");
    }

    @Test
    void subqueryWithDeleteKeywordBlocked() {
        String result = tool.executeSql("SELECT * FROM chart WHERE id IN (DELETE FROM other)");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"),
                "SQL with DELETE as standalone keyword should still be blocked");
    }

    @Test
    void selectIntoOutfileBlocked() {
        String result = tool.executeSql("SELECT * INTO OUTFILE '/tmp/data' FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void sleepBlocked() {
        String result = tool.executeSql("SELECT SLEEP(5)");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void commentBypassBlocked() {
        // Comment hides the keyword but real attack puts keyword after comment
        String result = tool.executeSql("SELECT * /*comment*/ FROM chart; DROP TABLE chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"),
                "SQL with forbidden keyword outside comment should be blocked");
    }

    @Test
    void pureCommentHidingKeywordAllowed() {
        when(jdbcTemplate.queryForList("SELECT   * FROM chart LIMIT 100"))
                .thenReturn(List.of());

        // DROP is only inside a comment, not executed — should be allowed
        String result = tool.executeSql("SELECT /*DROP TABLE*/ * FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"),
                "SQL with keyword only in comments should be allowed");
    }

    @Test
    void trailingSemicolonStrippedBeforeLimit() {
        when(jdbcTemplate.queryForList("SELECT * FROM chart LIMIT 100"))
                .thenReturn(List.of());

        tool.executeSql("SELECT * FROM chart;");
        verify(jdbcTemplate).queryForList("SELECT * FROM chart LIMIT 100");
    }

    @Test
    void limitInColumnNameStillAppendsLimit() {
        when(jdbcTemplate.queryForList(anyString())).thenReturn(List.of());

        tool.executeSql("SELECT unlimited_plan FROM chart");
        // Should still append LIMIT since "unlimited" doesn't match trailing LIMIT pattern
        verify(jdbcTemplate).queryForList(argThat(sql ->
                sql.endsWith("LIMIT 100") && !sql.contains("LIMIT 100 LIMIT")));
    }

    @Test
    void errorMessageDoesNotLeakInternalState() {
        when(jdbcTemplate.queryForList(anyString())).thenThrow(new RuntimeException("internal stack trace detail"));

        String result = tool.executeSql("SELECT * FROM chart");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
        assertFalse(json.getString("error").contains("internal stack trace"),
                "Error message should not leak internal details");
    }
}
