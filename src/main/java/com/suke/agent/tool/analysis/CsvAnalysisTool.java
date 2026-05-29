/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description CSV分析工具，解析CSV并生成统计摘要
 */

package com.suke.agent.tool.analysis;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class CsvAnalysisTool {

    private static final int MAX_ROWS = 1000;

    @Tool(description = "解析CSV数据并生成统计摘要，包括列名、数据类型推断、行数、每列的基本统计量")
    public String analyzeCsv(
            @ToolParam(description = "CSV格式的原始数据，第一行为表头") String csvData,
            @ToolParam(description = "用户的分析目标") String analysisGoal) {

        if (csvData == null || csvData.isBlank()) {
            return "错误：CSV数据为空";
        }

        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvData.trim()));
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return "错误：CSV数据无表头";
            }

            String[] headers = parseCsvLine(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(parseCsvLine(line));
                    if (rows.size() >= MAX_ROWS) break;
                }
            }

            JSONObject result = new JSONObject();
            result.put("analysisGoal", analysisGoal);
            result.put("totalRows", rows.size());
            result.put("totalColumns", headers.length);
            result.put("truncated", rows.size() >= MAX_ROWS);

            JSONArray columnsArr = new JSONArray();
            for (int i = 0; i < headers.length; i++) {
                JSONObject colInfo = new JSONObject();
                colInfo.put("name", headers[i].trim());

                List<String> values = new ArrayList<>();
                for (String[] row : rows) {
                    if (i < row.length) {
                        values.add(row[i].trim());
                    }
                }

                long missingCount = values.stream().filter(String::isEmpty).count();
                colInfo.put("missingCount", missingCount);
                colInfo.put("missingRate", values.isEmpty() ? "0.00%" :
                        String.format("%.2f%%", (double) missingCount / values.size() * 100));

                // 推断类型
                String type = inferType(values);
                colInfo.put("inferredType", type);

                long uniqueCount = values.stream().filter(v -> !v.isEmpty()).distinct().count();
                colInfo.put("uniqueValues", uniqueCount);

                // 数值列统计
                if ("numeric".equals(type)) {
                    List<Double> nums = values.stream()
                            .filter(v -> !v.isEmpty())
                            .map(Double::parseDouble)
                            .collect(Collectors.toList());
                    if (!nums.isEmpty()) {
                        DoubleSummaryStatistics stats = nums.stream()
                                .mapToDouble(Double::doubleValue)
                                .summaryStatistics();
                        colInfo.put("min", stats.getMin());
                        colInfo.put("max", stats.getMax());
                        colInfo.put("mean", Math.round(stats.getAverage() * 100.0) / 100.0);
                    }
                }

                // 前5个样本值
                colInfo.put("sampleValues", values.stream()
                        .filter(v -> !v.isEmpty())
                        .limit(5)
                        .collect(Collectors.toList()));

                columnsArr.add(colInfo);
            }

            result.put("columns", columnsArr);
            return result.toJSONString();

        } catch (Exception e) {
            return "CSV解析错误: " + e.getMessage();
        }
    }

    private String[] parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
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

    private String inferType(List<String> values) {
        long numericCount = values.stream()
                .filter(v -> !v.isEmpty())
                .filter(v -> {
                    try {
                        Double.parseDouble(v);
                        return true;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }).count();

        long nonEmpty = values.stream().filter(v -> !v.isEmpty()).count();
        if (nonEmpty == 0) return "empty";
        if (numericCount == nonEmpty) return "numeric";

        // 检测日期
        long dateCount = values.stream()
                .filter(v -> !v.isEmpty())
                .filter(v -> v.matches("\\d{4}[-/]\\d{1,2}[-/]\\d{1,2}.*"))
                .count();
        if (dateCount > nonEmpty * 0.8) return "date";

        return "string";
    }
}
