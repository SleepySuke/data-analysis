package com.suke.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 图表信息表
 * </p>
 *
 * @author author
 * @since 2025-10-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chart")
@ApiModel(value="Chart对象", description="图表信息表")
public class Chart implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @ApiModelProperty(value = "分析目标")
    private String goal;

    @ApiModelProperty(value = "图表数据")
    private String chartData;

    @ApiModelProperty(value = "图表类型")
    private String chartType;

    @ApiModelProperty(value = "生成的图表数据")
    private String genChart;

    @ApiModelProperty(value = "生成的分析结论")
    private String genResult;

    @ApiModelProperty(value = "创建用户 id")
    private Long userId;

    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;

    @ApiModelProperty(value = "是否删除")
    private Integer isDelete;


}
