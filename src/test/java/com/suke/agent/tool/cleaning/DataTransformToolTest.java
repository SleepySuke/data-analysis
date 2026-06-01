package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DataTransformToolTest {

    private final DataTransformTool tool = new DataTransformTool();

    @Test
    void trimStringColumn() {
        String csv = "name,value\n Alice ,80\n Bob  ,90";
        String result = tool.transformData(csv, "[{\"column\":\"name\",\"operation\":\"trim\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        assertTrue(data.contains("Alice,80"));
        assertFalse(data.contains(" Alice "));
    }

    @Test
    void toLowerCaseColumn() {
        String csv = "name,value\nALICE,80\nBOB,90";
        String result = tool.transformData(csv, "[{\"column\":\"name\",\"operation\":\"to_lower\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        assertTrue(data.contains("alice"));
        assertFalse(data.contains("ALICE"));
    }

    @Test
    void toUpperCaseColumn() {
        String csv = "name,value\nalice,80\nbob,90";
        String result = tool.transformData(csv, "[{\"column\":\"name\",\"operation\":\"to_upper\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        assertTrue(data.contains("ALICE"));
    }

    @Test
    void multipleTransforms() {
        String csv = "name,city\n Alice , beijing\n Bob , shanghai";
        String result = tool.transformData(csv,
                "[{\"column\":\"name\",\"operation\":\"trim\"},{\"column\":\"city\",\"operation\":\"to_upper\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        assertTrue(data.contains("Alice"));
        assertTrue(data.contains("BEIJING"));
    }

    @Test
    void emptyCsvReturnsError() {
        String result = tool.transformData("", "[{\"column\":\"name\",\"operation\":\"trim\"}]");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void invalidTransformsJsonReturnsError() {
        String result = tool.transformData("name\nAlice", "not json");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void transformDataWithParseDate() {
        // parse_date converts date formats: "2024/01/15" -> "2024-01-15"
        String csv = "name,date\nAlice,2024/01/15\nBob,2023-12-25";
        String result = tool.transformData(csv, "[{\"column\":\"date\",\"operation\":\"parse_date\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        // Slash-formatted date should be converted to dash format
        assertTrue(data.contains("2024-01-15"), "Expected slash date to be converted to dash format, got: " + data);
        // Already dash-formatted date should remain unchanged
        assertTrue(data.contains("2023-12-25"), "Expected dash date to remain unchanged, got: " + data);
    }

    @Test
    void transformDataWithFormatNumber() {
        // format_number rounds to 2 decimal places: 3.14159 -> 3.14
        String csv = "name,value\nAlice,3.14159\nBob,2.71828";
        String result = tool.transformData(csv, "[{\"column\":\"value\",\"operation\":\"format_number\"}]");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        String data = json.getString("data");
        assertTrue(data.contains("3.14"), "Expected 3.14159 formatted to 3.14, got: " + data);
        assertTrue(data.contains("2.72"), "Expected 2.71828 formatted to 2.72, got: " + data);
    }

    @Test
    void transformDataWithNullCsvData() {
        String result = tool.transformData(null, "[{\"column\":\"name\",\"operation\":\"trim\"}]");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }

    @Test
    void transformDataWithEmptyTransforms() {
        // Empty JSON array "[]" triggers: transformArr.isEmpty() -> errorJson("转换操作格式错误")
        String csv = "name,value\nAlice,80";
        String result = tool.transformData(csv, "[]");
        JSONObject json = JSON.parseObject(result);

        assertFalse(json.getBooleanValue("success"));
        assertNotNull(json.getString("error"));
    }
}
