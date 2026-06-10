/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 交互日志实体，记录用户与Agent的每次交互
 */

package com.suke.agent.memory.model;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_interaction_log")
public class InteractionLog {

    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;
    private String sessionId;
    private String agentName;
    private String intent;
    private String topic;
    private Integer tokensUsed;
    private Integer durationMs;

    @TableField(fill = FieldFill.INSERT, value = "create_time")
    private LocalDateTime createTime;
}
