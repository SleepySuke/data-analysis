package com.suke.agent.tool.analysis;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CsvAnalysisToolTest {

    private final CsvAnalysisTool tool = new CsvAnalysisTool();

    @Test
    void parseNormalCsv() {
        String csv = "name,age,score\nAlice,25,90.5\nBob,30,85.0\nCarol,28,92.3";
        String result = tool.analyzeCsv(csv, "分析学生成绩");

        JSONObject json = JSON.parseObject(result);
        assertEquals("分析学生成绩", json.getString("analysisGoal"));
        assertEquals(3, json.getIntValue("totalRows"));
        assertEquals(3, json.getIntValue("totalColumns"));
        assertFalse(json.getBooleanValue("truncated"));
    }

    @Test
    void parseEmptyCsv() {
        String result = tool.analyzeCsv("", "test");
        assertTrue(result.startsWith("错误"));
    }

    @Test
    void parseNullCsv() {
        String result = tool.analyzeCsv(null, "test");
        assertTrue(result.startsWith("错误"));
    }

    @Test
    void parseCsvDetectsNumericColumn() {
        String csv = "name,value\nA,100\nB,200\nC,300";
        String result = tool.analyzeCsv(csv, "test");
        JSONObject json = JSON.parseObject(result);

        var columns = json.getJSONArray("columns");
        JSONObject valueCol = columns.stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "value".equals(c.getString("name")))
                .findFirst().orElseThrow();

        assertEquals("numeric", valueCol.getString("inferredType"));
        assertTrue(valueCol.containsKey("mean"));
    }

    @Test
    void parseCsvDetectsMissingValues() {
        String csv = "name,age\nA,25\nB,\nC,30";
        String result = tool.analyzeCsv(csv, "test");
        JSONObject json = JSON.parseObject(result);

        var columns = json.getJSONArray("columns");
        JSONObject ageCol = columns.stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "age".equals(c.getString("name")))
                .findFirst().orElseThrow();

        assertEquals(1, ageCol.getIntValue("missingCount"));
    }
}
