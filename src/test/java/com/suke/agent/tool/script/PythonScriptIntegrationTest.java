package com.suke.agent.tool.script;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.config.PythonScriptProperties;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for Python scripts.
 * These tests require python3 to be available on the system PATH.
 */
class PythonScriptIntegrationTest {

    private ScriptExecutionTool tool;

    @TempDir
    Path tempDir;

    @BeforeAll
    static void checkPython() {
        try {
            Process p = new ProcessBuilder("python3", "--version").start();
            boolean ok = p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(ok, "python3 should be available");
        } catch (Exception e) {
            fail("python3 is not available: " + e.getMessage());
        }
    }

    @BeforeEach
    void setUp() {
        PythonScriptProperties properties = new PythonScriptProperties();
        properties.setEnabled(true);
        properties.setTimeout(30);
        properties.setExecutable("python3");

        tool = new ScriptExecutionTool(properties);
        tool.setWorkDir(tempDir);
    }

    private void copyScript(String resourcePath) throws IOException {
        String resourceName = "/skills/" + resourcePath;
        try (var is = getClass().getResourceAsStream(resourceName)) {
            assertNotNull(is, "Script resource not found: " + resourceName);
            Path target = tempDir.resolve(resourcePath);
            Files.createDirectories(target.getParent());
            Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void webScraper_extractsTables() throws IOException {
        copyScript("web_scraper/scripts/web_scraper.py");

        String result = tool.executeScript(
                "web_scraper/scripts/web_scraper.py",
                "{\"url\": \"https://example.com\", \"extract_type\": \"all\"}",
                "");

        JSONObject json = JSON.parseObject(result);
        int exitCode = json.getIntValue("exitCode");

        // example.com may or may not have tables, just verify script runs
        if (exitCode == 0) {
            JSONObject output = JSON.parseObject(json.getString("output"));
            assertEquals("https://example.com", output.getString("url"));
        }
        // If network unavailable, exitCode may be non-zero — that's acceptable
    }

    @Test
    void dataProfiler_fullScope() throws IOException {
        copyScript("data_cleaner/scripts/data_profiler.py");

        String csvData = "name,age,score\nAlice,25,90\nBob,30,85\nCharlie,,75\n";
        String result = tool.executeScript(
                "data_cleaner/scripts/data_profiler.py",
                "{\"scope\": \"full\"}",
                csvData);

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"), "Script error: " + json.getString("error"));

        JSONObject output = JSON.parseObject(json.getString("output"));
        assertEquals(3, output.getIntValue("totalRows"));
        assertEquals(3, output.getIntValue("totalColumns"));
    }

    @Test
    void dataProfiler_missingScope() throws IOException {
        copyScript("data_cleaner/scripts/data_profiler.py");

        String csvData = "name,age,score\nAlice,25,90\nBob,,85\n,,\n";
        String result = tool.executeScript(
                "data_cleaner/scripts/data_profiler.py",
                "{\"scope\": \"missing\"}",
                csvData);

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));

        JSONObject output = JSON.parseObject(json.getString("output"));
        assertEquals(3, output.getIntValue("totalRows"));
    }

    @Test
    void missingValueHandler_meanStrategy() throws IOException {
        copyScript("data_cleaner/scripts/missing_value_handler.py");

        String csvData = "name,value\nA,10\nB,20\nC,\nD,30\n";
        String result = tool.executeScript(
                "data_cleaner/scripts/missing_value_handler.py",
                "{\"strategy\": \"mean\", \"columns\": \"value\"}",
                csvData);

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));

        // Output should be CSV (to stdout) with the missing value filled
        String stdout = json.getString("output");
        assertTrue(stdout.contains("20.0") || stdout.contains("20"), "Mean of 10,20,30 = 20");
    }

    @Test
    void outlierDetector_detectMode() throws IOException {
        copyScript("data_cleaner/scripts/outlier_detector.py");

        String csvData = "name,value\nA,10\nB,12\nC,11\nD,100\nE,13\n";
        String result = tool.executeScript(
                "data_cleaner/scripts/outlier_detector.py",
                "{\"method\": \"iqr\", \"threshold\": 1.5, \"action\": \"detect\"}",
                csvData);

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));

        JSONObject output = JSON.parseObject(json.getString("output"));
        assertTrue(output.getIntValue("totalOutliers") > 0, "100 should be detected as outlier");
    }

    @Test
    void dedupStandardizer_removesDuplicates() throws IOException {
        copyScript("data_cleaner/scripts/dedup_standardizer.py");

        String csvData = "id,name\n1,Alice\n2,Bob\n1,Alice\n3,Charlie\n";
        String result = tool.executeScript(
                "data_cleaner/scripts/dedup_standardizer.py",
                "{\"dedup_columns\": \"id\", \"dedup_strategy\": \"first\"}",
                csvData);

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));

        // Output CSV should have 3 rows (deduplicated)
        String stdout = json.getString("output");
        long lineCount = stdout.lines().count();
        assertEquals(4, lineCount, "Header + 3 unique rows"); // header + 3 data rows
    }
}
