-- -------------------------------------------
-- 复习路径：自助考试表增加阶段标识
-- -------------------------------------------
-- 新库由 schema.sql 直接建出该列，无需执行本脚本。
-- 已有的库执行一次即可（幂等性依赖 MySQL 无 IF NOT EXISTS，重复执行会报重复列，属预期）。

ALTER TABLE `self_study_exam`
    ADD COLUMN `stage_key` VARCHAR(20)
        COMMENT '所属复习阶段（如 stage-2），普通自助考试为空'
        AFTER `generate_prompt`;

-- 按阶段查询是高频操作（每次打开复习路径页），补一个索引
ALTER TABLE `self_study_exam`
    ADD INDEX `idx_creator_stage` (`creator_id`, `stage_key`);
