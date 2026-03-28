package com.suke.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author 自然醒
 * @version 1.0
 */

//AI对接的模型接口
@Configuration
@Slf4j
public class SaaLLMConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    private static final String QWEN_MODEL = "qwen-plus-2025-12-01";
    private static final String EMBEDDING_MODEL = "text-embedding-v2";
    private static final String DEEPSEEK_MODEL = "deepseek-v3.2";

//    @Value("${spring.ai.dashscope.base-url}")
//    private String baseUrl;
    @Bean(name = "deepseek")
    public ChatModel deepseekModel(){
        log.info("ApiKey -> {}", apiKey);
        return DashScopeChatModel.builder()
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                .defaultOptions(DashScopeChatOptions.builder().withModel(DEEPSEEK_MODEL).build())
                .build();
    }

    @Bean(name = "deepseekClient")
    public ChatClient deepseekClient(@Qualifier("deepseek") ChatModel deepseekModel){
        return ChatClient.builder(deepseekModel).build();
    }

    @Bean(name = "qwen")
    public ChatModel qwenModel(){
       // log.info("baseUrl ->{}",baseUrl);
        //使用DashScopeChatModel创建模型
        return DashScopeChatModel.builder()
                //配置DashScopeApi 构建DashScopeApi
                .dashScopeApi(DashScopeApi.builder().apiKey(apiKey).build())
                //配置模型名称
                .defaultOptions(DashScopeChatOptions.builder().withModel(QWEN_MODEL).build())
                .build();
    }

    @Bean(name = "qwenChatClient")
    public ChatClient qwenChatClient(@Qualifier("qwen") ChatModel qwenModel){
        return ChatClient.builder(qwenModel).build();
    }

//    @Bean(name = "react")
//    public ReactAgent reactAgent(@Qualifier("qwen") ChatModel qwenModel){
//        return ReactAgent.builder()
//                .model(qwenModel)
//                .build();
//    }

    @Bean(name = "embeddingModel")
    public EmbeddingModel embeddingModel(){
        log.info("初始化 DashScope 嵌入模型，模型: {}", EMBEDDING_MODEL);
        return new DashScopeEmbeddingModel(
                DashScopeApi.builder().apiKey(apiKey).build()
        );
    }
}
