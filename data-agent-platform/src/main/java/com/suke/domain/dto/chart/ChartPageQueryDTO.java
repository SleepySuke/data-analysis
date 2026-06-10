package com.suke.domain.dto.chart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

@Data
public class ChartPageQueryDTO implements Serializable {
    private static final long serialVersionUID = 45123687L;

    private String chartType;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码不能小于1")
    private Integer page;

    private String goal;
    private Integer id;
    private String name;

    @NotNull(message = "每页记录数不能为空")
    @Min(value = 1, message = "每页记录数不能小于1")
    private Integer pageSize;

    private String sortField;
    private String sortOrder;
    private Integer userId;
}
