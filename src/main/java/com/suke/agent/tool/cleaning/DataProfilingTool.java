/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 数据画像工具，扫描CSV生成每列统计摘要
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
public class DataProfilingTool {

    @Tool(description = "扫描CSV数据，生成每列的统计画像（类型、缺失率、唯一值、数值统计）")
    public String profileData(
            @ToolParam(description = "CSV格式的原始数据") String csvData) {

        if (csvData == null || csvData.isBlank()) {
            return CsvUtils.errorJson("CSV数据为空");
        }

        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvData.trim()));
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                return CsvUtils.errorJson("CSV数据无表头");
            }

            String[] headers = CsvUtils.parseCsvLine(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    rows.add(CsvUtils.parseCsvLine(line));
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("totalRows", rows.size());
            result.put("totalColumns", headers.length);

            JSONArray columnsArr = new JSONArray();
            for (int i = 0; i < headers.length; i++) {
                JSONObject colInfo = new JSONObject();
                colInfo.put("name", headers[i].trim());

                List<String> values = new ArrayList<>();
                for (String[] row : rows) {
                    values.add(i < row.length ? row[i].trim() : "");
                }

                long missingCount = values.stream().filter(String::isEmpty).count();
                colInfo.put("missingCount", missingCount);
                colInfo.put("missingRate", values.isEmpty() ? "0.00%" :
                        String.format("%.2f%%", (double) missingCount / values.size() * 100));

                String type = inferType(values);
                colInfo.put("inferredType", type);

                long uniqueCount = values.stream().filter(v -> !v.isEmpty()).distinct().count();
                colInfo.put("uniqueValues", uniqueCount);

                if ("numeric".equals(type)) {
                    List<Double> nums = values.stream()
                            .filter(v -> !v.isEmpty())
                            .map(Double::parseDouble)
                            .collect(Collectors.toList());
                    if (!nums.isEmpty()) {
                        DoubleSummaryStatistics stats = nums.stream()
                                .mapToDouble(Double::doubleValue).summaryStatistics();
                        colInfo.put("min", stats.getMin());
                        colInfo.put("max", stats.getMax());
                        colInfo.put("mean", Math.round(stats.getAverage() * 100.0) / 100.0);

                        List<Double> sorted = nums.stream().sorted().toList();
                        double median = sorted.size() % 2 == 0
                                ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
                                : sorted.get(sorted.size() / 2);
                        colInfo.put("median", Math.round(median * 100.0) / 100.0);
                    }
                }

                colInfo.put("sampleValues", values.stream()
                        .filter(v -> !v.isEmpty()).limit(5).collect(Collectors.toList()));

                columnsArr.add(colInfo);
            }

            result.put("columns", columnsArr);
            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("分析失败: " + e.getMessage());
        }
    }

    private String inferType(List<String> values) {
        long nonEmpty = values.stream().filter(v -> !v.isEmpty()).count();
        if (nonEmpty == 0) return "empty";

        long numericCount = values.stream()
                .filter(v -> !v.isEmpty())
                .filter(v -> {
                    try { Double.parseDouble(v); return true; }
                    catch (NumberFormatException e) { return false; }
                }).count();
        if (numericCount == nonEmpty) return "numeric";

        return "string";
    }

}
