package com.suke.agent.tool.sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultInterpreterToolTest {

    private final ResultInterpreterTool tool = new ResultInterpreterTool();

    @Test
    void interpretResultsReturnsSummary() {
        String queryResult = "[{\"id\":1,\"name\":\"Alice\",\"score\":85},{\"id\":2,\"name\":\"Bob\",\"score\":92}]";
        String result = tool.interpretResult(queryResult, "查询所有学生成绩");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(2, json.getIntValue("rowCount"));
        assertTrue(json.getJSONArray("columns").size() >= 1);
        assertNotNull(json.getString("summary"));
    }

    @Test
    void interpretEmptyResults() {
        String result = tool.interpretResult("[]", "查询数据");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("rowCount"));
        assertNotNull(json.getString("summary"));
    }

    @Test
    void interpretNullResults() {
        String result = tool.interpretResult(null, "查询数据");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("rowCount"));
    }

    @Test
    void interpretInvalidJson() {
        String result = tool.interpretResult("not json", "查询数据");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
        assertNotNull(json.getString("summary"));
    }

    @Test
    void interpretResultWithNullUserQuestion() {
        String queryResult = "[{\"id\":1,\"name\":\"Alice\"}]";
        String result = tool.interpretResult(queryResult, null);
        JSONObject json = JSON.parseObject(result);

        // Should not crash, should return success with empty userQuestion
        assertTrue(json.getBooleanValue("success"));
        assertEquals("", json.getString("userQuestion"));
        assertEquals(1, json.getIntValue("rowCount"));
        assertNotNull(json.getString("summary"));
    }

    @Test
    void interpretResultWithBlankQueryResult() {
        String result = tool.interpretResult("", "查询数据");
        JSONObject json = JSON.parseObject(result);

        // Blank queryResult triggers the null/blank branch: rowCount=0, empty columns
        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("rowCount"));
        assertNotNull(json.getString("summary"));
    }
}
