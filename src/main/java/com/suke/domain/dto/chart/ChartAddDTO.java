package com.suke.domain.dto.chart;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChartAddDTO implements Serializable {
    private static final long serialVersionUID = 45123687L;

    @NotBlank(message = "图表名称不能为空")
    @Size(max = 100, message = "图表名称过长")
    private String name;

    @NotBlank(message = "分析目标不能为空")
    private String goal;

    private String chartData;

    @NotBlank(message = "图表类型不能为空")
    private String chartType;
}
