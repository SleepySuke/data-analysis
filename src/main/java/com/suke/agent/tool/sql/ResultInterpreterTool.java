/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description SQL结果解读工具，将查询结果转为结构化摘要
 */

package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

@Component
public class ResultInterpreterTool {

    @Tool(description = "将SQL查询结果解读为结构化摘要，包含行数、列名、统计信息")
    public String interpretResult(
            @ToolParam(description = "SQL查询结果的JSON字符串") String queryResult,
            @ToolParam(description = "用户的原始问题") String userQuestion) {

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("userQuestion", userQuestion != null ? userQuestion : "");

        if (queryResult == null || queryResult.isBlank()) {
            result.put("rowCount", 0);
            result.put("columns", new JSONArray());
            result.put("summary", "查询结果为空");
            return result.toJSONString();
        }

        try {
            JSONArray rows = JSON.parseArray(queryResult);
            if (rows == null || rows.isEmpty()) {
                result.put("rowCount", 0);
                result.put("columns", new JSONArray());
                result.put("summary", "查询返回0行数据，未找到匹配记录");
                return result.toJSONString();
            }

            JSONObject firstRow = rows.getJSONObject(0);
            Set<String> columnNames = new LinkedHashSet<>(firstRow.keySet());

            JSONArray colArr = new JSONArray();
            colArr.addAll(columnNames);

            result.put("rowCount", rows.size());
            result.put("columns", colArr);

            StringBuilder summary = new StringBuilder();
            summary.append("查询返回 ").append(rows.size()).append(" 行数据，");
            summary.append("包含 ").append(columnNames.size()).append(" 个字段：");
            summary.append(String.join("、", columnNames)).append("。");

            if (rows.size() <= 5) {
                summary.append(" 数据量较少，可以逐行分析。");
            } else {
                summary.append(" 数据量较大，建议关注关键统计指标。");
            }

            result.put("summary", summary.toString());
            return result.toJSONString();

        } catch (Exception e) {
            result.put("rowCount", 0);
            result.put("columns", new JSONArray());
            result.put("summary", "无法解析查询结果: " + e.getMessage());
            return result.toJSONString();
        }
    }
}
