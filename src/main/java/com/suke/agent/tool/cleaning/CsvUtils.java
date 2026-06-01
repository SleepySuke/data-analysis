/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description CSV解析/输出/错误格式化工具类
 */

package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvUtils {

    private CsvUtils() {}

    /**
     * 生成标准错误JSON响应
     */
    public static String errorJson(String message) {
        return JSON.toJSONString(Map.of("success", false, "error", message));
    }

    /**
     * 生成包含自定义key-value的成功JSON响应
     */
    public static String successJson(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return JSON.toJSONString(map);
    }

    /**
     * 解析CSV行，正确处理引号内的逗号
     */
    public static String[] parseCsvLine(String line) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString());
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }
        result.add(current.toString());
        return result.toArray(new String[0]);
    }

    /**
     * 转义CSV字段，处理包含逗号、引号、换行的情况
     */
    public static String escapeField(String field) {
        if (field == null) return "";
        if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    /**
     * 将headers和rows输出为CSV字符串，自动转义特殊字符
     */
    public static String toCsv(String[] headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        String[] escapedHeaders = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            escapedHeaders[i] = escapeField(headers[i]);
        }
        sb.append(String.join(",", escapedHeaders)).append("\n");
        for (String[] row : rows) {
            String[] escaped = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                escaped[i] = escapeField(row[i]);
            }
            sb.append(String.join(",", escaped)).append("\n");
        }
        return sb.toString().trim();
    }

    /**
     * 将rows输出为CSV字符串（含表头行），自动转义特殊字符
     */
    public static String toCsv(List<String[]> rows) {
        if (rows.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            String[] escaped = new String[row.length];
            for (int i = 0; i < row.length; i++) {
                escaped[i] = escapeField(row[i]);
            }
            sb.append(String.join(",", escaped)).append("\n");
        }
        return sb.toString().trim();
    }
}
