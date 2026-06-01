/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description SQL执行工具，只允许SELECT查询，自动追加LIMIT
 */

package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class SqlExecutionTool {

    private static final int DEFAULT_LIMIT = 100;
    private static final Pattern FORBIDDEN_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|TRUNCATE|CREATE|EXEC|EXECUTE|GRANT|REVOKE" +
            "|INTO\\s+(OUTFILE|DUMPFILE)|LOAD\\s+DATA|LOAD_FILE|SLEEP|BENCHMARK|WAITFOR)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern COMMENT_PATTERN = Pattern.compile(
            "/\\*.*?\\*/|--.*?$|#[^\\n]*", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Pattern TRAILING_LIMIT = Pattern.compile(
            "\\bLIMIT\\s+\\d+\\s*;?\\s*$", Pattern.CASE_INSENSITIVE
    );

    private final JdbcTemplate jdbcTemplate;

    public SqlExecutionTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(description = "执行只读SQL查询，返回JSON结果。只允许SELECT，禁止写操作")
    public String executeSql(
            @ToolParam(description = "SQL查询语句，必须是SELECT") String sql) {

        if (sql == null || sql.isBlank()) {
            return CsvUtils.errorJson("SQL不能为空");
        }

        String trimmedSql = sql.trim();

        // Remove trailing semicolon
        if (trimmedSql.endsWith(";")) {
            trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
        }

        String upperSql = trimmedSql.toUpperCase();
        if (!upperSql.startsWith("SELECT")) {
            return CsvUtils.errorJson("只允许SELECT查询");
        }

        // Strip comments before keyword check
        String sqlNoComments = COMMENT_PATTERN.matcher(trimmedSql).replaceAll(" ");

        var forbiddenMatcher = FORBIDDEN_PATTERN.matcher(sqlNoComments);
        if (forbiddenMatcher.find()) {
            return CsvUtils.errorJson("SQL包含禁止的操作，只允许SELECT查询");
        }

        String finalSql = appendLimitIfNeeded(trimmedSql);

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(finalSql);

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("data", rows);
            result.put("rowCount", rows.size());
            result.put("sql", finalSql);
            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("查询执行失败");
        }
    }

    String appendLimitIfNeeded(String sql) {
        if (!TRAILING_LIMIT.matcher(sql).find()) {
            return sql + " LIMIT " + DEFAULT_LIMIT;
        }
        return sql;
    }

}
