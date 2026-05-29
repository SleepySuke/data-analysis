/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 数据分析师Agent工厂，构建ReactAgent实例
 */

package com.suke.agent.specialized;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.suke.agent.prompt.AgentPrompts;
import com.suke.agent.tool.analysis.ChartGenerationTool;
import com.suke.agent.tool.analysis.CsvAnalysisTool;
import com.suke.agent.tool.analysis.KnowledgeSearchToolAdapter;
import com.suke.agent.tool.analysis.StatisticalAnalysisTool;
import com.suke.agent.tool.script.ScriptExecutionTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class DataAnalystFactory {

    private final ChatModel chatModel;
    private final CsvAnalysisTool csvAnalysisTool;
    private final ChartGenerationTool chartGenerationTool;
    private final StatisticalAnalysisTool statisticalAnalysisTool;
    private final KnowledgeSearchToolAdapter knowledgeSearchToolAdapter;
    private final ScriptExecutionTool scriptExecutionTool;
    private final BaseCheckpointSaver checkpointSaver;

    public DataAnalystFactory(
            @org.springframework.beans.factory.annotation.Qualifier("qwen") ChatModel chatModel,
            CsvAnalysisTool csvAnalysisTool,
            ChartGenerationTool chartGenerationTool,
            StatisticalAnalysisTool statisticalAnalysisTool,
            @Nullable KnowledgeSearchToolAdapter knowledgeSearchToolAdapter,
            ScriptExecutionTool scriptExecutionTool,
            BaseCheckpointSaver checkpointSaver) {
        this.chatModel = chatModel;
        this.csvAnalysisTool = csvAnalysisTool;
        this.chartGenerationTool = chartGenerationTool;
        this.statisticalAnalysisTool = statisticalAnalysisTool;
        this.knowledgeSearchToolAdapter = knowledgeSearchToolAdapter;
        this.scriptExecutionTool = scriptExecutionTool;
        this.checkpointSaver = checkpointSaver;
    }

    public ReactAgent build() {
        List<Object> toolList = new java.util.ArrayList<>(List.of(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                scriptExecutionTool
        ));
        if (knowledgeSearchToolAdapter != null) {
            toolList.add(knowledgeSearchToolAdapter);
        }
        ToolCallback[] tools = ToolCallbacks.from(toolList.toArray());

        return ReactAgent.builder()
                .name("data_analyst")
                .description("数据分析师：分析CSV数据，生成分析结论和ECharts图表")
                .model(chatModel)
                .instruction(AgentPrompts.DATA_ANALYST)
                .tools(List.of(tools))
                .saver(checkpointSaver)
                .build();
    }

    public List<ToolCallback> getTools() {
        List<Object> toolList = new java.util.ArrayList<>(List.of(
                csvAnalysisTool,
                chartGenerationTool,
                statisticalAnalysisTool,
                scriptExecutionTool
        ));
        if (knowledgeSearchToolAdapter != null) {
            toolList.add(knowledgeSearchToolAdapter);
        }
        return List.of(ToolCallbacks.from(toolList.toArray()));
    }
}
