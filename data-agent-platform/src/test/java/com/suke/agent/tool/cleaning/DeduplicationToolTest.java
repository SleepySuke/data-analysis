package com.suke.agent.tool.cleaning;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeduplicationToolTest {

    private final DeduplicationTool tool = new DeduplicationTool();

    @Test
    void dedupAllColumns() {
        String csv = "name,age\nAlice,25\nBob,30\nAlice,25\nCarol,28";
        String result = tool.deduplicate(csv, "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("removedCount"));
        String data = json.getString("data");
        assertTrue(data.contains("Alice,25"));
        assertTrue(data.contains("Bob,30"));
        assertTrue(data.contains("Carol,28"));
    }

    @Test
    void dedupByColumn() {
        String csv = "name,age\nAlice,25\nBob,30\nAlice,28\nCarol,28";
        String result = tool.deduplicate(csv, "name");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("removedCount"));
        String data = json.getString("data");
        assertTrue(data.contains("Alice,25"));
    }

    @Test
    void noDuplicates() {
        String csv = "name,age\nAlice,25\nBob,30\nCarol,28";
        String result = tool.deduplicate(csv, "");
        JSONObject json = JSON.parseObject(result);

        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("removedCount"));
    }

    @Test
    void emptyCsvReturnsError() {
        String result = tool.deduplicate("", "");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void nullCsvReturnsError() {
        String result = tool.deduplicate(null, "");
        JSONObject json = JSON.parseObject(result);
        assertFalse(json.getBooleanValue("success"));
    }

    @Test
    void noCollisionWithPipeInData() {
        // Row 1: col1="a|b", col2="c"
        // Row 2: col1="a", col2="b|c"
        // These are different rows and should NOT be deduplicated
        String csv = "col1,col2\n\"a|b\",c\na,\"b|c\"";
        String result = tool.deduplicate(csv, "");
        JSONObject json = JSON.parseObject(result);
        assertTrue(json.getBooleanValue("success"));
        assertEquals(0, json.getIntValue("removedCount"),
            "Different data should not be falsely deduplicated due to pipe separator collision");
    }
}
