package com.suke.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import java.time.LocalDateTime;
import java.io.Serializable;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.Schema;
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
@ApiModel(name="Chart对象", description="图表信息表")
public class Chart implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(name = "id")
    @TableId(name = "id", type = IdType.AUTO)
    private Long id;

    @Schema(name = "分析目标")
    private String goal;

    @Schema(name = "图表数据")
    private String chartData;

    @Schema(name = "图表类型")
    private String chartType;

    @Schema(name = "生成的图表数据")
    private String genChart;

    @Schema(name = "生成的分析结论")
    private String genResult;

    @Schema(name = "创建用户 id")
    private Long userId;

    @Schema(name = "创建时间")
    private LocalDateTime createTime;

    @Schema(name = "更新时间")
    private LocalDateTime updateTime;

    @Schema(name = "是否删除")
    private Integer isDelete;


}
