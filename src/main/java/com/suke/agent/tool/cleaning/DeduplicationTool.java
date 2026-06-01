/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29
 * @description 去重工具，支持全行去重和按指定列去重
 */

package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DeduplicationTool {

    @Tool(description = "去除CSV中的重复数据，支持全行或按指定列去重")
    public String deduplicate(
            @ToolParam(description = "CSV数据") String csvData,
            @ToolParam(description = "去重判断列，逗号分隔，空则全行比较") String columns) {

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

            Set<Integer> dedupColIndices;
            if (columns == null || columns.isBlank()) {
                dedupColIndices = null; // all columns
            } else {
                dedupColIndices = Arrays.stream(columns.split(","))
                        .map(String::trim)
                        .map(col -> {
                            for (int i = 0; i < headers.length; i++) {
                                if (headers[i].trim().equals(col)) return i;
                            }
                            return -1;
                        })
                        .filter(i -> i >= 0)
                        .collect(Collectors.toSet());
            }

            Set<String> seen = new LinkedHashSet<>();
            List<String[]> uniqueRows = new ArrayList<>();
            int removedCount = 0;

            for (String[] row : rows) {
                String key = buildKey(row, dedupColIndices);
                if (seen.add(key)) {
                    uniqueRows.add(row);
                } else {
                    removedCount++;
                }
            }

            String resultCsv = CsvUtils.toCsv(headers, uniqueRows);
            JSONObject result = new JSONObject();
            result.put("success", true);
            result.put("data", resultCsv);
            result.put("removedCount", removedCount);
            return result.toJSONString();

        } catch (Exception e) {
            return CsvUtils.errorJson("去重失败: " + e.getMessage());
        }
    }

    private static final String KEY_SEPARATOR = "\0";

    private String buildKey(String[] row, Set<Integer> colIndices) {
        if (colIndices == null) {
            return String.join(KEY_SEPARATOR, row);
        }
        return colIndices.stream()
                .sorted()
                .map(i -> i < row.length ? row[i] : "")
                .collect(Collectors.joining(KEY_SEPARATOR));
    }

}
