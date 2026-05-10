package com.suke.utils;

import com.suke.config.ChartTypeTemplateConfig;
import com.suke.exception.AIDockingException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Field;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AIDockingTest {

    private AIDocking aiDocking;

    @Mock private ChatModel qwenModel;
    @Mock private ChatClient qwenClient;
    @Mock private ChatClient deepseekClient;
    @Mock private ChartTypeTemplateConfig chartTypeTemplateConfig;
    @Mock private PromptBuilder promptBuilder;

    @Mock private ChatClient.ChatClientRequestSpec requestSpec;
    @Mock private ChatClient.CallResponseSpec callResponseSpec;

    @BeforeEach
    void setUp() throws Exception {
        aiDocking = new AIDocking();
        // Inject mocks via reflection
        setField("qwenModel", qwenModel);
        setField("qwenClient", qwenClient);
        setField("deepseekClient", deepseekClient);
        setField("chartTypeTemplateConfig", chartTypeTemplateConfig);
        setField("promptBuilder", promptBuilder);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = AIDocking.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(aiDocking, value);
    }

    private void setupMockChain() {
        when(qwenClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
    }

    // ========== #16: Unsupported chart type ==========

    @Test
    @DisplayName("doDataAnalysis-不支持图表类型应抛异常")
    void doDataAnalysis_unsupportedChartType_shouldThrow() {
        when(chartTypeTemplateConfig.supportsChartType("unknown")).thenReturn(false);

        assertThrows(AIDockingException.class,
                () -> aiDocking.doDataAnalysis("goal", "unknown", "data"));
    }

    // ========== #16: Null/empty AI response ==========

    @Test
    @DisplayName("doDataAnalysis-AI返回null应抛异常")
    void doDataAnalysis_nullResponse_shouldThrow() {
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(promptBuilder.buildPrompt("line")).thenReturn("system prompt");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn(null);

        assertThrows(AIDockingException.class,
                () -> aiDocking.doDataAnalysis("分析趋势", "line", "csv数据"));
    }

    @Test
    @DisplayName("doDataAnalysis-空响应应抛异常")
    void doDataAnalysis_emptyResponse_shouldThrow() {
        when(chartTypeTemplateConfig.supportsChartType("bar")).thenReturn(true);
        when(promptBuilder.buildPrompt("bar")).thenReturn("system prompt");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("   ");

        assertThrows(AIDockingException.class,
                () -> aiDocking.doDataAnalysis("分析", "bar", "data"));
    }

    // ========== #16: Success path ==========

    @Test
    @DisplayName("doDataAnalysis-正常返回分析结果")
    void doDataAnalysis_success_shouldReturnResult() {
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(promptBuilder.buildPrompt("line")).thenReturn("system prompt");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("分析结果");

        String result = aiDocking.doDataAnalysis("分析趋势", "line", "csv数据");
        assertEquals("分析结果", result);
    }

    // ========== #16: system and user message separation ==========

    @Test
    @DisplayName("doDataAnalysis-应使用system和user分离调用")
    void doDataAnalysis_shouldSeparateSystemAndUserMessage() {
        when(chartTypeTemplateConfig.supportsChartType("pie")).thenReturn(true);
        when(promptBuilder.buildPrompt("pie")).thenReturn("系统提示词模板");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("结果");

        aiDocking.doDataAnalysis("分析目标", "pie", "csv数据");

        verify(requestSpec).system(anyString());
        verify(requestSpec).user(anyString());
    }

    @Test
    @DisplayName("doDataAnalysis-system prompt不应包含用户CSV数据")
    void doDataAnalysis_systemPromptShouldNotContainCsvData() {
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(promptBuilder.buildPrompt("line")).thenReturn("纯系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("结果");

        aiDocking.doDataAnalysis("分析目标", "line", "date,sales\n1,100");

        ArgumentCaptor<String> systemCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).system(systemCaptor.capture());

        String systemArg = systemCaptor.getValue();
        assertFalse(systemArg.contains("date,sales"), "system prompt should not contain CSV data");
        assertFalse(systemArg.contains("1,100"), "system prompt should not contain CSV data");
        assertFalse(systemArg.contains("分析目标"), "system prompt should not contain user goal");
    }
}
