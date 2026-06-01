package com.suke.agent.specialized;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.tool.cleaning.*;
import com.suke.agent.tool.sql.ResultInterpreterTool;
import com.suke.agent.tool.sql.SqlExecutionTool;
import com.suke.agent.tool.scraping.ContentExtractorTool;
import com.suke.agent.tool.scraping.UrlFetchTool;
import org.junit.jupiter.api.*;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * E2E tests for all specialized agents.
 * Each test defines an expected fixture and verifies actual matches expected.
 */
class AgentE2ETest {

    // ==================== Fixture 1: UrlFetchTool ====================

    @Test
    void fixture1_urlFetchTool_returnsHtmlContent() {
        UrlFetchTool tool = new UrlFetchTool();
        String actual = tool.fetchUrl("https://example.com", 30);

        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertTrue(json.getBooleanValue("success"), "Expected success=true");
        assertTrue(json.getString("html").length() > 0, "Expected non-empty html");
        assertEquals("https://example.com", json.getString("url"), "Expected url to match input");
        assertEquals(200, json.getIntValue("statusCode"), "Expected HTTP 200");
    }

    // ==================== Fixture 2: ContentExtractorTool ====================

    @Test
    void fixture2_contentExtractor_extractsTableFromHtml() {
        String inputHtml = """
            <html><head><title>Test</title></head><body>
            <table>
              <tr><th>Name</th><th>Age</th></tr>
              <tr><td>Alice</td><td>25</td></tr>
              <tr><td>Bob</td><td>30</td></tr>
            </table>
            </body></html>
            """;

        ContentExtractorTool tool = new ContentExtractorTool();
        String actual = tool.extractContent(inputHtml, "table");
        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertTrue(json.getBooleanValue("success"));
        assertEquals("Test", json.getString("title"));

        JSONArray tables = json.getJSONArray("tables");
        assertEquals(1, tables.size(), "Expected exactly 1 table");

        JSONObject table = tables.getJSONObject(0);
        assertEquals("Name", table.getJSONArray("headers").getString(0));
        assertEquals("Age", table.getJSONArray("headers").getString(1));
        assertEquals(2, table.getJSONArray("rows").size(), "Expected 2 data rows");
        assertEquals("Alice", table.getJSONArray("rows").getJSONArray(0).getString(0));
    }

    // ==================== Fixture 3: SqlExecutionTool security ====================

    @Test
    void fixture3_sqlExecutionTool_rejectsDropTable() {
        SqlExecutionTool tool = new SqlExecutionTool(null);
        String actual = tool.executeSql("DROP TABLE users");
        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertFalse(json.getBooleanValue("success"), "DROP must be rejected");
        String error = json.getString("error");
        assertTrue(error.contains("SELECT"), "Error must mention only SELECT is allowed");
    }

    // ==================== Fixture 4: DataProfilingTool ====================

    @Test
    void fixture4_dataProfilingTool_detectsMissingValues() {
        String inputCsv = "name,age\nA,25\nB,\nC,30";

        DataProfilingTool tool = new DataProfilingTool();
        String actual = tool.profileData(inputCsv);
        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertTrue(json.getBooleanValue("success"));
        assertEquals(3, json.getIntValue("totalRows"));
        assertEquals(2, json.getIntValue("totalColumns"));

        // age column must have missingCount=1
        JSONArray columns = json.getJSONArray("columns");
        JSONObject ageCol = columns.stream()
                .map(o -> (JSONObject) o)
                .filter(c -> "age".equals(c.getString("name")))
                .findFirst().orElseThrow();
        assertEquals(1, ageCol.getIntValue("missingCount"), "age column must have 1 missing value");
    }

    // ==================== Fixture 5: DeduplicationTool ====================

    @Test
    void fixture5_deduplicationTool_removesExactDuplicates() {
        String inputCsv = "name,age\nAlice,25\nBob,30\nAlice,25\nCarol,28";

        DeduplicationTool tool = new DeduplicationTool();
        String actual = tool.deduplicate(inputCsv, "");
        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertTrue(json.getBooleanValue("success"));
        assertEquals(1, json.getIntValue("removedCount"), "Expected 1 duplicate removed");
        String data = json.getString("data");
        assertTrue(data.contains("Alice,25"));
        assertTrue(data.contains("Bob,30"));
        assertTrue(data.contains("Carol,28"));
    }

    // ==================== Fixture 6: ResultInterpreterTool ====================

    @Test
    void fixture6_resultInterpreter_summarizesQueryResults() {
        String inputResult = "[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]";

        ResultInterpreterTool tool = new ResultInterpreterTool();
        String actual = tool.interpretResult(inputResult, "查询所有用户");
        JSONObject json = JSON.parseObject(actual);

        // Expected fixture
        assertTrue(json.getBooleanValue("success"));
        assertEquals(2, json.getIntValue("rowCount"), "Expected rowCount=2");
        assertTrue(json.getJSONArray("columns").contains("id"), "Expected columns to contain 'id'");
        assertTrue(json.getJSONArray("columns").contains("name"), "Expected columns to contain 'name'");
        assertNotNull(json.getString("summary"));
    }

    // ==================== Fixture 7: Cross-tool pipeline ====================

    @Test
    void fixture7_pipeline_profileThenClean() {
        String inputCsv = "name,value\nA,100\nB,\nC,300\nA,100";

        // Step 1: Profile
        DataProfilingTool profiler = new DataProfilingTool();
        String profileResult = profiler.profileData(inputCsv);
        JSONObject profileJson = JSON.parseObject(profileResult);
        assertTrue(profileJson.getBooleanValue("success"));

        // Step 2: Fill missing values
        MissingValueTool filler = new MissingValueTool();
        String fillResult = filler.handleMissingValues(inputCsv, "mean", "");
        JSONObject fillJson = JSON.parseObject(fillResult);
        assertTrue(fillJson.getBooleanValue("success"));

        // Step 3: Deduplicate
        String cleanedData = fillJson.getString("data");
        DeduplicationTool dedup = new DeduplicationTool();
        String dedupResult = dedup.deduplicate(cleanedData, "");
        JSONObject dedupJson = JSON.parseObject(dedupResult);
        assertTrue(dedupJson.getBooleanValue("success"));

        // Expected fixture: pipeline produces clean data with no missing and no dups
        assertEquals(1, dedupJson.getIntValue("removedCount"), "Expected 1 duplicate removed");
    }
}
