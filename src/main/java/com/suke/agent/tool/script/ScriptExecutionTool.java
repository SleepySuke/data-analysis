/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 脚本执行工具，通过ProcessBuilder执行Python脚本
 */

package com.suke.agent.tool.script;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.suke.config.PythonScriptProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ScriptExecutionTool {

    private final PythonScriptProperties properties;
    private Path workDir;

    public ScriptExecutionTool(PythonScriptProperties properties) {
        this.properties = properties;
    }

    void setWorkDir(Path workDir) {
        this.workDir = workDir;
    }

    @PostConstruct
    void init() throws IOException {
        workDir = Path.of("work", "scripts");
        Files.createDirectories(workDir);

        int count = 0;
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:skills/*/scripts/*.py");

        for (Resource resource : resources) {
            String filename = extractRelativePath(resource);
            if (filename == null) continue;

            Path target = workDir.resolve(filename);
            Files.createDirectories(target.getParent());
            Files.copy(resource.getInputStream(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            count++;
        }

        log.info("Extracted {} Python scripts to {}", count, workDir.toAbsolutePath());
    }

    @Tool(description = "执行指定路径的Python脚本处理数据。用于网页抓取、数据清洗、数据质量分析等操作。")
    public String executeScript(
            @ToolParam(description = "脚本相对路径，如 web_scraper/scripts/web_scraper.py 或 data_cleaner/scripts/data_profiler.py")
            String scriptPath,
            @ToolParam(description = "脚本参数，JSON格式。键名对应脚本参数名(不含--)。例如 {\"url\":\"https://example.com\",\"extract_type\":\"table\"}")
            String arguments,
            @ToolParam(description = "可选的stdin输入数据，如CSV格式数据。不需要时传空字符串")
            String stdinData) {

        if (!properties.isEnabled()) {
            return errorJson("Python script execution is disabled");
        }

        // Security: validate path
        if (scriptPath.contains("..")) {
            return errorJson("Invalid script path: path traversal not allowed");
        }
        if (!scriptPath.endsWith(".py")) {
            return errorJson("Only .py scripts are allowed");
        }

        Path scriptFile = workDir.resolve(scriptPath);
        if (!Files.exists(scriptFile)) {
            return errorJson("Script not found: " + scriptPath);
        }

        // Parse arguments JSON to command-line args
        List<String> command = buildCommand(scriptFile, arguments);

        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(false);

            Process process = pb.start();

            // Write stdin data if provided
            if (stdinData != null && !stdinData.isBlank()) {
                process.getOutputStream().write(stdinData.getBytes());
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(properties.getTimeout(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return errorJson("Script timed out after " + properties.getTimeout() + "s");
            }

            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.exitValue();

            JSONObject result = new JSONObject();
            result.put("exitCode", exitCode);
            result.put("output", stdout);
            result.put("error", stderr);
            result.put("script", scriptPath);
            return result.toJSONString();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return errorJson("Script execution interrupted");
        } catch (Exception e) {
            return errorJson("Execution failed: " + e.getMessage());
        }
    }

    private List<String> buildCommand(Path scriptFile, String arguments) {
        List<String> command = new java.util.ArrayList<>();
        command.add(properties.getExecutable());
        command.add(scriptFile.toString());

        if (arguments != null && !arguments.isBlank()) {
            try {
                JSONObject args = JSON.parseObject(arguments);
                for (Map.Entry<String, Object> entry : args.entrySet()) {
                    String key = entry.getKey().replace("_", "-");
                    Object value = entry.getValue();
                    if (value instanceof Boolean && (Boolean) value) {
                        command.add("--" + key);
                    } else {
                        command.add("--" + key);
                        command.add(String.valueOf(value));
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to parse arguments as JSON, passing as-is: {}", e.getMessage());
            }
        }

        return command;
    }

    private String extractRelativePath(Resource resource) {
        try {
            String uri = resource.getURI().toString();
            int idx = uri.indexOf("skills/");
            if (idx < 0) return null;
            return uri.substring(idx + "skills/".length());
        } catch (IOException e) {
            return null;
        }
    }

    private String errorJson(String message) {
        return JSON.toJSONString(Map.of(
                "exitCode", -1,
                "error", message,
                "output", ""
        ));
    }
}
