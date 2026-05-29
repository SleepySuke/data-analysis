/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 统计分析工具，计算均值/中位数/标准差/相关系数等
 */

package com.suke.agent.tool.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StatisticalAnalysisTool {

    @Tool(description = "对CSV数据中的数值列计算统计指标，支持均值、中位数、标准差、相关系数、偏度、峰度")
    public String calculateStatistics(
            @ToolParam(description = "CSV格式的原始数据") String csvData,
            @ToolParam(description = "要计算的指标，逗号分隔：mean,median,stddev,correlation,skewness,kurtosis") String metrics) {

        if (csvData == null || csvData.isBlank()) {
            return "错误：数据为空";
        }

        try {
            String[] lines = csvData.trim().split("\n");
            if (lines.length < 2) {
                return "错误：数据行数不足";
            }

            String[] headers = lines[0].split(",", -1);
            List<double[]> numericData = new ArrayList<>();
            List<String> numericColumns = new ArrayList<>();

            // 识别数值列
            for (int col = 0; col < headers.length; col++) {
                List<Double> values = new ArrayList<>();
                boolean allNumeric = true;

                for (int i = 1; i < lines.length; i++) {
                    String[] parts = lines[i].split(",", -1);
                    if (col < parts.length) {
                        String val = parts[col].trim();
                        if (!val.isEmpty()) {
                            try {
                                values.add(Double.parseDouble(val));
                            } catch (NumberFormatException e) {
                                allNumeric = false;
                                break;
                            }
                        }
                    }
                }

                if (allNumeric && !values.isEmpty()) {
                    numericColumns.add(headers[col].trim());
                    double[] colData = values.stream().mapToDouble(Double::doubleValue).toArray();
                    numericData.add(colData);
                }
            }

            if (numericColumns.isEmpty()) {
                return "未找到数值列，无法计算统计指标";
            }

            Set<String> requestedMetrics = Arrays.stream(metrics.split(","))
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());

            JSONObject result = new JSONObject();
            result.put("metricsRequested", new ArrayList<>(requestedMetrics));

            JSONArray columnsResult = new JSONArray();
            for (int i = 0; i < numericColumns.size(); i++) {
                JSONObject colResult = new JSONObject();
                colResult.put("column", numericColumns.get(i));
                double[] data = numericData.get(i);

                if (requestedMetrics.contains("mean")) {
                    colResult.put("mean", round(mean(data)));
                }
                if (requestedMetrics.contains("median")) {
                    colResult.put("median", round(median(data)));
                }
                if (requestedMetrics.contains("stddev")) {
                    colResult.put("stddev", round(stddev(data)));
                }
                if (requestedMetrics.contains("skewness")) {
                    colResult.put("skewness", round(skewness(data)));
                }
                if (requestedMetrics.contains("kurtosis")) {
                    colResult.put("kurtosis", round(kurtosis(data)));
                }

                columnsResult.add(colResult);
            }
            result.put("columns", columnsResult);

            // 相关系数矩阵
            if (requestedMetrics.contains("correlation") && numericColumns.size() > 1) {
                JSONArray corrMatrix = new JSONArray();
                for (int i = 0; i < numericColumns.size(); i++) {
                    for (int j = i + 1; j < numericColumns.size(); j++) {
                        int minLen = Math.min(numericData.get(i).length, numericData.get(j).length);
                        double[] x = Arrays.copyOf(numericData.get(i), minLen);
                        double[] y = Arrays.copyOf(numericData.get(j), minLen);
                        JSONObject pair = new JSONObject();
                        pair.put("column1", numericColumns.get(i));
                        pair.put("column2", numericColumns.get(j));
                        pair.put("correlation", round(correlation(x, y)));
                        corrMatrix.add(pair);
                    }
                }
                result.put("correlations", corrMatrix);
            }

            return result.toJSONString();

        } catch (Exception e) {
            return "统计计算错误: " + e.getMessage();
        }
    }

    private double mean(double[] data) {
        return Arrays.stream(data).average().orElse(0);
    }

    private double median(double[] data) {
        double[] sorted = data.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2;
        }
        return sorted[n / 2];
    }

    private double stddev(double[] data) {
        double mean = mean(data);
        double variance = Arrays.stream(data)
                .map(v -> (v - mean) * (v - mean))
                .average().orElse(0);
        return Math.sqrt(variance);
    }

    private double skewness(double[] data) {
        double mean = mean(data);
        double sd = stddev(data);
        if (sd == 0) return 0;
        double n = data.length;
        return Arrays.stream(data)
                .map(v -> Math.pow((v - mean) / sd, 3))
                .sum() / n;
    }

    private double kurtosis(double[] data) {
        double mean = mean(data);
        double sd = stddev(data);
        if (sd == 0) return 0;
        double n = data.length;
        return Arrays.stream(data)
                .map(v -> Math.pow((v - mean) / sd, 4))
                .sum() / n - 3;
    }

    private double correlation(double[] x, double[] y) {
        double meanX = mean(x);
        double meanY = mean(y);
        double sumXY = 0, sumX2 = 0, sumY2 = 0;
        for (int i = 0; i < x.length; i++) {
            double dx = x[i] - meanX;
            double dy = y[i] - meanY;
            sumXY += dx * dy;
            sumX2 += dx * dx;
            sumY2 += dy * dy;
        }
        double denom = Math.sqrt(sumX2 * sumY2);
        return denom == 0 ? 0 : sumXY / denom;
    }

    private double round(double val) {
        return Math.round(val * 10000.0) / 10000.0;
    }
}
