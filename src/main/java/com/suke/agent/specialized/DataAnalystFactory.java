package com.suke.agent.specialized;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.suke.agent.prompt.AgentPrompts;
import com.suke.agent.tool.analysis.ChartGenerationTool;
import com.suke.agent.tool.analysis.CsvAnalysisTool;
import com.suke.agent.tool.analysis.KnowledgeSearchToolAdapter;
import com.suke.agent.tool.analysis.StatisticalAnalysisTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataAnalystFactory {

    private final ChatModel chatModel;
    private final CsvAnalysisTool csvAnalysisTool;
    private final ChartGenerationTool chartGenerationTool;
    private final StatisticalAnalysisTool statisticalAnalysisTool;
    private final KnowledgeSearchToolAdapter knowledgeSearchToolAdapter;

    public DataAnalystFactory(
            @org.springframework.beans.factory.annotation.Qualifier("qwen") ChatModel chatModel,
            CsvAnalysisTool csvAnalysisTool,
            ChartGenerationTool chartGenerationTool,
            StatisticalAnalysisTool statisticalAnalysisTool,
            KnowledgeSearchToolAdapter knowledgeSearchToolAdapter) {
        this.chatModel = chatModel;
        this.csvAnalysisTool = csvAnalysisTool;
        this.chartGenerationTool = chartGenerationTool;
        this.statisticalAnalysisTool = statisticalAnalysisTool;
        this.knowledgeSearchToolAdapter = knowledgeSearchToolAdapter;
    }

    public ReactAgent build() {
        ToolCallback[] tools = ToolCallbacks.from(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                knowledgeSearchToolAdapter
        );

        return ReactAgent.builder()
                .name("data_analyst")
                .description("数据分析师：分析CSV数据，生成分析结论和ECharts图表")
                .model(chatModel)
                .instruction(AgentPrompts.DATA_ANALYST)
                .tools(List.of(tools))
                .build();
    }

    public List<ToolCallback> getTools() {
        return List.of(ToolCallbacks.from(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                knowledgeSearchToolAdapter
        ));
    }
}
