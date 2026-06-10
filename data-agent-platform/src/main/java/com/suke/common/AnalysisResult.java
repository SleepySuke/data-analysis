package com.suke.common;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * @author 自然醒
 * @version 1.0
 */
//智能助手返回的响应结果
@Data
@AllArgsConstructor
public class AnalysisResult {
    /**
     * 分析结果
     */
    private String analysis;
    /**
     * 图表配置
     */
    private String chartConfig;
}
