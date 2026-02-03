package com.suke.domain.entity;

import com.baomidou.mybatisplus.annotation.*;

import java.time.LocalDateTime;
import java.io.Serializable;
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
public class Chart implements Serializable {

    private static final long serialVersionUID = 1L;


    /**
     * 图表id
     */
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 名称
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
     * 状态
     */
    private String status;

    /**
     * 执行信息
     */
    private String execMsg;

    /**
     * 图表类型
     */
    private String chartType;

    /**
     * 生成的图表数据
     */
    private String genChart;

    /**
     * 生成的分析结论
     */
    private String genResult;

    /**
     * 创建用户 id
     */
    private Long userId;

    /**
     * 文件路径
     */
    private String minioPath;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT,value = "createTime")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE,value = "updateTime")
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;


}