-- ============================================
-- 在线考试平台 - 完整数据库初始化脚本
-- 数据库: MySQL 8.0+ / 9.x
-- 字符集: utf8mb4 / utf8mb4_0900_ai_ci
-- ============================================

CREATE DATABASE IF NOT EXISTS exam_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE exam_platform;

-- ============================================
-- 一、基础表结构
-- ============================================

-- -------------------------------------------
-- 1. 角色表
-- -------------------------------------------
DROP TABLE IF EXISTS `role`;
CREATE TABLE `role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    `role_name` VARCHAR(50) NOT NULL COMMENT '角色名称',
    `role_key` VARCHAR(50) NOT NULL COMMENT '角色标识',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`)
) ENGINE=InnoDB COMMENT='角色表';

-- -------------------------------------------
-- 2. 用户表
-- -------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `student_no` VARCHAR(50) DEFAULT NULL COMMENT '学号/工号',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `gender` VARCHAR(20) DEFAULT 'unknown' COMMENT '性别(male-男 female-女 unknown-保密)',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像路径',
    `avatar_data` LONGBLOB DEFAULT NULL COMMENT '头像二进制数据',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1-正常 0-禁用)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB COMMENT='用户表';

-- -------------------------------------------
-- 3. 知识点标签表
-- -------------------------------------------
DROP TABLE IF EXISTS `knowledge_tag`;
CREATE TABLE `knowledge_tag` (
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

-- -------------------------------------------
-- 4. 题目表
-- -------------------------------------------
DROP TABLE IF EXISTS `question`;
CREATE TABLE `question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目ID',
    `type` TINYINT NOT NULL COMMENT '题目类型(1-单选 2-多选 3-判断 4-简答)',
    `content` TEXT NOT NULL COMMENT '题干',
    `option_a` VARCHAR(500) DEFAULT NULL COMMENT '选项A',
    `option_b` VARCHAR(500) DEFAULT NULL COMMENT '选项B',
    `option_c` VARCHAR(500) DEFAULT NULL COMMENT '选项C',
    `option_d` VARCHAR(500) DEFAULT NULL COMMENT '选项D',
    `answer` VARCHAR(200) DEFAULT NULL COMMENT '正确答案(A/B/C/D，多选用逗号分隔，判断用true/false，简答题为空)',
    `analysis` TEXT DEFAULT NULL COMMENT '解析',
    `image_url` VARCHAR(500) DEFAULT NULL COMMENT '题干图片路径',
    `score` INT NOT NULL DEFAULT 5 COMMENT '默认分值',
    `difficulty` TINYINT NOT NULL DEFAULT 1 COMMENT '难度(1-简单 2-中等 3-困难)',
    `knowledge_point` VARCHAR(64) DEFAULT NULL COMMENT '知识点（对应复习路径的阶段模块名，AI 出卷时标注）',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB COMMENT='题目表';

-- -------------------------------------------
-- 5. 题目-标签关联表
-- -------------------------------------------
DROP TABLE IF EXISTS `question_tag`;
CREATE TABLE `question_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `tag_id` BIGINT NOT NULL COMMENT '标签ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_question_tag` (`question_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB COMMENT='题目标签关联表';

-- -------------------------------------------
-- 6. 用户人脸信息表
-- -------------------------------------------
DROP TABLE IF EXISTS `user_face`;
CREATE TABLE `user_face` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `photo_url` VARCHAR(500) DEFAULT NULL COMMENT '注册照片路径',
    `photo_data` LONGBLOB DEFAULT NULL COMMENT '注册照片二进制数据',
    `face_token` VARCHAR(500) DEFAULT NULL COMMENT '人脸特征Token（预留，对接商业SDK用）',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1-有效 0-无效)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB COMMENT='用户人脸信息表';

-- -------------------------------------------
-- 7. 人脸核验记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `face_verify_record`;
CREATE TABLE `face_verify_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `verify_photo_url` VARCHAR(500) DEFAULT NULL COMMENT '核验照片路径',
    `verify_photo_data` LONGBLOB DEFAULT NULL COMMENT '核验照片二进制数据',
    `similarity` DOUBLE DEFAULT NULL COMMENT '相似度得分(0-1)',
    `verify_result` TINYINT NOT NULL COMMENT '核验结果(1-通过 0-未通过)',
    `fail_reason` VARCHAR(200) DEFAULT NULL COMMENT '失败原因',
    `verify_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '核验时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_exam` (`user_id`, `exam_id`),
    KEY `idx_exam_id` (`exam_id`)
) ENGINE=InnoDB COMMENT='人脸核验记录表';

-- -------------------------------------------
-- 8. 考试凭证表
-- -------------------------------------------
DROP TABLE IF EXISTS `exam_token`;
CREATE TABLE `exam_token` (
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
-- 9. 试卷表
-- -------------------------------------------
DROP TABLE IF EXISTS `paper`;
CREATE TABLE `paper` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '试卷ID',
    `title` VARCHAR(200) NOT NULL COMMENT '试卷名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '试卷描述',
    `total_score` INT NOT NULL DEFAULT 0 COMMENT '总分',
    `pass_score` INT NOT NULL DEFAULT 60 COMMENT '及格分',
    `question_count` INT NOT NULL DEFAULT 0 COMMENT '题目数量',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB COMMENT='试卷表';

-- -------------------------------------------
-- 10. 试卷题目关联表
-- -------------------------------------------
DROP TABLE IF EXISTS `paper_question`;
CREATE TABLE `paper_question` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `paper_id` BIGINT NOT NULL COMMENT '试卷ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `score` INT NOT NULL DEFAULT 5 COMMENT '本题分值',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_paper_question` (`paper_id`, `question_id`),
    KEY `idx_paper_id` (`paper_id`),
    KEY `idx_question_id` (`question_id`)
) ENGINE=InnoDB COMMENT='试卷题目关联表';

-- -------------------------------------------
-- 11. 考试表
-- -------------------------------------------
DROP TABLE IF EXISTS `exam`;
CREATE TABLE `exam` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '考试ID',
    `title` VARCHAR(200) NOT NULL COMMENT '考试名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '考试描述',
    `paper_id` BIGINT NOT NULL COMMENT '试卷ID',
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID(教师)',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `duration` INT NOT NULL COMMENT '考试时长(分钟)',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0-未开始 1-进行中 2-已结束)',
    `max_switch_count` INT NOT NULL DEFAULT 3 COMMENT '最大允许切屏次数',
    `notice` TEXT DEFAULT NULL COMMENT '考试须知',
    `require_face_verify` TINYINT NOT NULL DEFAULT 0 COMMENT '是否需要人脸核验(0-否 1-是)',
    `enable_ai_monitor` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用AI监考(0-否 1-是)',
    `pass_score` INT NOT NULL DEFAULT 60 COMMENT '及格分',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_paper` (`paper_id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_time` (`start_time`, `end_time`)
) ENGINE=InnoDB COMMENT='考试表';

-- -------------------------------------------
-- 12. 考试白名单表
-- -------------------------------------------
DROP TABLE IF EXISTS `exam_whitelist`;
CREATE TABLE `exam_whitelist` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_exam_id` (`exam_id`),
    KEY `idx_student_id` (`student_id`)
) ENGINE=InnoDB COMMENT='考试白名单表';

-- -------------------------------------------
-- 13. 考生在线状态表
-- -------------------------------------------
DROP TABLE IF EXISTS `exam_online_student`;
CREATE TABLE `exam_online_student` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `session_id` VARCHAR(200) DEFAULT NULL COMMENT 'WebSocket会话ID',
    `last_heartbeat` DATETIME NOT NULL COMMENT '最后心跳时间',
    `online_status` TINYINT NOT NULL DEFAULT 1 COMMENT '在线状态(1-在线 0-离线 2-断网中)',
    `disconnect_time` DATETIME DEFAULT NULL COMMENT '断网时间',
    `reconnect_time` DATETIME DEFAULT NULL COMMENT '重连时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_exam_id` (`exam_id`),
    KEY `idx_last_heartbeat` (`last_heartbeat`)
) ENGINE=InnoDB COMMENT='考生在线状态表';

-- -------------------------------------------
-- 14. 考试记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `exam_record`;
CREATE TABLE `exam_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `start_time` DATETIME DEFAULT NULL COMMENT '开始答题时间',
    `submit_time` DATETIME DEFAULT NULL COMMENT '提交时间',
    `score` INT DEFAULT NULL COMMENT '得分',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0-未提交 1-已提交 2-自动提交)',
    `switch_count` INT NOT NULL DEFAULT 0 COMMENT '切屏次数',
    `disconnect_count` INT NOT NULL DEFAULT 0 COMMENT '断网次数',
    `total_disconnect_duration` INT NOT NULL DEFAULT 0 COMMENT '累计断网时长(秒)',
    `last_disconnect_time` DATETIME DEFAULT NULL COMMENT '最后断网时间',
    `is_passed` TINYINT DEFAULT NULL COMMENT '是否及格(1-是 0-否)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`),
    KEY `idx_student` (`student_id`)
) ENGINE=InnoDB COMMENT='考试记录表';

-- -------------------------------------------
-- 15. 答题记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `answer_record`;
CREATE TABLE `answer_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `record_id` BIGINT NOT NULL COMMENT '考试记录ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `student_answer` VARCHAR(500) DEFAULT NULL COMMENT '学生答案',
    `is_correct` TINYINT DEFAULT NULL COMMENT '是否正确(1-是 0-否)',
    `score` INT DEFAULT 0 COMMENT '得分',
    `question_score` INT DEFAULT NULL COMMENT '题目满分',
    `teacher_score` INT DEFAULT NULL COMMENT '教师评分(主观题)',
    `teacher_comment` VARCHAR(1000) DEFAULT NULL COMMENT '教师批注(主观题)',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_question` (`record_id`, `question_id`),
    KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB COMMENT='答题记录表';

-- -------------------------------------------
-- 16. 违规记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `violation_record`;
CREATE TABLE `violation_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `violation_type` VARCHAR(50) NOT NULL COMMENT '违规类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '违规描述',
    `screenshot_url` VARCHAR(500) DEFAULT NULL COMMENT '违规截图路径',
    `screenshot_data` LONGBLOB DEFAULT NULL COMMENT '违规截图二进制数据',
    `severity` TINYINT NOT NULL DEFAULT 1 COMMENT '严重等级(1-低 2-中 3-高)',
    `handled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理(1-是 0-否)',
    `video_evidence_id` BIGINT DEFAULT NULL COMMENT '关联视频证据ID',
    `score` INT NOT NULL DEFAULT 0 COMMENT '违规扣分',
    `occur_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '违规时间',
    PRIMARY KEY (`id`),
    KEY `idx_exam_student` (`exam_id`, `student_id`)
) ENGINE=InnoDB COMMENT='违规记录表';

-- -------------------------------------------
-- 17. 视频证据表
-- -------------------------------------------
DROP TABLE IF EXISTS `video_evidence`;
CREATE TABLE `video_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `violation_id` BIGINT DEFAULT NULL COMMENT '关联违规记录ID',
    `evidence_type` VARCHAR(30) NOT NULL COMMENT '证据类型(screenshot-截图 video_clip-视频片段)',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件路径',
    `file_data` LONGBLOB DEFAULT NULL COMMENT '文件二进制数据(MP4视频或JPEG截图)',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(bytes)',
    `duration` INT DEFAULT NULL COMMENT '视频时长(秒，仅视频片段)',
    `thumbnail_url` VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径',
    `thumbnail_data` LONGBLOB DEFAULT NULL COMMENT '缩略图二进制数据(JPEG)',
    `capture_time` DATETIME NOT NULL COMMENT '采集时间',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '证据描述',
    `analysis_result` VARCHAR(500) DEFAULT NULL COMMENT 'AI分析结果(JSON)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_exam_student` (`exam_id`, `student_id`),
    KEY `idx_violation` (`violation_id`),
    KEY `idx_capture_time` (`capture_time`)
) ENGINE=InnoDB COMMENT='视频证据表';

-- -------------------------------------------
-- 18. 帧分析记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `frame_analysis_log`;
CREATE TABLE `frame_analysis_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `face_count` INT NOT NULL DEFAULT 0 COMMENT '检测到的人脸数量',
    `head_pose` VARCHAR(100) DEFAULT NULL COMMENT '头部姿态(roll,pitch,yaw)',
    `is_looking_away` TINYINT DEFAULT 0 COMMENT '是否东张西望(1-是 0-否)',
    `forbidden_detected` TINYINT DEFAULT 0 COMMENT '是否检测到违禁物品(1-是 0-否)',
    `confidence` DOUBLE DEFAULT NULL COMMENT '检测置信度',
    `processing_time_ms` INT DEFAULT NULL COMMENT '处理耗时(毫秒)',
    `frame_time` DATETIME NOT NULL COMMENT '帧时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_exam_student` (`exam_id`, `student_id`),
    KEY `idx_frame_time` (`frame_time`)
) ENGINE=InnoDB COMMENT='帧分析记录表';

-- -------------------------------------------
-- 19. 错题练习记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `practice_record`;
CREATE TABLE `practice_record` (
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
-- 20. 练习答题记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `practice_answer`;
CREATE TABLE `practice_answer` (
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

-- -------------------------------------------
-- 21. 自助考试表
-- -------------------------------------------
DROP TABLE IF EXISTS `self_study_exam`;
CREATE TABLE `self_study_exam` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '自助考试ID',
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
    `stage_key` VARCHAR(20) COMMENT '所属复习阶段（如 stage-2），普通自助考试为空',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_exam_code` (`exam_code`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自助考试表';

-- -------------------------------------------
-- 22. 自助考试参与记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `self_study_participant`;
CREATE TABLE `self_study_participant` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    `exam_id` BIGINT NOT NULL COMMENT '自助考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `is_creator` TINYINT NOT NULL DEFAULT 0 COMMENT '是否创建者: 0-否 1-是',
    `join_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY `uk_exam_student` (`exam_id`, `student_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='自助考试参与记录';

-- -------------------------------------------
-- 23. 操作日志表
-- -------------------------------------------
DROP TABLE IF EXISTS `operation_log`;
CREATE TABLE `operation_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `user_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `username` VARCHAR(50) DEFAULT NULL COMMENT '操作人用户名',
    `operation` VARCHAR(200) NOT NULL COMMENT '操作描述',
    `method` VARCHAR(200) DEFAULT NULL COMMENT '请求方法',
    `params` TEXT DEFAULT NULL COMMENT '请求参数',
    `ip` VARCHAR(50) DEFAULT NULL COMMENT 'IP地址',
    `execution_time` BIGINT DEFAULT NULL COMMENT '执行时间(ms)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB COMMENT='操作日志表';

-- -------------------------------------------
-- 24. 系统配置表
-- -------------------------------------------
DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '配置ID',
    `config_key` VARCHAR(100) NOT NULL COMMENT '配置键',
    `config_value` VARCHAR(500) NOT NULL COMMENT '配置值',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '配置描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB COMMENT='系统配置表';

-- ============================================
-- 二、初始数据
-- ============================================

-- 初始化角色数据
INSERT INTO `role` (`id`, `role_name`, `role_key`, `description`) VALUES
(1, '管理员', 'admin', '系统管理员，拥有所有权限'),
(2, '教师', 'teacher', '教师，管理题目、试卷、考试'),
(3, '学生', 'student', '学生，参加考试、查看成绩');

-- 初始化演示账号（admin / teacher1 / student1 / student2）
-- ⚠️ 四个账号共用同一个演示密码，仅用于本地调试。
--    任何对公网开放的部署都必须先重置这些密码或直接删除演示账号，
--    不要把它们带上线。明文密码见 README 的「测试账号」一节。
INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `student_no`, `role_id`) VALUES
(1, 'admin', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '管理员', NULL, 1),
(2, 'teacher1', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '张老师', 'T001', 2),
(3, 'student1', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '李同学', 'S001', 3),
(4, 'student2', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '王同学', 'S002', 3);

-- 初始化知识点标签
INSERT INTO `knowledge_tag` (`name`, `parent_id`, `sort_order`) VALUES
('Java基础', NULL, 1),
('Spring框架', NULL, 2),
('数据库', NULL, 3),
('前端技术', NULL, 4),
('网络与安全', NULL, 5),
('数据结构', NULL, 6);

-- 初始化示例题目
INSERT INTO `question` (`id`, `type`, `content`, `option_a`, `option_b`, `option_c`, `option_d`, `answer`, `analysis`, `score`, `difficulty`, `creator_id`) VALUES
(1, 1, 'Java中，以下哪个关键字用于定义一个类？', 'class', 'interface', 'struct', 'define', 'A', 'Java使用class关键字定义类', 5, 1, 2),
(2, 1, 'Spring Boot中，@Autowired注解的作用是？', '定义配置类', '自动装配Bean', '声明接口', '标记控制器', 'B', '@Autowired是Spring的自动装配注解', 5, 2, 2),
(3, 1, 'MySQL中，以下哪个语句用于创建数据库？', 'CREATE DATABASE', 'NEW DATABASE', 'MAKE DATABASE', 'BUILD DATABASE', 'A', 'CREATE DATABASE是标准SQL语句', 5, 1, 2),
(4, 2, '以下哪些是Java的基本数据类型？（多选）', 'int', 'String', 'boolean', 'double', 'A,C,D', 'String是引用类型，其余为基本数据类型', 10, 3, 2),
(5, 2, 'Spring框架的核心特性包括？（多选）', 'IoC（控制反转）', 'AOP（面向切面）', 'MVC', 'JDBC直接操作', 'A,B,C', 'Spring核心是IoC和AOP，也提供MVC框架', 10, 3, 2),
(6, 3, 'HTTP协议是无状态协议。', '正确', '错误', NULL, NULL, 'true', 'HTTP本身是无状态的，每次请求都是独立的', 5, 1, 2),
(7, 3, 'Vue 3使用Options API作为唯一的组合方式。', '正确', '错误', NULL, NULL, 'false', 'Vue 3新增了Composition API作为另一种组合方式', 5, 1, 2),
(8, 1, 'Git中，用于查看提交历史的命令是？', 'git status', 'git log', 'git diff', 'git show', 'B', 'git log用于查看提交历史记录', 5, 2, 2),
(9, 1, 'MyBatis中，#{}和${}的区别是？', '没有区别', '#{}防SQL注入，${}不防', '#{}不防SQL注入', '$#{}更安全', 'B', '#{}使用预编译参数，${}直接拼接SQL', 5, 2, 2),
(10, 3, 'Redis是关系型数据库。', '正确', '错误', NULL, NULL, 'false', 'Redis是NoSQL内存键值数据库', 5, 1, 2);

-- 给题目打标签
INSERT INTO `question_tag` (`question_id`, `tag_id`) VALUES
(1, 1), (4, 1), (5, 2), (2, 2), (3, 3), (6, 5),
(7, 4), (8, 1), (9, 2), (10, 3);

-- 初始化系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('max_switch_count', '3', '默认最大切屏次数'),
('exam_auto_submit', 'true', '考试超时自动提交'),
('system_name', '在线考试平台', '系统名称'),
('paper_gen_strategy', 'greedy', '组卷策略(greedy-贪心, random-随机)'),
('paper_gen_seed', '42', '组卷随机种子（可重现）'),
('face_verify_threshold', '0.75', '人脸比对通过阈值'),
('face_verify_enabled', 'true', '是否启用人脸核验功能'),
('ai_monitor_enabled', 'true', '是否启用AI实时监控'),
('ai_face_detect_interval', '3', 'AI人脸检测间隔(秒)'),
('ai_head_pose_threshold', '30', '头部偏转报警阈值(角度)'),
('ai_multi_face_alert', 'true', '多人脸检测报警开关'),
('ai_forbidden_detect', 'true', '违禁物品检测开关'),
('video_evidence_duration', '10', '违规视频录制时长(秒)'),
('video_evidence_enabled', 'true', '视频证据录制开关'),
('heartbeat_timeout_seconds', '60', '心跳超时时间(秒)'),
('offline_cache_enabled', 'true', '断网离线缓存开关');
