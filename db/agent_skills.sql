-- Agent Skill 系统表
-- 支持系统内置 Skill 和用户自定义 Skill 的渐进式披露
-- extension 字段存储扩展信息：scripts/tags/inputFormat/outputFormat 等

CREATE TABLE IF NOT EXISTS `agent_skill` (
    `id`              BIGINT PRIMARY KEY COMMENT '雪花ID',
    `skill_name`      VARCHAR(64) NOT NULL COMMENT 'Skill名称',
    `description`     VARCHAR(2048) NOT NULL COMMENT 'Skill描述',
    `agent_name`      VARCHAR(32) NOT NULL COMMENT 'Agent名称: data_analyst/web_scraper/sql_analyst/data_cleaner',
    `prompt_template` TEXT NOT NULL COMMENT '完整指令模板',
    `allowed_tools`   JSON DEFAULT NULL COMMENT '可用工具名列表，如 ["executeScript","analyzeCsv"]',
    `extension`       JSON DEFAULT NULL COMMENT '扩展信息: scripts/tags/inputFormat/outputFormat/version 等',
    `owner_type`      VARCHAR(16) NOT NULL DEFAULT 'SYSTEM' COMMENT '归属类型: SYSTEM/USER',
    `owner_id`        BIGINT DEFAULT NULL COMMENT 'USER类型时为userId',
    `usage_count`     INT DEFAULT 0 COMMENT '使用次数',
    `is_public`       TINYINT(1) DEFAULT 0 COMMENT '是否公开',
    `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_agent_skill (agent_name, skill_name, owner_type, owner_id),
    INDEX idx_agent_owner (agent_name, owner_type, owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent Skill定义表';
