package com.suke.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class SaaLLMConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    @Value("${ai.model.qwen:qwen-plus}")
    private String qwenModel;
    @Value("${ai.model.embedding:text-embedding-v3}")
    private String embeddingModel;
    @Value("${ai.model.deepseek:deepseek-v4-flash}")
    private String deepseekModel;

    @Bean
    public DashScopeApi dashScopeApi() {
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    @Bean(name = "deepseek")
    public ChatModel deepseekModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().withModel(deepseekModel).build())
                .build();
    }

    @Bean(name = "deepseekClient")
    public ChatClient deepseekClient(@Qualifier("deepseek") ChatModel deepseekModel) {
        return ChatClient.builder(deepseekModel).build();
    }

    @Bean(name = "qwen")
    public ChatModel qwenModel(DashScopeApi dashScopeApi) {
        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .defaultOptions(DashScopeChatOptions.builder().withModel(qwenModel).build())
                .build();
    }

    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(@Qualifier("qwen") ChatModel qwenModel) {
        return ChatClient.builder(qwenModel).build();
    }

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        log.info("初始化 DashScope 嵌入模型，模型: {}", embeddingModel);
        DashScopeEmbeddingOptions options = DashScopeEmbeddingOptions.builder()
                .withModel(embeddingModel).build();
        return new DashScopeEmbeddingModel(dashScopeApi,
                org.springframework.ai.document.MetadataMode.ALL, options);
    }
}
