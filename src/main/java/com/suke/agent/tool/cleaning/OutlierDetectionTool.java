/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 异常值检测工具，支持IQR和Z-score方法
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
public class OutlierDetectionTool {

    @Tool(description = "使用IQR或Z-score方法检测数值列异常值，支持detect/remove/cap操作")
    public String detectOutliers(
            @ToolParam(description = "CSV数据") String csvData,
            @ToolParam(description = "方法：iqr/zscore，默认iqr") String method,
            @ToolParam(description = "操作：detect/remove/cap，默认detect") String action) {

        if (csvData == null || csvData.isBlank()) {
            return CsvUtils.errorJson("CSV数据为空");
        }

        String m = (method == null || method.isBlank()) ? "iqr" : method.toLowerCase();
        String a = (action == null || action.isBlank()) ? "detect" : action.toLowerCase();

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

            List<Integer> numericCols = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) {
                int idx = i;
                boolean isNumeric = rows.stream()
                        .filter(r -> idx < r.length && !r[idx].trim().isEmpty())
                        .allMatch(r -> {
                            try { Double.parseDouble(r[idx].trim()); return true; }
                            catch (NumberFormatException e) { return false; }
                        });
                if (isNumeric && rows.stream().filter(r -> idx < r.length && !r[idx].trim().isEmpty()).count() > 0) {
                    numericCols.add(i);
                }
            }

            Set<Integer> outlierRowIndices = new LinkedHashSet<>();
            JSONArray outlierDetails = new JSONArray();

            for (int col : numericCols) {
                // Build a list of (originalRowIndex, value) pairs, skipping empty values
                List<int[]> indexMap = new ArrayList<>(); // [originalRowIdx, valuesListIdx]
                List<Double> values = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    String[] row = rows.get(i);
                    if (col < row.length && !row[col].trim().isEmpty()) {
                        try {
                            values.add(Double.parseDouble(row[col].trim()));
                            indexMap.add(new int[]{i, values.size() - 1});
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (values.size() < 3) continue;

                Set<Integer> outliers = switch (m) {
                    case "iqr" -> detectIqr(values);
                    case "zscore" -> detectZscore(values);
                    default -> detectIqr(values);
                };

                for (int valIdx : outliers) {
                    // Map values-list index back to original rows index
                    int rowIdx = indexMap.get(valIdx)[0];
                    outlierRowIndices.add(rowIdx);
                    JSONObject detail = new JSONObject();
                    detail.put("rowIndex", rowIdx);
                    detail.put("column", headers[col].trim());
                    detail.put("value", rows.get(rowIdx)[col].trim());
                    outlierDetails.add(detail);
                }
            }

            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("outlierCount", outlierRowIndices.size());
            result.put("outliers", outlierDetails);

            if ("remove".equals(a)) {
                List<String[]> cleaned = new ArrayList<>();
                for (int i = 0; i < rows.size(); i++) {
                    if (!outlierRowIndices.contains(i)) {
                        cleaned.add(rows.get(i));
                    }
                }
                result.put("data", CsvUtils.toCsv(headers, cleaned));
            } else if ("cap".equals(a)) {
                List<String[]> capped = new ArrayList<>(rows);
                for (int col : numericCols) {
                    List<Double> values = rows.stream()
                            .filter(r -> col < r.length && !r[col].trim().isEmpty())
                            .map(r -> Double.parseDouble(r[col].trim()))
                            .sorted().collect(Collectors.toList());
                    if (values.isEmpty()) continue;

                    double q1 = values.get(values.size() / 4);
                    double q3 = values.get(values.size() * 3 / 4);
                    double iqr = q3 - q1;
                    double lower = q1 - 1.5 * iqr;
                    double upper = q3 + 1.5 * iqr;

                    for (int i = 0; i < capped.size(); i++) {
                        if (col < capped.get(i).length && !capped.get(i)[col].trim().isEmpty()) {
                            try {
                                double val = Double.parseDouble(capped.get(i)[col].trim());
                                if (val < lower) capped.get(i)[col] = String.valueOf(Math.round(lower * 100.0) / 100.0);
                                else if (val > upper) capped.get(i)[col] = String.valueOf(Math.round(upper * 100.0) / 100.0);
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                }
                result.put("data", CsvUtils.toCsv(headers, capped));
            }

            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("检测失败: " + e.getMessage());
        }
    }

    private Set<Integer> detectIqr(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        double q1 = sorted.get(sorted.size() / 4);
        double q3 = sorted.get(sorted.size() * 3 / 4);
        double iqr = q3 - q1;
        double lower = q1 - 1.5 * iqr;
        double upper = q3 + 1.5 * iqr;

        Set<Integer> outliers = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i) < lower || values.get(i) > upper) {
                outliers.add(i);
            }
        }
        return outliers;
    }

    private Set<Integer> detectZscore(List<Double> values) {
        double mean = values.stream().mapToDouble(d -> d).average().orElse(0);
        double std = Math.sqrt(values.stream().mapToDouble(d -> Math.pow(d - mean, 2)).average().orElse(0));
        if (std == 0) return Set.of();

        Set<Integer> outliers = new LinkedHashSet<>();
        for (int i = 0; i < values.size(); i++) {
            if (Math.abs((values.get(i) - mean) / std) > 3) {
                outliers.add(i);
            }
        }
        return outliers;
    }

}
