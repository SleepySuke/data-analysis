/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 数据转换工具，支持CSV列的类型转换与格式化
 */

package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DataTransformTool {

    private static final Set<String> VALID_OPERATIONS = Set.of(
            "trim", "to_lower", "to_upper", "parse_date", "format_number"
    );

    @Tool(description = "对CSV列执行类型转换与格式化操作")
    public String transformData(
            @ToolParam(description = "CSV数据") String csvData,
            @ToolParam(description = "转换操作JSON数组，如 [{\"column\":\"name\",\"operation\":\"trim\"}]") String transforms) {

        if (csvData == null || csvData.isBlank()) {
            return CsvUtils.errorJson("CSV数据为空");
        }
        if (transforms == null || transforms.isBlank()) {
            return CsvUtils.errorJson("转换操作为空");
        }

        try {
            JSONArray transformArr = JSON.parseArray(transforms);
            if (transformArr == null || transformArr.isEmpty()) {
                return CsvUtils.errorJson("转换操作格式错误");
            }

            BufferedReader reader = new BufferedReader(new StringReader(csvData.trim()));
            String headerLine = reader.readLine();
            if (headerLine == null) return CsvUtils.errorJson("CSV无表头");

            String[] headers = CsvUtils.parseCsvLine(headerLine);
            Map<String, Integer> colIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                colIndex.put(headers[i].trim(), i);
            }

            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(CsvUtils.parseCsvLine(line));
            }

            int transformedCount = 0;
            for (int t = 0; t < transformArr.size(); t++) {
                JSONObject transform = transformArr.getJSONObject(t);
                String column = transform.getString("column");
                String operation = transform.getString("operation");

                if (column == null || operation == null) continue;
                if (!VALID_OPERATIONS.contains(operation)) continue;

                Integer idx = colIndex.get(column.trim());
                if (idx == null) continue;

                for (String[] row : rows) {
                    if (idx >= row.length) continue;
                    String original = row[idx];
                    String transformed = applyTransform(original, operation);
                    if (!original.equals(transformed)) {
                        row[idx] = transformed;
                        transformedCount++;
                    }
                }
            }

            String resultCsv = CsvUtils.toCsv(headers, rows);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("data", resultCsv);
            result.put("transformedCount", transformedCount);
            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("转换失败: " + e.getMessage());
        }
    }

    private String applyTransform(String value, String operation) {
        return switch (operation) {
            case "trim" -> value.trim();
            case "to_lower" -> value.toLowerCase();
            case "to_upper" -> value.toUpperCase();
            case "parse_date" -> {
                try {
                    String trimmed = value.trim();
                    if (trimmed.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*")) {
                        yield trimmed.replaceAll("/", "-").split(" ")[0];
                    }
                    yield value;
                } catch (Exception e) { yield value; }
            }
            case "format_number" -> {
                try {
                    double d = Double.parseDouble(value.trim());
                    yield String.valueOf(Math.round(d * 100.0) / 100.0);
                } catch (NumberFormatException e) { yield value; }
            }
            default -> value;
        };
    }

}
