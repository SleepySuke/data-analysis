/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description Skill定义实体，映射agent_skill表
 */

package com.suke.agent.skill.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName(value = "agent_skill", autoResultMap = true)
public class SkillDefinition {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private String skillName;
    private String description;
    private String agentName;
    private String promptTemplate;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String allowedTools;

    @TableField(typeHandler = com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler.class)
    private String extension;

    private String ownerType;
    private Long ownerId;
    private Integer usageCount;
    private Boolean isPublic;

    @TableField(fill = FieldFill.INSERT, value = "create_time")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE, value = "update_time")
    private LocalDateTime updateTime;
}
