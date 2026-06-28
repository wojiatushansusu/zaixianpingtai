-- ============================================
-- 在线考试平台 - 阶段一 数据库初始化脚本
-- 数据库: MySQL 8.0
-- ============================================

CREATE DATABASE IF NOT EXISTS exam_platform DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE exam_platform;

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

-- 初始化角色数据
INSERT INTO `role` (`id`, `role_name`, `role_key`, `description`) VALUES
(1, '管理员', 'admin', '系统管理员，拥有所有权限'),
(2, '教师', 'teacher', '教师，管理题目、试卷、考试'),
(3, '学生', 'student', '学生，参加考试、查看成绩');

-- -------------------------------------------
-- 2. 用户表
-- -------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) NOT NULL COMMENT '用户名',
    `password` VARCHAR(200) NOT NULL COMMENT '密码(BCrypt加密)',
    `real_name` VARCHAR(50) DEFAULT NULL COMMENT '真实姓名',
    `email` VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(500) DEFAULT NULL COMMENT '头像路径',
    `role_id` BIGINT NOT NULL COMMENT '角色ID',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1-正常 0-禁用)',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB COMMENT='用户表';

-- 初始化测试用户（密码均为 BCrypt 加密的 123456）
-- 使用 Spring Security BCryptPasswordEncoder 生成的有效哈希
INSERT INTO `user` (`id`, `username`, `password`, `real_name`, `role_id`) VALUES
(1, 'admin', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '管理员', 1),
(2, 'teacher1', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '张老师', 2),
(3, 'student1', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '李同学', 3),
(4, 'student2', '$2a$10$vTHfrCARHh8b5nv4UDoC4.HZuCopkiYq1rmNXb97hCbyD/h7Kk6Wa', '王同学', 3);

-- -------------------------------------------
-- 3. 题目表
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
    `creator_id` BIGINT NOT NULL COMMENT '创建者ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_creator` (`creator_id`)
) ENGINE=InnoDB COMMENT='题目表';

-- 初始化示例题目
INSERT INTO `question` (`id`, `type`, `content`, `option_a`, `option_b`, `option_c`, `option_d`, `answer`, `analysis`, `score`, `creator_id`) VALUES
(1, 1, 'Java中，以下哪个关键字用于定义一个类？', 'class', 'interface', 'struct', 'define', 'A', 'Java使用class关键字定义类', 5, 2),
(2, 1, 'Spring Boot中，@Autowired注解的作用是？', '定义配置类', '自动装配Bean', '声明接口', '标记控制器', 'B', '@Autowired是Spring的自动装配注解', 5, 2),
(3, 1, 'MySQL中，以下哪个语句用于创建数据库？', 'CREATE DATABASE', 'NEW DATABASE', 'MAKE DATABASE', 'BUILD DATABASE', 'A', 'CREATE DATABASE是标准SQL语句', 5, 2),
(4, 2, '以下哪些是Java的基本数据类型？（多选）', 'int', 'String', 'boolean', 'double', 'A,C,D', 'String是引用类型，其余为基本数据类型', 10, 2),
(5, 2, 'Spring框架的核心特性包括？（多选）', 'IoC（控制反转）', 'AOP（面向切面）', 'MVC', 'JDBC直接操作', 'A,B,C', 'Spring核心是IoC和AOP，也提供MVC框架', 10, 2),
(6, 3, 'HTTP协议是无状态协议。', '正确', '错误', NULL, NULL, 'true', 'HTTP本身是无状态的，每次请求都是独立的', 5, 2),
(7, 3, 'Vue 3使用Options API作为唯一的组合方式。', '正确', '错误', NULL, NULL, 'false', 'Vue 3新增了Composition API作为另一种组合方式', 5, 2),
(8, 1, 'Git中，用于查看提交历史的命令是？', 'git status', 'git log', 'git diff', 'git show', 'B', 'git log用于查看提交历史记录', 5, 2),
(9, 1, 'MyBatis中，#{}和${}的区别是？', '没有区别', '#{}防SQL注入，${}不防', '#{}不防SQL注入', '$#{}更安全', 'B', '#{}使用预编译参数，${}直接拼接SQL', 5, 2),
(10, 3, 'Redis是关系型数据库。', '正确', '错误', NULL, NULL, 'false', 'Redis是NoSQL内存键值数据库', 5, 2);

-- -------------------------------------------
-- 4. 试卷表
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
-- 5. 试卷题目关联表
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
-- 6. 考试表
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
-- 7. 考试白名单表
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
-- 8. 考试记录表（考生答卷）
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
-- 9. 答题记录表
-- -------------------------------------------
DROP TABLE IF EXISTS `answer_record`;
CREATE TABLE `answer_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `record_id` BIGINT NOT NULL COMMENT '考试记录ID',
    `question_id` BIGINT NOT NULL COMMENT '题目ID',
    `student_answer` VARCHAR(500) DEFAULT NULL COMMENT '学生答案',
    `is_correct` TINYINT DEFAULT NULL COMMENT '是否正确(1-是 0-否)',
    `score` INT DEFAULT 0 COMMENT '得分',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_record_question` (`record_id`, `question_id`),
    KEY `idx_record_id` (`record_id`)
) ENGINE=InnoDB COMMENT='答题记录表';

-- -------------------------------------------
-- 10. 违规记录表（切屏等）
-- -------------------------------------------
DROP TABLE IF EXISTS `violation_record`;
CREATE TABLE `violation_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `exam_id` BIGINT NOT NULL COMMENT '考试ID',
    `student_id` BIGINT NOT NULL COMMENT '学生ID',
    `violation_type` VARCHAR(50) NOT NULL COMMENT '违规类型',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '违规描述',
    `screenshot_data` LONGBLOB DEFAULT NULL COMMENT '违规截图二进制数据',
    `severity` TINYINT NOT NULL DEFAULT 1 COMMENT '严重等级(1-低 2-中 3-高)',
    `score` INT NOT NULL DEFAULT 0 COMMENT '违规扣分',
    `occur_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '违规时间',
    PRIMARY KEY (`id`),
    KEY `idx_exam_student` (`exam_id`, `student_id`)
) ENGINE=InnoDB COMMENT='违规记录表';

-- -------------------------------------------
-- 11. 操作日志表
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
-- 12. 系统配置表
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

-- 初始化系统配置
INSERT INTO `system_config` (`config_key`, `config_value`, `description`) VALUES
('max_switch_count', '3', '默认最大切屏次数'),
('exam_auto_submit', 'true', '考试超时自动提交'),
('system_name', '在线考试平台', '系统名称');
