package com.suke.utils;

import com.suke.config.ChartTypeTemplateConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * @author 自然醒
 * @version 1.0
 */
//提示词构建器
@Component
public class PromptBuilder {
    @Autowired
    private ChartTypeTemplateConfig chartTypeTemplateConfig;

    // 数据分析师提示词
    private static final String DATA_ANALYST_PROMPT = """
        你是一个专业的数据分析师和前端ECharts专家。请根据用户选择的图表类型和数据分析需求，生成相应的分析结论和ECharts配置，必须严格按照以下格式响应：

        【数据分析结论】
        {详细的数据分析结论，包含趋势分析、特征总结、洞察发现等}

        【可视化图表代码】
        ```json
        %s
        ```

        要求：
        1. 数据分析结论要专业、详细，包含数值分析和趋势判断
        2. 可视化图表代码必须是完整的ECharts配置JSON
        3. 根据数据类型自动选择最合适的图表类型（折线图、柱状图、饼图等）
        4. 确保JSON格式正确，可以直接被前端ECharts使用
        特别注意：
        5. 必须使用用户指定的图表类型：%s
        6. 图表配置JSON必须使用双引号，不能有注释，确保是标准的JSON格式
        7. 图表配置要包含title、xAxis、yAxis、series等必要组件
        8. 不要在任何地方使用单引号，全部使用双引号

        现在请分析以下数据：
        """;

    // 知识库增强的数据分析师提示词
    private static final String ENHANCED_DATA_ANALYST_PROMPT = """
        你是一个专业的数据分析师和前端ECharts专家。请根据用户的分析需求、相关知识库内容和原始数据，生成专业的分析结论和ECharts配置。

        === 用户分析目标 ===
        %s

        === 相关知识库内容 ===
        %s

        === 原始数据 ===
        %s

        === 输出要求 ===
        必须严格按照以下格式响应：

        【数据分析结论】
        {结合知识库内容和数据分析，给出专业的分析结论，包含：
         1. 数据趋势分析
         2. 关键指标解读（引用知识库中的专业定义和标准）
         3. 业务洞察和建议
        }

        【可视化图表代码】
        ```json
        %s
        ```

        要求：
        1. 分析结论要引用知识库中的专业知识，体现专业性
        2. 可视化图表代码必须是完整的ECharts配置JSON
        3. 必须使用用户指定的图表类型：%s
        4. 图表配置JSON必须使用双引号，不能有注释，确保是标准的JSON格式
        5. 确保JSON格式正确，可以直接被前端ECharts使用
        6. 不要在任何地方使用单引号，全部使用双引号
        """;

    public String buildPrompt(String chartType){
        return String.format(DATA_ANALYST_PROMPT, chartTypeTemplateConfig.getTemplate(chartType), chartType);
    }

    /**
     * 构建知识库增强的 Prompt
     *
     * @param goal 用户的分析目标
     * @param knowledgeResult 知识库检索结果
     * @param csvData 原始 CSV 数据
     * @param chartType 图表类型
     * @return 增强后的 Prompt
     */
    public String buildEnhancedPrompt(String goal, String knowledgeResult, String csvData, String chartType) {
        return String.format(ENHANCED_DATA_ANALYST_PROMPT,
                goal,
                knowledgeResult,
                csvData,
                chartTypeTemplateConfig.getTemplate(chartType),
                chartType
        );
    }
}
