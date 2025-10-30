package com.suke.domain.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @author 自然醒
 * @version 1.0
 */
//智能分析后的结果
@Data
@Accessors(chain = true)
public class GenChartVO {
    /**
     * 生成的结果
     */
    private String genResult;

    /**
     * 生成的图表数据
     */
    private String genChart;

    /**
     * 图表id
     */
    private Long chartId;
}
