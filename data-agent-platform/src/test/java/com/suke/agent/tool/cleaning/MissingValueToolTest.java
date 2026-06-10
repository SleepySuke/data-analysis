package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MissingValueToolTest {

    private final MissingValueTool tool = new MissingValueTool();

    @Test
    void fillWithMean() {
        String csv = "name,score\nAlice,80\nBob,\nCarol,90";
        String result = tool.handleMissingValues(csv, "mean", "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        assertTrue(cleaned.contains("85")); // mean of 80 and 90
    }

    @Test
    void fillWithForwardFill() {
        String csv = "name,score\nAlice,80\nBob,\nCarol,90";
        String result = tool.handleMissingValues(csv, "forward_fill", "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        assertTrue(cleaned.contains("80"));
    }

    @Test
    void dropRows() {
        String csv = "name,score\nAlice,80\nBob,\nCarol,90";
        String result = tool.handleMissingValues(csv, "drop", "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        assertTrue(cleaned.contains("Alice"));
        assertFalse(cleaned.contains("Bob,"));
        assertTrue(cleaned.contains("Carol"));
    }

    @Test
    void fillSpecificColumn() {
        String csv = "name,score,age\nAlice,80,20\nBob,,\nCarol,90,25";
        String result = tool.handleMissingValues(csv, "mean", "score");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        assertTrue(cleaned.contains("85"));
    }

    @Test
    void emptyCsvReturnsError() {
        String result = tool.handleMissingValues("", "mean", "");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void noMissingValuesReturnsOriginal() {
        String csv = "name,score\nAlice,80\nCarol,90";
        String result = tool.handleMissingValues(csv, "mean", "");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
    }

    @Test
    void handleMissingValuesWithMedianStrategy() {
        String csv = "name,value\nA,10\nB,20\nC,\nD,30";
        String result = tool.handleMissingValues(csv, "median", "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        // median of 10, 20, 30 is 20
        assertTrue(cleaned.contains("20"), "Expected missing value to be filled with median 20, got: " + cleaned);
        // Verify original rows remain
        assertTrue(cleaned.contains("A,10"));
        assertTrue(cleaned.contains("B,20"));
        assertTrue(cleaned.contains("D,30"));
    }

    @Test
    void handleMissingValuesWithModeStrategy() {
        String csv = "name,value\nA,10\nB,10\nC,\nD,20";
        String result = tool.handleMissingValues(csv, "mode", "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String cleaned = json.getString("data");
        // mode: 10 appears twice, 20 appears once → mode is 10
        assertTrue(cleaned.contains("C,10"), "Expected missing value to be filled with mode 10, got: " + cleaned);
    }

    @Test
    void handleMissingValuesWithNullCsvData() {
        String result = tool.handleMissingValues(null, "mean", "");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }
}
