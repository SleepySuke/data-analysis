package com.suke.config;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
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
public class SaaLLMConfig {
    @Value("${spring.ai.dashscope.api-key}")
    private String apiKey;
    private static final String QWEN_MODEL = "qwen-plus";

    @Bean(name = "qwen")
    public ChatModel qwenModel(){
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
}
