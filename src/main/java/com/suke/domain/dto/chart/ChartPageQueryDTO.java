package com.suke.domain.dto.chart;

import lombok.Data;

import java.io.Serializable;

/**
 * @author 自然醒
 * @version 1.0
 */
// 查询图表信息
@Data
public class ChartPageQueryDTO implements Serializable {
    private static final long serialVersionUID = 45123687L;
    /**
     * 图表类型
     */
    private String chartType;
    /**
     *页码
     */
    private Integer page;
    /**
     * 分析的目标
     */
    private String goal;
    /**
     * 图表id
     */
    private Integer id;
    /**
     * 图表名称
     */
    private String name;
    /**
     * 每页记录数
     */
    private Integer pageSize;
    /**
     * 排序字段
     */
    private String sortField;
    /**
     * 排序顺序（默认升序）
     */
    private String sortOrder;
    /**
     * 用户id
     */
    private Integer userId;
}
