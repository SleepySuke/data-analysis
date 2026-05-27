package com.suke.agent.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.suke.agent.core.AgentDescriptor;
import com.suke.agent.core.AgentRegistry;
import com.suke.agent.prompt.AgentPrompts;
import com.suke.agent.specialized.DataAnalystFactory;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class AgentConfig {

    private final AgentRegistry agentRegistry;
    private final DataAnalystFactory dataAnalystFactory;

    public AgentConfig(AgentRegistry agentRegistry, DataAnalystFactory dataAnalystFactory) {
        this.agentRegistry = agentRegistry;
        this.dataAnalystFactory = dataAnalystFactory;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing Agent Registry...");

        // 注册 DataAnalyst
        ReactAgent dataAnalystAgent = dataAnalystFactory.build();
        List<ToolCallback> dataAnalystTools = dataAnalystFactory.getTools();

        agentRegistry.register(AgentDescriptor.builder()
                .name("data_analyst")
                .description("数据分析师：分析CSV数据，生成分析结论和ECharts图表")
                .prompt(AgentPrompts.DATA_ANALYST)
                .tools(dataAnalystTools)
                .handoffs(List.of("data_cleaner"))
                .agent(dataAnalystAgent)
                .build());

        // 其他Agent占位（Plan 10实现）
        // web_scraper, sql_analyst, data_cleaner 将在专项Agent计划中注册

        log.info("Agent Registry initialized with {} agents", agentRegistry.allAgentNames().size());
    }
}
