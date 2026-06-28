-- =====================================================
-- 阶段五：AI自助考试模块
-- =====================================================

-- 自助考试表（学生创建的AI生成考试）
CREATE TABLE IF NOT EXISTS `self_study_exam` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `title` VARCHAR(200) NOT NULL COMMENT '考试标题',
    `description` TEXT COMMENT '考试描述/要求',
    `creator_id` BIGINT NOT NULL COMMENT '创建者（学生）ID',
    `exam_code` VARCHAR(8) NOT NULL COMMENT '考试码（6位），其他学生通过此码加入',
    `paper_id` BIGINT COMMENT '关联试卷ID',
    `duration` INT NOT NULL DEFAULT 60 COMMENT '考试时长（分钟）',
    `total_score` INT NOT NULL DEFAULT 100 COMMENT '总分',
    `pass_score` INT NOT NULL DEFAULT 60 COMMENT '及格分',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-生成中 1-待考试 2-进行中 3-已结束',
    `question_count` INT DEFAULT 0 COMMENT '题目数量',
    `student_count` INT NOT NULL DEFAULT 1 COMMENT '参与学生数（含创建者）',
    `exam_id` BIGINT COMMENT '关联的正式考试ID（复用Exam表）',
    `generate_prompt` TEXT COMMENT '用户输入的AI生成要求',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_exam_code` (`exam_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助考试表';

-- 自助考试参与记录表（通过考试码加入的学生）
CREATE TABLE IF NOT EXISTS `self_study_participant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `exam_id` BIGINT NOT NULL COMMENT '自助考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `is_creator` TINYINT NOT NULL DEFAULT 0 COMMENT '是否创建者: 0-否 1-是',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `deleted` TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自助考试参与记录';
