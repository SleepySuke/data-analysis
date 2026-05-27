package com.suke.agent.tool.analysis;

import com.suke.config.ChartTypeTemplateConfig;
import com.suke.utils.ParseAIResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class ChartGenerationTool {

    private final ChatClient chatClient;
    private final ChartTypeTemplateConfig templateConfig;

    public ChartGenerationTool(
            @Qualifier("qwenChatClient") ChatClient chatClient,
            ChartTypeTemplateConfig templateConfig) {
        this.chatClient = chatClient;
        this.templateConfig = templateConfig;
    }

    @Tool(description = "根据分析结果和图表类型生成ECharts图表配置JSON，支持line/bar/pie/radar/scatter/area/gauge")
    public String generateChart(
            @ToolParam(description = "图表类型：line/bar/pie/radar/scatter/area/gauge") String chartType,
            @ToolParam(description = "数据分析结果的摘要") String analysisResult,
            @ToolParam(description = "原始CSV数据") String csvData) {

        if (!templateConfig.supportsChartType(chartType)) {
            return "错误：不支持的图表类型: " + chartType + "，支持: line,bar,pie,radar,scatter,area,gauge";
        }

        String template = templateConfig.getTemplate(chartType);

        String prompt = String.format("""
                请根据以下分析结果和原始数据，生成一个完整的ECharts图表配置JSON。

                图表类型：%s
                图表模板：
                %s

                分析结果：
                %s

                原始数据：
                %s

                要求：
                1. 必须使用 %s 图表类型
                2. 生成完整可用的ECharts option JSON
                3. 使用双引号，无注释，标准JSON格式
                4. 只输出JSON，不要其他内容
                """, chartType, template, analysisResult, csvData, chartType);

        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                return createDefaultChartJson(chartType);
            }

            // 尝试提取JSON
            String chartJson = extractJson(response);
            if (chartJson == null) {
                return createDefaultChartJson(chartType);
            }

            return chartJson;

        } catch (Exception e) {
            return createDefaultChartJson(chartType);
        }
    }

    private String extractJson(String response) {
        // 尝试从markdown代码块中提取
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "```json\\s*(.*?)\\s*```", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 尝试直接匹配JSON对象
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }

        return null;
    }

    private String createDefaultChartJson(String chartType) {
        try {
            return templateConfig.getTemplate(chartType).replace("%s", "数据分析图表");
        } catch (Exception e) {
            return "{\"title\":{\"text\":\"数据分析图表\"},\"xAxis\":{\"type\":\"category\",\"data\":[]},\"yAxis\":{\"type\":\"value\"},\"series\":[{\"type\":\"bar\",\"data\":[]}]}";
        }
    }
}
