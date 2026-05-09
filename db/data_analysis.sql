/*
 数据库初始化脚本
 Source Server Type  : MySQL 8.0+
 Source Schema       : data_analysis
 字符集: utf8mb4 | 排序规则: utf8mb4_unicode_ci
 ID 策略: 雪花算法（应用层 ASSIGN_ID，非数据库自增）
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for chart
-- ----------------------------
DROP TABLE IF EXISTS `chart`;
CREATE TABLE `chart` (
  `id` bigint NOT NULL COMMENT '图表id（雪花ID）',
  `name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '图表名称',
  `goal` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '分析目标',
  `status` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'wait' COMMENT '状态: wait(等待) / running(执行中) / succeed(成功) / failed(失败)',
  `chart_data` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '图表原始数据(CSV)',
  `chart_type` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '图表类型: line/bar/pie/radar/scatter/area/gauge',
  `gen_chart` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '生成的ECharts图表配置(JSON)',
  `gen_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '生成的分析结论',
  `user_id` bigint NULL DEFAULT NULL COMMENT '创建用户id',
  `exec_msg` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '执行信息',
  `minio_path` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'MinIO文件路径(大文件)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0-未删除 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_user_id`(`user_id`) USING BTREE COMMENT '按用户查询图表'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '图表信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of chart (示例数据已清理)
-- ----------------------------

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id` bigint NOT NULL COMMENT '用户id（雪花ID）',
  `user_account` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录账号',
  `user_password` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录密码(加密存储)',
  `user_name` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户昵称',
  `user_avatar` varchar(1024) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '用户头像URL',
  `user_role` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '用户角色: user / admin / ban',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint NOT NULL DEFAULT 0 COMMENT '是否删除: 0-未删除 1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_account`(`user_account`) USING BTREE COMMENT '账号唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of user (密码已脱敏，请使用注册接口创建用户)
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
