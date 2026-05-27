-- Agent Trace 观测表
-- 用于记录每次 Agent 调用的完整链路

CREATE TABLE IF NOT EXISTS `agent_trace` (
    `id`                BIGINT PRIMARY KEY COMMENT '雪花ID',
    `trace_id`          VARCHAR(64) NOT NULL UNIQUE COMMENT '追踪ID',
    `session_id`        VARCHAR(64) NOT NULL COMMENT '会话ID',
    `user_id`           BIGINT NOT NULL COMMENT '用户ID',
    `entry_type`        VARCHAR(16) NOT NULL COMMENT '入口类型: direct/supervisor',
    `target_agent`      VARCHAR(32) NOT NULL COMMENT '目标Agent名称',
    `steps`             JSON DEFAULT NULL COMMENT '执行步骤(JSON数组)',
    `handoffs`          JSON DEFAULT NULL COMMENT '转交记录(JSON数组)',
    `total_tokens`      INT DEFAULT 0 COMMENT '总Token消耗',
    `total_duration_ms` INT DEFAULT 0 COMMENT '总耗时(ms)',
    `final_output`      TEXT DEFAULT NULL COMMENT '最终输出',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'success' COMMENT '状态: success/failed/timeout',
    `create_time`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_created (user_id, create_time DESC),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent调用追踪';
