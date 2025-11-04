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

    public String buildPrompt(String chartType){
        return String.format(DATA_ANALYST_PROMPT, chartTypeTemplateConfig.getTemplate(chartType), chartType);
    }
}
