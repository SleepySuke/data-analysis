package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataProfilingToolTest {

    private final DataProfilingTool tool = new DataProfilingTool();

    @Test
    void profileNormalCsv() {
        String csv = "name,age,score\nAlice,25,90.5\nBob,30,85.0\nCarol,28,92.3";
        String result = tool.profileData(csv);
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(3, json.getIntValue("totalRows"));
        assertEquals(3, json.getIntValue("totalColumns"));

        JSONArray columns = json.getJSONArray("columns");
        assertEquals(3, columns.size());
    }

    @Test
    void profileDetectsNumericColumn() {
        String csv = "name,value\nA,100\nB,200\nC,300";
        String result = tool.profileData(csv);
        JSONObject json = JSON.parseObject(result);

        JSONObject valueCol = findColumn(json, "value");
        assertEquals("numeric", valueCol.getString("inferredType"));
        assertTrue(valueCol.containsKey("mean"));
    }

    @Test
    void profileDetectsMissingValues() {
        String csv = "name,age\nA,25\nB,\nC,30";
        String result = tool.profileData(csv);
        JSONObject json = JSON.parseObject(result);

        JSONObject ageCol = findColumn(json, "age");
        assertEquals(1, ageCol.getIntValue("missingCount"));
    }

    @Test
    void profileEmptyCsvReturnsError() {
        String result = tool.profileData("");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void profileNullCsvReturnsError() {
        String result = tool.profileData(null);
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    private JSONObject findColumn(JSONObject json, String name) {
        JSONArray columns = json.getJSONArray("columns");
        return columns.stream()
                .map(o -> (JSONObject) o)
                .filter(c -> name.equals(c.getString("name")))
                .findFirst().orElseThrow();
    }
}
