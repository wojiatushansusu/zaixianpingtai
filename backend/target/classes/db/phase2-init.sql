-- ============================================
-- 在线考试平台 - 阶段二 数据库增量脚本
-- 智能组卷 + 基础视频身份核验
-- ============================================

USE exam_platform;

-- -------------------------------------------
-- 1. 知识点标签表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `knowledge_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签ID',
    `name` VARCHAR(100) NOT NULL COMMENT '标签名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父标签ID（支持层级）',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB COMMENT='知识点标签表';

-- 初始化示例标签
INSERT INTO `knowledge_tag` (`name`, `parent_id`, `sort_order`) VALUES
('Java基础', NULL, 1),
('Spring框架', NULL, 2),
('数据库', NULL, 3),
('前端技术', NULL, 4),
('网络与安全', NULL, 5),
('数据结构', NULL, 6);

-- -------------------------------------------
-- 2. 题目-标签关联表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `question_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_tag` (`question_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB COMMENT='题目标签关联表';

-- 给现有题目打标签
INSERT INTO `question_tag` (`question_id`, `tag_id`) VALUES
(1, 1), (4, 1), (5, 2), (2, 2), (3, 3), (6, 5),
(7, 4), (8, 1), (9, 2), (10, 3);

-- -------------------------------------------
-- 3. 用户人脸信息表（存储注册照片）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `user_face` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `photo_url` VARCHAR(500) DEFAULT NULL COMMENT '注册照片路径',
    `face_token` VARCHAR(500) DEFAULT NULL COMMENT '人脸特征Token（预留，对接商业SDK用）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1-有效 0-无效)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='用户人脸信息表';

-- -------------------------------------------
-- 4. 人脸核验记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `face_verify_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `verify_photo_url` VARCHAR(500) DEFAULT NULL COMMENT '核验照片路径',
    `similarity` DOUBLE DEFAULT NULL COMMENT '相似度得分(0-1)',
    `verify_result` TINYINT NOT NULL COMMENT '核验结果(1-通过 0-未通过)',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    `verify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核验时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_exam` (`user_id`, `exam_id`),
    KEY `idx_exam_id` (`exam_id`)
) ENGINE=InnoDB COMMENT='人脸核验记录表';

-- -------------------------------------------
-- 5. 考试凭证表（核验通过后颁发的临时Token）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `exam_token` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `token` VARCHAR(200) NOT NULL COMMENT '考试凭证Token',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `used` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已使用(0-未使用 1-已使用)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token` (`token`),
    KEY `idx_user_exam` (`user_id`, `exam_id`)
) ENGINE=InnoDB COMMENT='考试凭证表';

-- -------------------------------------------
-- 6. 违规截图表（新增字段：截图路径）
-- -------------------------------------------
ALTER TABLE `violation_record`
    ADD COLUMN `screenshot_url` VARCHAR(500) DEFAULT NULL COMMENT '违规截图路径' AFTER `description`;

-- -------------------------------------------
-- 7. 题目表新增字段（难度、标签相关）
-- -------------------------------------------
ALTER TABLE `question`
    ADD COLUMN `difficulty` TINYINT NOT NULL DEFAULT 2 COMMENT '难度(1-简单 2-中等 3-困难)' AFTER `score`,
    ADD COLUMN `image_url` VARCHAR(500) DEFAULT NULL COMMENT '题干图片路径' AFTER `analysis`;

-- 更新现有题目难度
UPDATE `question` SET `difficulty` = 1 WHERE `id` IN (1, 3, 6, 7, 10);
UPDATE `question` SET `difficulty` = 2 WHERE `id` IN (2, 8, 9);
UPDATE `question` SET `difficulty` = 3 WHERE `id` IN (4, 5);

-- -------------------------------------------
-- 8. 考试表新增字段（身份核验开关）
-- -------------------------------------------
ALTER TABLE `exam`
    ADD COLUMN `require_face_verify` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人脸核验(1-是 0-否)' AFTER `max_switch_count`;

-- -------------------------------------------
-- 9. 用户表新增字段（学号/工号）
-- -------------------------------------------
ALTER TABLE `user`
    ADD COLUMN `student_no` VARCHAR(50) DEFAULT NULL COMMENT '学号/工号' AFTER `real_name`;

-- 更新现有用户
UPDATE `user` SET `student_no` = 'T001' WHERE `id` = 2;
UPDATE `user` SET `student_no` = 'S001' WHERE `id` = 3;
UPDATE `user` SET `student_no` = 'S002' WHERE `id` = 4;

-- -------------------------------------------
-- 10. 初始化组卷策略配置
-- -------------------------------------------
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('paper_gen_strategy', 'greedy', '组卷策略(greedy-贪心, random-随机)'),
('paper_gen_seed', '42', '组卷随机种子（可重现）'),
('face_verify_threshold', '0.75', '人脸比对通过阈值'),
('face_verify_enabled', 'true', '是否启用人脸核验功能');
