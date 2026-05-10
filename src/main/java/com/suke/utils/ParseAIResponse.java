package com.suke.utils;

import com.alibaba.fastjson2.JSON;
import com.suke.common.AnalysisResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author 自然醒
 * @version 1.0
 */
//智能助手响应解析工具类
@Slf4j
public class ParseAIResponse {
    /**
     * 解析AI响应
     * @param response
     * @return
     */
    public static AnalysisResult parseResponse(String response){
        log.info("AI响应：{}", response);
        String analysis = extractCleanAnalysis(response);
        String chartConfig = extractChartConfig(response);
        //如果没有分析结果则使用响应作为分析结果
        if(StringUtils.isAnyBlank( analysis)){
            analysis = removeChartCode( response);
        }
        //如果没有图表配置则使用默认的图表配置
        if(StringUtils.isAnyBlank(chartConfig)){
            chartConfig = createDefaultChartConfig();
        }else{
            chartConfig = ensureValidJson(chartConfig);
        }
        return new AnalysisResult(analysis,chartConfig);
    }

    /**
     * 提取纯净的分析结论（不包含任何标记)
     * @param response
     * @return
     */
    private static String extractCleanAnalysis(String response){
        String analysis = extractAnalysis(response);
        if(StringUtils.isNotBlank( analysis)){
            return cleanAnalysisText(analysis);
        }
        // 如果没有找到标记，尝试智能提取分析结论部分
        return extractAnalysisIntelligently(response);
    }

    /**
     * 提取带标记分析结果
     * @param response
     * @return
     */
    private static String extractAnalysis(String response){
        //提取[数据分析结论部分]
        Pattern pattern = Pattern.compile("【数据分析结论】\\s*(.*?)(?=【可视化图表代码】|$)", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if(matcher.find()){
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 清理分析结果文本
     * @param analysis
     * @return
     */
    private static String cleanAnalysisText(String analysis){
        if (StringUtils.isBlank(analysis)) {
            return analysis;
        }
        // 移除可能的代码块标记
        String cleaned = analysis
                .replaceAll("^【数据分析结论】\\s*", "")
                .replaceAll("```json.*?```", "")  // 移除JSON代码块
                .replaceAll("```.*?```", "")      // 移除其他代码块
                .replaceAll("\\*\\*json.*?\\*\\*", "") // 移除**json标记
                .replaceAll("\\{.*?\\}", "")      // 移除可能的JSON片段
                .replaceAll("\\[.*?\\]", "")      // 移除可能的数组片段
                .trim();
        // 移除多余的空行
        cleaned = cleaned.replaceAll("\\n\\s*\\n", "\n");
        // 确保以正常文本结尾，移除可能的图表代码残留
        int jsonStart = cleaned.indexOf("{");
        if (jsonStart > 0) {
            cleaned = cleaned.substring(0, jsonStart).trim();
        }
        // 移除末尾的标点问题
        cleaned = cleaned.replaceAll("[，。！？;:,.!?]+$", "");
        return cleaned.trim();
    }

    /**
     * 智能提取分析结论部分
     * @param response
     * @return
     */
    private static String extractAnalysisIntelligently(String response){
        // 尝试智能提取分析结论部分
        int chartCodeStart = findChartCodeStart(response);
        if (chartCodeStart > 0) {
            // 返回图表代码之前的内容
            String potentialAnalysis = response.substring(0, chartCodeStart).trim();
            return cleanAnalysisText(potentialAnalysis);
        }
        // 如果没有找到图表代码，返回整个响应但清理可能的代码片段
        return cleanAnalysisText(response);
    }

    /**
     * 提取图表配置
     * @param response
     * @return
     */
    private static String extractChartConfig(String response){
        //提取json代码块
        Pattern pattern = Pattern.compile("```json\\s*(.*?)\\s*```", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(response);
        if(matcher.find()){
            return matcher.group(1).trim();
        }
        //没有json代码块则尝试提取json代码块
        pattern = Pattern.compile("\\{.*?\"xAxis\".*?\\}", Pattern.DOTALL);
        matcher = pattern.matcher(response);
        if(matcher.find()){
            return matcher.group(0).trim();
        }
        return null;
    }

    /**
     * 创建默认的图表配置
     * @return
     */
    private static String createDefaultChartConfig(){
        return """
            {
                "title": {"text": "数据分析图表"},
                "tooltip": {"trigger": "axis"},
                "xAxis": {"type": "category", "data": []},
                "yAxis": {"type": "value"},
                "series": [{"type": "line", "data": []}]
            }""";
    }

    /**
     * 修复json格式
     * @param jsonStr
     * @return
     */
    private static String fixJsonFormat(String jsonStr ){
        String fixed = jsonStr.trim();
        if(!fixed.startsWith("{")){
            fixed = "{" + fixed;
        }
        if(!fixed.endsWith("}")){
            fixed = fixed + "}";
        }
        return fixed;
    }

    /**
     * 确保JSON格式正确
     * @param jsonStr
     * @return
     */
    private static String ensureValidJson(String jsonStr){
        try{
            JSON.parseObject(jsonStr);
            return jsonStr;
        }catch (Exception e){
            log.warn("图表格式JSON格式不正确,尝试修正：{}",e.getMessage());
            String fixed = fixJsonFormat(jsonStr);
            try {
                JSON.parseObject(fixed);
                return fixed;
            } catch (Exception e2) {
                log.warn("JSON修正失败,使用默认配置");
                return createDefaultChartConfig();
            }
        }
    }

    /**
     * 移除图表代码
     * @param response
     * @return
     */
    private static String removeChartCode(String response){
        int chartStart = findChartCodeStart(response);
        if (chartStart > 0) {
            return cleanAnalysisText(response.substring(0, chartStart));
        }
        return cleanAnalysisText(response);
    }

    /**
     * 寻找图表代码的起始位置
     * @param response
     * @return
     */
    private static int findChartCodeStart(String response){
        // 可能的图表代码标记
        String[] chartMarkers = {
                "【可视化图表代码】",
                "```json",
                "**json",
                "{\"title\"",
                "{\"xAxis\"",
                "{\"series\""
        };
        for (String marker : chartMarkers) {
            int index = response.indexOf(marker);
            if (index >= 0) {
                return index;
            }
        }
        return -1;
    }
}
