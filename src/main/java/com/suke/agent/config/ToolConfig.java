package com.suke.agent.config;

import com.suke.agent.tool.analysis.ChartGenerationTool;
import com.suke.agent.tool.analysis.CsvAnalysisTool;
import com.suke.agent.tool.analysis.KnowledgeSearchToolAdapter;
import com.suke.agent.tool.analysis.StatisticalAnalysisTool;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ToolConfig {

    @Bean("dataAnalystTools")
    public List<ToolCallback> dataAnalystTools(
            CsvAnalysisTool csvAnalysisTool,
            ChartGenerationTool chartGenerationTool,
            StatisticalAnalysisTool statisticalAnalysisTool,
            KnowledgeSearchToolAdapter knowledgeSearchToolAdapter) {
        return List.of(ToolCallbacks.from(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                knowledgeSearchToolAdapter
        ));
    }
}
