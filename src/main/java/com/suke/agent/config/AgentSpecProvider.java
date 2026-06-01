/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-31
 * @description Agent规格定义集中管理，所有AgentSpec在此声明为Spring Bean
 */

package com.suke.agent.config;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.checkpoint.BaseCheckpointSaver;
import com.suke.agent.core.AgentSpec;
import com.suke.agent.hook.ProfileFirstHook;
import com.suke.agent.hook.SchemaFirstHook;
import com.suke.agent.prompt.AgentPrompts;
import com.suke.agent.specialized.WebScraperAgent;
import com.suke.agent.tool.analysis.ChartGenerationTool;
import com.suke.agent.tool.analysis.CsvAnalysisTool;
import com.suke.agent.tool.analysis.KnowledgeSearchToolAdapter;
import com.suke.agent.tool.analysis.StatisticalAnalysisTool;
import com.suke.agent.tool.cleaning.*;
import com.suke.agent.tool.script.ScriptExecutionTool;
import com.suke.agent.tool.scraping.ContentExtractorTool;
import com.suke.agent.tool.scraping.KnowledgeIngestTool;
import com.suke.agent.tool.scraping.UrlFetchTool;
import com.suke.agent.tool.sql.ResultInterpreterTool;
import com.suke.agent.tool.sql.SchemaIntrospectTool;
import com.suke.agent.tool.sql.SqlExecutionTool;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class AgentSpecProvider {

    @Bean
    public AgentSpec dataAnalystSpec(
            @Qualifier("qwen") ChatModel chatModel,
            CsvAnalysisTool csvAnalysisTool,
            ChartGenerationTool chartGenerationTool,
            StatisticalAnalysisTool statisticalAnalysisTool,
            @Nullable KnowledgeSearchToolAdapter knowledgeSearchToolAdapter,
            ScriptExecutionTool scriptExecutionTool) {
        List<Object> tools = new ArrayList<>(List.of(
                csvAnalysisTool, chartGenerationTool,
                statisticalAnalysisTool, scriptExecutionTool));
        if (knowledgeSearchToolAdapter != null) {
            tools.add(knowledgeSearchToolAdapter);
        }
        return AgentSpec.react("data_analyst",
                "数据分析师：分析CSV数据，生成分析结论和ECharts图表",
                AgentPrompts.DATA_ANALYST,
                List.of("data_cleaner"),
                tools,
                ToolCallLimitHook.builder().runLimit(10).build(),
                ModelCallLimitHook.builder().runLimit(5).build());
    }

    @Bean
    public AgentSpec webScraperSpec(
            @Qualifier("qwen") ChatModel chatModel,
            BaseCheckpointSaver saver,
            UrlFetchTool urlFetchTool,
            ContentExtractorTool contentExtractorTool,
            @Nullable KnowledgeIngestTool knowledgeIngestTool) {
        return AgentSpec.custom("web_scraper",
                "网页采集专家：抓取网页数据，补充知识库",
                List.of("data_analyst", "data_cleaner"),
                () -> new WebScraperAgent("web_scraper",
                        "网页采集专家：抓取网页数据，补充知识库",
                        chatModel, urlFetchTool, contentExtractorTool,
                        knowledgeIngestTool, saver));
    }

    @Bean
    public AgentSpec sqlAnalystSpec(
            SchemaIntrospectTool schemaIntrospectTool,
            SqlExecutionTool sqlExecutionTool,
            ResultInterpreterTool resultInterpreterTool) {
        return AgentSpec.react("sql_analyst",
                "SQL分析专家：查询数据库，分析结构化数据",
                AgentPrompts.SQL_ANALYST,
                List.of("data_analyst"),
                List.of(schemaIntrospectTool, sqlExecutionTool, resultInterpreterTool),
                new SchemaFirstHook(),
                ToolCallLimitHook.builder().runLimit(6).build(),
                ModelCallLimitHook.builder().runLimit(4).build());
    }

    @Bean
    public AgentSpec dataCleanerSpec(
            DataProfilingTool dataProfilingTool,
            MissingValueTool missingValueTool,
            OutlierDetectionTool outlierDetectionTool,
            DataTransformTool dataTransformTool,
            DeduplicationTool deduplicationTool,
            ScriptExecutionTool scriptExecutionTool) {
        return AgentSpec.react("data_cleaner",
                "数据清洗专家：处理数据质量问题",
                AgentPrompts.DATA_CLEANER,
                List.of("data_analyst"),
                List.of(dataProfilingTool, missingValueTool, outlierDetectionTool,
                        dataTransformTool, deduplicationTool, scriptExecutionTool),
                new ProfileFirstHook(),
                ToolCallLimitHook.builder().runLimit(8).build(),
                ModelCallLimitHook.builder().runLimit(5).build());
    }
}
