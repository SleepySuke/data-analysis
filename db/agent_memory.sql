-- Agent 记忆系统表
-- 用于存储用户画像、交互日志，支持长期记忆和跨会话个性化

CREATE TABLE IF NOT EXISTS `user_profile` (
    `id`               BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_id`          BIGINT NOT NULL UNIQUE COMMENT '用户ID',
    `industry`         VARCHAR(64)  DEFAULT NULL COMMENT '行业: finance/medical/retail/tech',
    `expertise`        VARCHAR(32)  DEFAULT 'general' COMMENT '专业程度: general/intermediate/expert',
    `preferred_charts` VARCHAR(255) DEFAULT 'bar,line,pie' COMMENT '偏好图表类型',
    `detail_level`     VARCHAR(16)  DEFAULT 'standard' COMMENT '详情级别: brief/standard/detailed',
    `report_style`     VARCHAR(32)  DEFAULT 'business' COMMENT '报告风格: academic/business/casual',
    `frequent_topics`  JSON DEFAULT NULL COMMENT '常问主题 [{"topic":"销售趋势","count":12}]',
    `create_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户画像表';

CREATE TABLE IF NOT EXISTS `agent_interaction_log` (
    `id`           BIGINT PRIMARY KEY COMMENT '雪花ID',
    `user_id`      BIGINT NOT NULL COMMENT '用户ID',
    `session_id`   VARCHAR(64) NOT NULL COMMENT '会话ID',
    `agent_name`   VARCHAR(32) NOT NULL COMMENT 'Agent名称',
    `intent`       VARCHAR(64) DEFAULT NULL COMMENT '意图分类',
    `topic`        VARCHAR(128) DEFAULT NULL COMMENT '主题',
    `tokens_used`  INT DEFAULT 0 COMMENT 'Token消耗',
    `duration_ms`  INT DEFAULT 0 COMMENT '耗时(ms)',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_user_created (user_id, create_time DESC),
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent交互日志';
