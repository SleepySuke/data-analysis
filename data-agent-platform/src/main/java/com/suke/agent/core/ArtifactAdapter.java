/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 产物适配器，解析Agent输出为前端可展示的Artifact列表
 */

package com.suke.agent.core;

import com.alibaba.fastjson2.JSON;
import com.suke.agent.domain.vo.ArtifactVO;
import com.suke.common.AnalysisResult;
import com.suke.utils.ParseAIResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ArtifactAdapter {

    private static final AtomicInteger idCounter = new AtomicInteger(0);

    public static ParseResult parse(String agentOutput, String agentName) {
        if (agentOutput == null || agentOutput.isBlank()) {
            return new ParseResult("", Collections.emptyList());
        }

        List<ArtifactVO> artifacts = new ArrayList<>();
        String content;

        // 复用现有 ParseAIResponse 提取分析结论 + 图表配置
        AnalysisResult result = ParseAIResponse.parseResponse(agentOutput);

        if (result.getChartConfig() != null && !result.getChartConfig().isBlank()) {
            try {
                Object chartOption = JSON.parse(result.getChartConfig());
                artifacts.add(new ArtifactVO(
                        nextId("chart"), "chart", "数据分析图表",
                        Map.of("chartOption", chartOption, "chartType", inferChartType(result.getChartConfig()))
                ));
            } catch (Exception e) {
                log.warn("图表配置解析失败: {}", e.getMessage());
            }
        }

        content = result.getAnalysis();
        if (content == null || content.isBlank()) {
            content = agentOutput;
        }

        return new ParseResult(content, artifacts);
    }

    private static String inferChartType(String json) {
        if (json.contains("\"type\":\"pie\"") || json.contains("\"type\": \"pie\"")) return "pie";
        if (json.contains("\"type\":\"bar\"") || json.contains("\"type\": \"bar\"")) return "bar";
        if (json.contains("\"type\":\"scatter\"") || json.contains("\"type\": \"scatter\"")) return "scatter";
        if (json.contains("\"type\":\"radar\"") || json.contains("\"type\": \"radar\"")) return "radar";
        return "line";
    }

    private static String nextId(String prefix) {
        return prefix + "-" + System.currentTimeMillis() + "-" + idCounter.incrementAndGet();
    }

    public record ParseResult(String content, List<ArtifactVO> artifacts) {}
}
