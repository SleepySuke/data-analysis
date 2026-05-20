package com.suke.utils;

import com.suke.config.ChartTypeTemplateConfig;
import com.suke.exception.AIDockingException;
import com.suke.tool.KnowledgeSearchTool;
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
    @Mock private KnowledgeSearchTool knowledgeSearchTool;

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
        setField("knowledgeSearchTool", knowledgeSearchTool);
        setField("ragEnabled", false);
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

    // ========== RAG 集成测试 ==========

    @Test
    @DisplayName("RAG-关闭时应使用标准prompt，不调用知识检索")
    void doDataAnalysis_ragDisabled_shouldUseStandardPrompt() throws Exception {
        setField("ragEnabled", false);
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(promptBuilder.buildPrompt("line")).thenReturn("标准系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("分析结果");

        aiDocking.doDataAnalysis("分析趋势", "line", "csv数据");

        verify(promptBuilder).buildPrompt("line");
        verify(promptBuilder, never()).buildEnhancedPrompt(anyString(), anyString(), anyString(), anyString());
        verify(knowledgeSearchTool, never()).searchKnowledge(anyString());
    }

    @Test
    @DisplayName("RAG-开启且检索成功时应使用增强prompt")
    void doDataAnalysis_ragEnabled_withKnowledge_shouldUseEnhancedPrompt() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析趋势")).thenReturn("相关知识内容：金融趋势分析...");
        when(promptBuilder.buildEnhancedPrompt("分析趋势", "相关知识内容：金融趋势分析...", "csv数据", "line"))
                .thenReturn("增强系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("增强分析结果");

        String result = aiDocking.doDataAnalysis("分析趋势", "line", "csv数据");

        assertEquals("增强分析结果", result);
        verify(promptBuilder).buildEnhancedPrompt("分析趋势", "相关知识内容：金融趋势分析...", "csv数据", "line");
        verify(promptBuilder, never()).buildPrompt(anyString());
    }

    @Test
    @DisplayName("RAG-检索无结果时应回退到标准prompt")
    void doDataAnalysis_ragEnabled_emptyKnowledge_shouldFallbackToStandard() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("bar")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析目标")).thenReturn("未找到与 '分析目标' 相关的知识内容。");
        when(promptBuilder.buildPrompt("bar")).thenReturn("标准系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("标准分析结果");

        String result = aiDocking.doDataAnalysis("分析目标", "bar", "csv数据");

        assertEquals("标准分析结果", result);
        verify(promptBuilder).buildPrompt("bar");
        verify(promptBuilder, never()).buildEnhancedPrompt(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("RAG-检索异常时应回退到标准prompt，不抛异常")
    void doDataAnalysis_ragEnabled_searchException_shouldFallbackToStandard() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("pie")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析")).thenThrow(new RuntimeException("Redis连接失败"));
        when(promptBuilder.buildPrompt("pie")).thenReturn("标准系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("标准分析结果");

        String result = aiDocking.doDataAnalysis("分析", "pie", "csv数据");

        assertEquals("标准分析结果", result);
        verify(promptBuilder).buildPrompt("pie");
        verify(promptBuilder, never()).buildEnhancedPrompt(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("RAG-增强模式下userMessage不应包含CSV数据")
    void doDataAnalysis_ragEnabled_withKnowledge_userMessageShouldBeSimple() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析趋势")).thenReturn("知识内容");
        when(promptBuilder.buildEnhancedPrompt("分析趋势", "知识内容", "date,sales\n1,100", "line"))
                .thenReturn("增强系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("结果");

        aiDocking.doDataAnalysis("分析趋势", "line", "date,sales\n1,100");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userCaptor.capture());

        String userArg = userCaptor.getValue();
        assertFalse(userArg.contains("date,sales"), "增强模式下 userMessage 不应包含 CSV 数据");
        assertFalse(userArg.contains("1,100"), "增强模式下 userMessage 不应包含 CSV 数据");
    }

    @Test
    @DisplayName("RAG-检索返回搜索失败前缀时应回退到标准prompt")
    void doDataAnalysis_ragEnabled_searchFailedPrefix_shouldFallbackToStandard() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("bar")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析")).thenReturn("知识库搜索失败: Redis连接超时");
        when(promptBuilder.buildPrompt("bar")).thenReturn("标准系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("标准分析结果");

        String result = aiDocking.doDataAnalysis("分析", "bar", "csv数据");

        assertEquals("标准分析结果", result);
        verify(promptBuilder).buildPrompt("bar");
        verify(promptBuilder, never()).buildEnhancedPrompt(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("RAG-检索返回null时应回退到标准prompt")
    void doDataAnalysis_ragEnabled_nullResult_shouldFallbackToStandard() throws Exception {
        setField("ragEnabled", true);
        when(chartTypeTemplateConfig.supportsChartType("pie")).thenReturn(true);
        when(knowledgeSearchTool.searchKnowledge("分析")).thenReturn(null);
        when(promptBuilder.buildPrompt("pie")).thenReturn("标准系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("标准分析结果");

        String result = aiDocking.doDataAnalysis("分析", "pie", "csv数据");

        assertEquals("标准分析结果", result);
        verify(promptBuilder).buildPrompt("pie");
        verify(promptBuilder, never()).buildEnhancedPrompt(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("buildUserMessage-chartType为null时不应包含图表类型行")
    void buildUserMessage_nullChartType_shouldOmitChartTypeLine() throws Exception {
        setField("ragEnabled", false);
        when(chartTypeTemplateConfig.supportsChartType("line")).thenReturn(true);
        when(promptBuilder.buildPrompt("line")).thenReturn("系统提示词");
        setupMockChain();
        when(callResponseSpec.content()).thenReturn("结果");

        aiDocking.doDataAnalysis("分析目标", "line", "csv数据");

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        verify(requestSpec).user(userCaptor.capture());
        String userArg = userCaptor.getValue();
        assertTrue(userArg.contains("图表类型：line"), "chartType 非空时应包含图表类型行");
    }
}
