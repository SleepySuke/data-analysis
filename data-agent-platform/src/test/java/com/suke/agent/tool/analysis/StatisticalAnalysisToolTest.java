package com.suke.agent.tool.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatisticalAnalysisToolTest {

    private final StatisticalAnalysisTool tool = new StatisticalAnalysisTool();

    private static final String CSV = "name,age,score\nAlice,25,90\nBob,30,85\nCarol,28,95\nDave,35,70\nEve,22,88";

    @Test
    void calculateMean() {
        String result = tool.calculateStatistics(CSV, "mean");

        JSONObject json = JSON.parseObject(result);
        var columns = json.getJSONArray("columns");
        assertFalse(columns.isEmpty());

        JSONObject ageCol = columns.stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "age".equals(c.getString("column")))
                .findFirst().orElseThrow();

        assertTrue(ageCol.containsKey("mean"));
        assertEquals(28.0, ageCol.getDouble("mean"));
    }

    @Test
    void calculateMedian() {
        String result = tool.calculateStatistics(CSV, "median");
        JSONObject json = JSON.parseObject(result);

        JSONObject ageCol = json.getJSONArray("columns").stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "age".equals(c.getString("column")))
                .findFirst().orElseThrow();

        assertTrue(ageCol.containsKey("median"));
    }

    @Test
    void calculateCorrelation() {
        String result = tool.calculateStatistics(CSV, "correlation");

        JSONObject json = JSON.parseObject(result);
        assertTrue(json.containsKey("correlations"));
        var corr = json.getJSONArray("correlations");
        assertFalse(corr.isEmpty());
    }

    @Test
    void emptyDataReturnsError() {
        String result = tool.calculateStatistics("", "mean");
        assertTrue(result.startsWith("错误"));
    }

    @Test
    void insufficientDataReturnsNoNumericColumns() {
        String result = tool.calculateStatistics("name\nAlice", "mean");
        assertTrue(result.contains("未找到数值列"));
    }

    @Test
    void allMetricsCalculable() {
        String result = tool.calculateStatistics(CSV, "mean,median,stddev,skewness,kurtosis");
        JSONObject json = JSON.parseObject(result);

        JSONObject ageCol = json.getJSONArray("columns").stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "age".equals(c.getString("column")))
                .findFirst().orElseThrow();

        assertTrue(ageCol.containsKey("mean"));
        assertTrue(ageCol.containsKey("median"));
        assertTrue(ageCol.containsKey("stddev"));
        assertTrue(ageCol.containsKey("skewness"));
        assertTrue(ageCol.containsKey("kurtosis"));
    }
}
