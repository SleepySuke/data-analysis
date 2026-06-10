package com.suke.domain.enums;

/**
 * @author 自然醒
 * @version 1.0
 */
//图表类型枚举类,用于AI提示词模版
public enum ChartType {
    LINE("折线图"),
    BAR("柱状图"),
    PIE("饼图"),
    RADAR("雷达图"),
    SCATTER("散点图"),
    AREA("面积图"),
    GAUGE("仪表盘");

    private final String description;

    ChartType(String description) {
        this.description = description;
    }
}
