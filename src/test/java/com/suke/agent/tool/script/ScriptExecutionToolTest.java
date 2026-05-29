package com.suke.agent.tool.script;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.config.PythonScriptProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScriptExecutionToolTest {

    private ScriptExecutionTool tool;
    private PythonScriptProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        properties = new PythonScriptProperties();
        properties.setEnabled(true);
        properties.setTimeout(10);
        properties.setExecutable("python3");

        tool = new ScriptExecutionTool(properties);
        // Set workDir for testing (bypass @PostConstruct)
        tool.setWorkDir(tempDir);
        // Create a test script
        String testScript = """
                import sys
                import json
                print(json.dumps({"status": "ok", "args": sys.argv[1:]}))
                """;
        Path scriptFile = tempDir.resolve("test_script.py");
        Files.writeString(scriptFile, testScript);
    }

    @Test
    void executeScript_success() {
        String result = tool.executeScript("test_script.py", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));
        assertTrue(json.getString("output").contains("ok"));
    }

    @Test
    void executeScript_withArguments() {
        String result = tool.executeScript("test_script.py",
                "{\"key1\": \"value1\", \"key2\": 42}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));
        String output = json.getString("output");
        assertTrue(output.contains("--key1"));
        assertTrue(output.contains("value1"));
        assertTrue(output.contains("--key2"));
        assertTrue(output.contains("42"));
    }

    @Test
    void executeScript_withStdinData() throws IOException {
        Path echoScript = tempDir.resolve("echo_stdin.py");
        Files.writeString(echoScript, """
                import sys
                data = sys.stdin.read()
                print(f"stdin: {data}")
                """);

        String result = tool.executeScript("echo_stdin.py", "{}", "hello world");

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));
        assertTrue(json.getString("output").contains("stdin: hello world"));
    }

    @Test
    void executeScript_pathTraversal_rejected() {
        String result = tool.executeScript("../etc/passwd", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(-1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("path traversal"));
    }

    @Test
    void executeScript_nonPyFile_rejected() {
        String result = tool.executeScript("malicious.sh", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(-1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("Only .py"));
    }

    @Test
    void executeScript_scriptNotFound() {
        String result = tool.executeScript("nonexistent.py", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(-1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("not found"));
    }

    @Test
    void executeScript_disabled() {
        properties.setEnabled(false);

        String result = tool.executeScript("test_script.py", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(-1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("disabled"));
    }

    @Test
    void executeScript_timeout() throws IOException {
        Path slowScript = tempDir.resolve("slow_script.py");
        Files.writeString(slowScript, """
                import time
                time.sleep(60)
                """);

        properties.setTimeout(1);

        String result = tool.executeScript("slow_script.py", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(-1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("timed out"));
    }

    @Test
    void executeScript_scriptError() throws IOException {
        Path errorScript = tempDir.resolve("error_script.py");
        Files.writeString(errorScript, """
                import sys
                print("error message", file=sys.stderr)
                sys.exit(1)
                """);

        String result = tool.executeScript("error_script.py", "{}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(1, json.getIntValue("exitCode"));
        assertTrue(json.getString("error").contains("error message"));
    }

    @Test
    void executeScript_booleanArgument_flagStyle() {
        String result = tool.executeScript("test_script.py",
                "{\"verbose\": true}", "");

        JSONObject json = JSON.parseObject(result);
        assertEquals(0, json.getIntValue("exitCode"));
        // Boolean true should be a flag (--verbose without value)
        String output = json.getString("output");
        assertTrue(output.contains("--verbose"));
    }
}
