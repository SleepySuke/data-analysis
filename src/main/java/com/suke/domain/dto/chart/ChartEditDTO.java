package com.suke.domain.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
// 图表信息修改
@Data
public class ChartEditDTO implements Serializable {
    private static final long serialVersionUID = 454238792316L;

    /**
     * 图表id
     */
    private Long id;

    /**
     * 图表名称
     */
    private String name;

    /**
     * 分析目标
     */
    private String goal;

    /**
     * 图表类型
     */
    private String chartType;

}
