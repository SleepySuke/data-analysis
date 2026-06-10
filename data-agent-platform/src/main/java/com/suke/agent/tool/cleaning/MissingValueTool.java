/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 缺失值处理工具，支持均值/中位数/众数/前向填充/删除策略
 */

package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.tool.cleaning.CsvUtils;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class MissingValueTool {

    @Tool(description = "处理CSV中的缺失值，支持mean/median/mode/forward_fill/drop策略")
    public String handleMissingValues(
            @ToolParam(description = "CSV数据") String csvData,
            @ToolParam(description = "策略：mean/median/mode/forward_fill/drop") String strategy,
            @ToolParam(description = "列名，逗号分隔，空则处理所有含缺失的列") String columns) {

        if (csvData == null || csvData.isBlank()) {
            return CsvUtils.errorJson("CSV数据为空");
        }

        try {
            BufferedReader reader = new BufferedReader(new StringReader(csvData.trim()));
            String headerLine = reader.readLine();
            if (headerLine == null) return CsvUtils.errorJson("CSV无表头");

            String[] headers = CsvUtils.parseCsvLine(headerLine);
            List<String[]> rows = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) rows.add(CsvUtils.parseCsvLine(line));
            }

            Set<String> targetCols = (columns == null || columns.isBlank())
                    ? null
                    : Arrays.stream(columns.split(",")).map(String::trim).collect(Collectors.toSet());

            List<Integer> colIndices = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) {
                if (targetCols == null || targetCols.contains(headers[i].trim())) {
                    colIndices.add(i);
                }
            }

            int filledCount = 0;
            List<String[]> resultRows = rows;

            if ("drop".equals(strategy)) {
                resultRows = new ArrayList<>();
                for (String[] row : rows) {
                    boolean hasMissing = false;
                    for (int idx : colIndices) {
                        if (idx >= row.length || row[idx].trim().isEmpty()) {
                            hasMissing = true;
                            break;
                        }
                    }
                    if (!hasMissing) {
                        resultRows.add(row);
                    } else {
                        filledCount++;
                    }
                }
            } else {
                for (int idx : colIndices) {
                    List<Double> nums = new ArrayList<>();
                    List<String> strings = new ArrayList<>();
                    String lastValid = "";

                    for (String[] row : rows) {
                        String val = idx < row.length ? row[idx].trim() : "";
                        if (!val.isEmpty()) {
                            lastValid = val;
                            try { nums.add(Double.parseDouble(val)); }
                            catch (NumberFormatException e) { strings.add(val); }
                        }
                    }

                    for (String[] row : resultRows) {
                        if (idx >= row.length) continue;
                        if (!row[idx].trim().isEmpty()) continue;

                        String fillValue = computeFill(strategy, nums, strings, lastValid);
                        if (fillValue != null) {
                            row[idx] = fillValue;
                            filledCount++;
                        }
                    }
                }
            }

            String cleanedCsv = CsvUtils.toCsv(headers, resultRows);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("data", cleanedCsv);
            result.put("processedCount", filledCount);
            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("处理失败: " + e.getMessage());
        }
    }

    private String computeFill(String strategy, List<Double> nums, List<String> strings, String lastValid) {
        return switch (strategy) {
            case "mean" -> nums.isEmpty() ? null : String.valueOf(Math.round(nums.stream().mapToDouble(d -> d).average().orElse(0) * 100.0) / 100.0);
            case "median" -> {
                if (nums.isEmpty()) yield null;
                List<Double> sorted = nums.stream().sorted().toList();
                double median = sorted.size() % 2 == 0
                        ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0
                        : sorted.get(sorted.size() / 2);
                yield String.valueOf(Math.round(median * 100.0) / 100.0);
            }
            case "mode" -> {
                if (nums.isEmpty() && strings.isEmpty()) yield null;
                Map<String, Integer> freq = new HashMap<>();
                nums.forEach(n -> freq.merge(String.valueOf(n), 1, Integer::sum));
                strings.forEach(s -> freq.merge(s, 1, Integer::sum));
                yield freq.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey).orElse(null);
            }
            case "forward_fill" -> lastValid.isEmpty() ? null : lastValid;
            default -> null;
        };
    }

}
