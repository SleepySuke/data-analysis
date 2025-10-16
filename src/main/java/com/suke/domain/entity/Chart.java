package com.suke.domain.entity;

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
 * @author 自然醒
 * @version 1.0
 * @since 2025-10-16
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("chart")
@ApiModel(value="Chart对象", description="图表信息表")
public class Chart implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 图表id
     */
    @ApiModelProperty(value = "id")
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 分析目标
     */
    @ApiModelProperty(value = "分析目标")
    private String goal;

    /**
     * 图表数据
     */
    @ApiModelProperty(value = "图表数据")
    private String chartData;

    /**
     * 图表类型
     */
    @ApiModelProperty(value = "图表类型")
    private String chartType;

    /**
     * 生成的图表数据
     */
    @ApiModelProperty(value = "生成的图表数据")
    private String genChart;

    /**
     * 生成的分析结论
     */
    @ApiModelProperty(value = "生成的分析结论")
    private String genResult;

    /**
     * 创建用户 id
     */
    @ApiModelProperty(value = "创建用户 id")
    private Long userId;

    /**
     * 创建时间
     */
    @ApiModelProperty(value = "创建时间")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @ApiModelProperty(value = "更新时间")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @ApiModelProperty(value = "是否删除")
    private Integer isDelete;


}
