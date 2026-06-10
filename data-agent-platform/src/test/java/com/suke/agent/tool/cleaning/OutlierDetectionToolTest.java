package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OutlierDetectionToolTest {

    private final OutlierDetectionTool tool = new OutlierDetectionTool();

    @Test
    void detectWithIqr() {
        String csv = "name,value\nA,10\nB,12\nC,11\nD,13\nE,100";
        String result = tool.detectOutliers(csv, "iqr", "detect");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getIntValue("outlierCount") >= 1);
    }

    @Test
    void detectWithZscore() {
        // 20 values ~10, one outlier at 1000 → std ≈ 218, z ≈ 4.5
        StringBuilder csv = new StringBuilder("name,value\n");
        for (int i = 0; i < 20; i++) {
            csv.append("V").append(i).append(",").append(10 + i % 3).append("\n");
        }
        csv.append("OUT,1000");

        String result = tool.detectOutliers(csv.toString(), "zscore", "detect");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getIntValue("outlierCount") >= 1);
    }

    @Test
    void noOutliersInCleanData() {
        String csv = "name,value\nA,10\nB,11\nC,12\nD,11";
        String result = tool.detectOutliers(csv, "iqr", "detect");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("outlierCount"));
    }

    @Test
    void removeOutliers() {
        String csv = "name,value\nA,10\nB,12\nC,11\nD,100";
        String result = tool.detectOutliers(csv, "iqr", "remove");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        assertNotNull(cleaned);
    }

    @Test
    void emptyCsvReturnsError() {
        String result = tool.detectOutliers("", "iqr", "detect");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void detectOutliersWithCapAction() {
        // 4 normal values ~10-13 and 1 outlier at 100
        // With cap action, rows count should remain 5 (no rows removed)
        String csv = "name,value\nA,10\nB,12\nC,11\nD,13\nE,100";
        String result = tool.detectOutliers(csv, "iqr", "cap");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        // Verify outliers were detected
        assertTrue(json.getIntValue("outlierCount") >= 1, "Expected at least 1 outlier");
        // Verify data is returned (capped, not removed)
        String data = json.getString("data");
        assertNotNull(data, "Expected data field with capped values");
        // Count rows in output: header + 5 data rows = 6 lines
        long lineCount = data.lines().count();
        assertEquals(6, lineCount, "Cap should preserve all rows, expected 6 lines (header + 5 rows)");
        // Verify outlier value was capped (should no longer contain ",100")
        assertFalse(data.contains("E,100"), "Outlier 100 should have been capped to a lower value");
    }

    @Test
    void detectOutliersWithNullCsvData() {
        String result = tool.detectOutliers(null, "iqr", "detect");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void outlierIndexCorrectWithMissingValues() {
        // Use data with enough rows so IQR actually detects outliers.
        // 8 normal values around 10-12, 1 empty row, 1 outlier at row index 9
        StringBuilder csv = new StringBuilder("name,value\n");
        for (int i = 0; i < 4; i++) csv.append("N").append(i).append(",").append(10 + i % 3).append("\n");
        csv.append("EMPTY,\n");  // row index 4 (empty value)
        for (int i = 5; i < 9; i++) csv.append("N").append(i).append(",").append(10 + i % 3).append("\n");
        csv.append("OUTLIER,10000"); // row index 9

        String result = tool.detectOutliers(csv.toString(), "iqr", "detect");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
        assertTrue(json.getIntValue("outlierCount") >= 1, "Should detect the outlier 10000");

        var outliers = json.getJSONArray("outliers");
        assertNotNull(outliers);
        assertFalse(outliers.isEmpty());

        JSONObject outlier = outliers.getJSONObject(0);
        // The outlier is at original row index 9, not the filtered values index
        assertEquals(9, outlier.getIntValue("rowIndex"),
            "Outlier at OUTLIER,10000 should have rowIndex=9, not the filtered values index");
        assertEquals("value", outlier.getString("column"));
        assertEquals("10000", outlier.getString("value"));
    }
}
