-- ============================================
-- 在线考试平台 - 阶段四 数据库增量脚本
-- 错题集 + 错题练习考试
-- ============================================

USE exam_platform;

-- -------------------------------------------
-- 1. 错题练习记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `practice_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '练习记录ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `title` VARCHAR(200) NOT NULL COMMENT '练习标题',
    `total_count` INT NOT NULL DEFAULT 0 COMMENT '总题数',
    `correct_count` INT NOT NULL DEFAULT 0 COMMENT '正确题数',
    `score` INT NOT NULL DEFAULT 0 COMMENT '得分',
    `total_score` INT NOT NULL DEFAULT 0 COMMENT '总分',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0-进行中 1-已完成)',
    `start_time` DATETIME DEFAULT NULL COMMENT '开始时间',
    `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_student_id` (`student_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB COMMENT='错题练习记录表';

-- -------------------------------------------
-- 2. 练习答题记录表
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `practice_answer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `practice_id` BIGINT NOT NULL COMMENT '练习记录ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `student_answer` VARCHAR(500) DEFAULT NULL COMMENT '学生答案',
    `is_correct` TINYINT DEFAULT NULL COMMENT '是否正确(1-是 0-否)',
    `score` INT DEFAULT 0 COMMENT '得分',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_practice_question` (`practice_id`, `question_id`),
    KEY `idx_practice_id` (`practice_id`)
) ENGINE=InnoDB COMMENT='练习答题记录表';
