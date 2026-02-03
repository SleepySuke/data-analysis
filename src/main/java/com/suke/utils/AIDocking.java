package com.suke.utils;

import com.suke.common.ErrorCode;
import com.suke.common.Result;
import com.suke.config.ChartTypeTemplateConfig;
import com.suke.exception.AIDockingException;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * @author 自然醒
 * @version 1.0
 */
//智能助手对接工具类
@Slf4j
@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AIDocking {
    @Resource(name = "qwen")
    private ChatModel qwenModel;
    @Resource(name = "qwenChatClient")
    private ChatClient qwenClient;

    @Resource(name = "deepseekClient")
    private ChatClient deepseekClient;

    @Autowired
    private PromptBuilder promptBuilder;
    @Autowired
    private ChartTypeTemplateConfig chartTypeTemplateConfig;

//    // 数据分析师提示词
//    private static final String DATA_ANALYST_PROMPT = """
//        你是一个专业的数据分析师和前端ECharts专家。请根据用户选择的图表类型和数据分析需求，生成相应的分析结论和ECharts配置，必须严格按照以下格式响应：
//
//        【数据分析结论】
//        {详细的数据分析结论，包含趋势分析、特征总结、洞察发现等}
//
//        【可视化图表代码】
//        ```json
//        %s
//        ```
//
//        要求：
//        1. 数据分析结论要专业、详细，包含数值分析和趋势判断
//        2. 可视化图表代码必须是完整的ECharts配置JSON
//        3. 根据数据类型自动选择最合适的图表类型（折线图、柱状图、饼图等）
//        4. 确保JSON格式正确，可以直接被前端ECharts使用
//        特别注意：
//        5. 必须使用用户指定的图表类型：%s
//        6. 图表配置JSON必须使用双引号，不能有注释，确保是标准的JSON格式
//        7. 图表配置要包含title、xAxis、yAxis、series等必要组件
//        8. 不要在任何地方使用单引号，全部使用双引号
//
//        现在请分析以下数据：
//        """;

    /**
     * 智能助手进行数据智能分析
     * @param requirement
     * @param chartType
     * @param csvData
     * @return 包含分析结论和图表代码的完整响应
     */
    public String doDataAnalysis(String requirement,String chartType,String csvData){
        log.info("分析需求：{}", requirement);
        log.info("原始数据：{}", csvData);
        if(!chartTypeTemplateConfig.supportsChartType(chartType)){
            throw new AIDockingException("不支持的图表类型");
        }
        String prompt = promptBuilder.buildPrompt(chartType)  + "分析需求：" + requirement + "\n"
                + (StringUtils.isAnyBlank(chartType)? "图表类型：" + chartType + "\n": "")+
                "原始数据：\n" + csvData;
        String result = qwenClient.prompt().system(prompt).call().content();
        if(result == null || result.trim().isEmpty()){
            throw new AIDockingException("AI数据分析响应错误");
        }
        log.info("AI输出：{}",result);
        return result;
    }


    //标有todo的两个功能个人感觉不知道应该如何进行切换，因为在分析数据时感觉只需要一个较为专业即可，所以没有进行封装
    /**
     * todo
     * 智能助手直接响应
     * @param msg
     * @return
     */
    public String doChat(String msg){
        log.info("用户输入：{}", msg);
        String result = qwenModel.call(msg);
        if(result == null){
            throw new AIDockingException("AI响应错误");
        }
        log.info("AI输出：{}",result);
        return result;
    }

    /**
     * todo
     * 智能助手流式响应
     * @param msg
     * @return
     */
    public Flux<String> doChatStream(String msg){
        return null;
    }
}
