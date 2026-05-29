/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 工具配置类，注册Agent可调用的工具Bean
 */

package com.suke.agent.config;

import com.suke.agent.tool.analysis.ChartGenerationTool;
import com.suke.agent.tool.analysis.CsvAnalysisTool;
import com.suke.agent.tool.analysis.KnowledgeSearchToolAdapter;
import com.suke.agent.tool.analysis.StatisticalAnalysisTool;
import com.suke.agent.tool.script.ScriptExecutionTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class ToolConfig {

    @Bean("dataAnalystTools")
    public List<ToolCallback> dataAnalystTools(
            CsvAnalysisTool csvAnalysisTool,
            ChartGenerationTool chartGenerationTool,
            StatisticalAnalysisTool statisticalAnalysisTool,
            @Nullable KnowledgeSearchToolAdapter knowledgeSearchToolAdapter,
            ScriptExecutionTool scriptExecutionTool) {
        List<Object> tools = new ArrayList<>(List.of(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                scriptExecutionTool
        ));
        if (knowledgeSearchToolAdapter != null) {
            tools.add(knowledgeSearchToolAdapter);
        }
        return List.of(ToolCallbacks.from(tools.toArray()));
    }
}
