package com.suke.agent.trace;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("agent_trace")
public class AgentTrace {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String traceId;
    private String sessionId;
    private Long userId;
    private String entryType; // direct, supervisor
    private String targetAgent;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<AgentStep> steps;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<HandoffRecord> handoffs;

    private Integer totalTokens;
    private Integer totalDurationMs;
    private String finalOutput;
    private String status; // success, failed, timeout

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
