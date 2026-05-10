package com.suke.utils;

import com.suke.config.ChartTypeTemplateConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() throws Exception {
        promptBuilder = new PromptBuilder();
        ChartTypeTemplateConfig config = new ChartTypeTemplateConfig();
        config.init();
        // Use reflection since @Autowired field is private
        Field field = PromptBuilder.class.getDeclaredField("chartTypeTemplateConfig");
        field.setAccessible(true);
        field.set(promptBuilder, config);
    }

    // ========== #17: Prompt should not contain contradictory instructions ==========

    @Test
    @DisplayName("buildPrompt不应包含'自动选择'的矛盾指令")
    void buildPrompt_shouldNotContainAutoSelectInstruction() {
        String prompt = promptBuilder.buildPrompt("line");

        assertFalse(prompt.contains("自动选择"),
                "Prompt should not contain '自动选择' - contradicts '必须使用用户指定的图表类型'");
    }

    @Test
    @DisplayName("buildPrompt应包含强制使用指定图表类型的指令")
    void buildPrompt_shouldContainForcedChartTypeInstruction() {
        String prompt = promptBuilder.buildPrompt("bar");

        assertTrue(prompt.contains("必须使用用户指定的图表类型"),
                "Prompt should contain '必须使用用户指定的图表类型' instruction");
        assertTrue(prompt.contains("bar"),
                "Prompt should contain the specified chart type 'bar'");
    }

    @Test
    @DisplayName("buildEnhancedPrompt不应包含'自动选择'的矛盾指令")
    void buildEnhancedPrompt_shouldNotContainAutoSelectInstruction() {
        String prompt = promptBuilder.buildEnhancedPrompt(
                "分析趋势", "知识内容", "csv数据", "pie");

        assertFalse(prompt.contains("自动选择"),
                "Enhanced prompt should not contain '自动选择'");
    }

    @Test
    @DisplayName("buildEnhancedPrompt应包含强制图表类型")
    void buildEnhancedPrompt_shouldContainForcedChartType() {
        String prompt = promptBuilder.buildEnhancedPrompt(
                "分析趋势", "知识内容", "csv数据", "scatter");

        assertTrue(prompt.contains("必须使用用户指定的图表类型"),
                "Enhanced prompt should contain forced chart type instruction");
        assertTrue(prompt.contains("scatter"),
                "Enhanced prompt should contain chart type 'scatter'");
    }

    // ========== Additional coverage: template embedding and input propagation ==========

    @Test
    @DisplayName("buildPrompt应包含图表模板JSON内容")
    void buildPrompt_shouldContainChartTemplate() {
        String prompt = promptBuilder.buildPrompt("bar");

        // Template should contain ECharts structure keys
        assertTrue(prompt.contains("xAxis"), "Prompt should contain xAxis from template");
        assertTrue(prompt.contains("series"), "Prompt should contain series from template");
    }

    @Test
    @DisplayName("buildEnhancedPrompt应传播goal、knowledge、csvData")
    void buildEnhancedPrompt_shouldPropagateAllInputs() {
        String prompt = promptBuilder.buildEnhancedPrompt(
                "我的分析目标", "知识库检索内容", "日期,销售额\n1月,100", "line");

        assertTrue(prompt.contains("我的分析目标"), "Should contain goal");
        assertTrue(prompt.contains("知识库检索内容"), "Should contain knowledge result");
        assertTrue(prompt.contains("日期,销售额"), "Should contain CSV data");
    }

    @Test
    @DisplayName("buildPrompt所有7种图表类型都应正常工作")
    void buildPrompt_allChartTypes_shouldWork() {
        String[] types = {"line", "bar", "pie", "radar", "scatter", "area", "gauge"};
        for (String type : types) {
            String prompt = promptBuilder.buildPrompt(type);
            assertNotNull(prompt, "Prompt for " + type + " should not be null");
            assertTrue(prompt.contains("必须使用用户指定的图表类型"),
                    type + " prompt should contain forced chart type");
            assertTrue(prompt.contains(type),
                    type + " prompt should contain chart type name");
        }
    }
}
