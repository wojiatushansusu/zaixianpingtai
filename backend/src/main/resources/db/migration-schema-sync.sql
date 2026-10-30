-- -------------------------------------------
-- 补齐实体已声明、建表脚本漏掉的列
-- -------------------------------------------
-- 起因：部署时发现两处「实体有字段、表里没有列」，导致运行期 SQL 报
-- Unknown column。MyBatis-Plus 的全局逻辑删除还会给每次查询补 WHERE deleted = 0，
-- 所以 answer_record 缺 deleted 这一列足以让交卷、批改、错题本全线失败。
--
-- 新库由 schema.sql 直接建出这些列，无需执行本脚本。
-- 已有的库执行一次。两条语句互不影响，重复执行会报「列已存在」，属预期。

-- 答题记录：交卷 / 批改 / 成绩 / 错题本都读这张表
ALTER TABLE `answer_record`
    ADD COLUMN `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    ADD COLUMN `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    ADD COLUMN `deleted` TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除';

-- 在线状态：checkHeartbeat 每 30 秒写一次，缺列会持续刷错误日志
ALTER TABLE `exam_online_student`
    ADD COLUMN `disconnect_count` INT NOT NULL DEFAULT 0 COMMENT '断网次数',
    ADD COLUMN `total_disconnect_duration` INT NOT NULL DEFAULT 0 COMMENT '累计断网时长(秒)';
