-- ============================================
-- 在线考试平台 - 阶段三 数据库增量脚本
-- 全功能AI防作弊监考（JavaCV深度集成 + 前后端协同实时分析）
-- ============================================

USE exam_platform;

-- -------------------------------------------
-- 1. 考生在线状态表（WebSocket实时维护）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `exam_online_student` (
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
-- 2. 视频证据表（截图和短视频片段）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `video_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '证据ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `violation_id` BIGINT DEFAULT NULL COMMENT '关联违规记录ID',
    `evidence_type` VARCHAR(30) NOT NULL COMMENT '证据类型(screenshot-截图 video_clip-视频片段)',
    `file_url` VARCHAR(500) NOT NULL COMMENT '文件路径',
    `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(bytes)',
    `duration` INT DEFAULT NULL COMMENT '视频时长(秒，仅视频片段)',
    `thumbnail_url` VARCHAR(500) DEFAULT NULL COMMENT '缩略图路径',
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
-- 3. 考试帧分析记录表（AI分析日志）
-- -------------------------------------------
CREATE TABLE IF NOT EXISTS `frame_analysis_log` (
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
-- 4. 违规记录表扩展字段
-- -------------------------------------------
ALTER TABLE `violation_record`
    ADD COLUMN `severity` TINYINT NOT NULL DEFAULT 1 COMMENT '严重等级(1-低 2-中 3-高)' AFTER `screenshot_url`,
    ADD COLUMN `handled` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已处理(1-是 0-否)' AFTER `severity`,
    ADD COLUMN `video_evidence_id` BIGINT DEFAULT NULL COMMENT '关联视频证据ID' AFTER `handled`;

-- -------------------------------------------
-- 5. 考试记录表扩展字段（断网续传相关）
-- -------------------------------------------
ALTER TABLE `exam_record`
    ADD COLUMN `disconnect_count` INT NOT NULL DEFAULT 0 COMMENT '断网次数' AFTER `switch_count`,
    ADD COLUMN `total_disconnect_duration` INT NOT NULL DEFAULT 0 COMMENT '累计断网时长(秒)' AFTER `disconnect_count`,
    ADD COLUMN `last_disconnect_time` DATETIME DEFAULT NULL COMMENT '最后断网时间' AFTER `total_disconnect_duration`;

-- -------------------------------------------
-- 6. 题目表扩展字段（支持主观题）
-- -------------------------------------------
ALTER TABLE `question`
    ADD COLUMN `type` TINYINT NOT NULL DEFAULT 1 COMMENT '题目类型(1-单选 2-多选 3-判断 4-简答)' FIRST;

-- 修改已有字段的注释已存在，仅做说明
-- type字段原值域: 1-单选 2-多选 3-判断
-- 扩展后值域: 1-单选 2-多选 3-判断 4-简答(主观题)

-- 答题记录表扩展字段（主观题批改）
ALTER TABLE `answer_record`
    ADD COLUMN `teacher_score` INT DEFAULT NULL COMMENT '教师评分(主观题)' AFTER `score`,
    ADD COLUMN `teacher_comment` VARCHAR(1000) DEFAULT NULL COMMENT '教师批注(主观题)' AFTER `teacher_score`;

-- -------------------------------------------
-- 7. 系统配置增量
-- -------------------------------------------
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('ai_monitor_enabled', 'true', '是否启用AI实时监控'),
('ai_face_detect_interval', '3', 'AI人脸检测间隔(秒)'),
('ai_head_pose_threshold', '30', '头部偏转报警阈值(角度)'),
('ai_multi_face_alert', 'true', '多人脸检测报警开关'),
('ai_forbidden_detect', 'true', '违禁物品检测开关'),
('video_evidence_duration', '10', '违规视频录制时长(秒)'),
('video_evidence_enabled', 'true', '视频证据录制开关'),
('heartbeat_timeout_seconds', '60', '心跳超时时间(秒)'),
('offline_cache_enabled', 'true', '断网离线缓存开关');
