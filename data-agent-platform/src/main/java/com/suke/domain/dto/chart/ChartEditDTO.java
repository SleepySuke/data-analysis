package com.suke.domain.dto.chart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChartEditDTO implements Serializable {
    private static final long serialVersionUID = 454238792316L;

    @NotNull(message = "图表ID不能为空")
    private Long id;

    @NotBlank(message = "图表名称不能为空")
    private String name;

    @NotBlank(message = "分析目标不能为空")
    private String goal;

    @NotBlank(message = "图表类型不能为空")
    private String chartType;
}
