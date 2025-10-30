package com.suke.domain.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
//图表的添加信息
@Data
public class ChartAddDTO implements Serializable {
    private static final long serialVersionUID = 45123687L;

    /**
     * 图表名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表数据
     */
    private String chartData;

    /**
     * 图表类型
     */
    private String chartType;
}
