-- =====================================================
-- 在线考试平台 - 数据库完整性检查与修复脚本
-- 数据库: MySQL 8.0+ / 9.x / exam_platform
-- 版本: 1.1 (2026-06-28)
--
-- 用法:
--   1. 只检查（不修复）: 执行全部 SELECT 语句查看报告
--   2. 检查+自动修复: 取消注释末尾 "修复脚本" 部分的语句
--
-- 检查项分类:
--   🔴 CRITICAL - 数据丢失/核心功能受影响，必须立即修复
--   🟡 WARNING  - 数据不一致/可能引发异常，建议尽快处理
--   💡 INFO     - 建议优化/非紧急问题，可后续处理
-- =====================================================

USE exam_platform;

-- ============================================
-- 检查报告头
-- ============================================
SELECT '============================================================' AS '══════';
SELECT '📊 在线考试平台 - 数据库完整性检查报告' AS '标题';
SELECT CONCAT('⏰  检查时间: ', NOW()) AS '时间';
SELECT CONCAT('🔧  数据库版本: ', VERSION()) AS '数据库版本';
SELECT '============================================================' AS '══════';


-- ═══════════════════════════════════════════════════════════════════
-- 第一部分：基础数据存在性检查（CRITICAL）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第一部分：基础数据存在性检查 [🔴关键]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 1.1 角色表：必须至少有3个角色(admin/teacher/student)
SELECT
    (SELECT COUNT(*) FROM `role` WHERE deleted = 0) AS '角色总数',
    CASE
        WHEN (SELECT COUNT(*) FROM `role` WHERE deleted = 0) >= 3 THEN '✅ 正常'
        ELSE CONCAT('🔴 异常: 需要3个角色(admin/teacher/student)，当前仅', (SELECT COUNT(*) FROM `role` WHERE deleted = 0), '个')
    END AS '状态',
    '角色是权限系统的基础，缺少会导致用户无法正常使用系统' AS '说明';

-- 1.2 用户表：必须有至少1个管理员和1个教师
SELECT
    (SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND role_id = 1) AS '管理员数',
    (SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND role_id = 2) AS '教师数',
    (SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND role_id = 3) AS '学生数',
    CASE
        WHEN (SELECT COUNT(*) FROM `user` WHERE deleted = 0 AND role_id IN (1,2)) >= 2 THEN '✅ 正常'
        ELSE CONCAT('🔴 异常: 管理员或教师账号缺失')
    END AS '状态';

-- 1.3 系统配置表：关键配置项检查
SELECT
    config_key AS '配置键',
    config_value AS '当前值',
    description AS '说明',
    CASE
        WHEN config_key IN ('max_switch_count','exam_auto_submit','system_name') AND config_value IS NOT NULL THEN '✅'
        WHEN config_key NOT IN ('max_switch_count','exam_auto_submit','system_name') THEN '—'
        ELSE '🔴 缺失'
    END AS '状态'
FROM system_config
WHERE config_key IN ('max_switch_count','exam_auto_submit','system_name')
ORDER BY config_key;


-- ═══════════════════════════════════════════════════════════════════
-- 第二部分：试卷-题目关联完整性检查（CRITICAL）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第二部分：试卷-题目关联完整性 [🔴关键]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 2.1 孤立试卷：引用了不存在的paper_id的考试
SELECT
    e.id AS '考试ID',
    e.title AS '考试名称',
    e.paper_id AS '引用的试卷ID',
    CASE WHEN p.id IS NULL THEN '🔴 试卷不存在' ELSE '✅ 存在' END AS '试卷状态',
    e.status AS '考试状态'
FROM exam e
LEFT JOIN paper p ON e.paper_id = p.id
WHERE e.deleted = 0
  AND p.id IS NULL;

-- 2.2 空试卷：试卷没有关联任何题目
SELECT
    p.id AS '试卷ID',
    p.title AS '试卷名称',
    p.question_count AS '记录的题目数',
    COALESCE(pq.actual_count, 0) AS '实际关联题目数',
    CASE
        WHEN COALESCE(pq.actual_count, 0) = 0 THEN '🔴 空试卷！考生进入后看不到题目'
        WHEN COALESCE(pq.actual_count, 0) != p.question_count THEN '🟡 数量不匹配'
        ELSE '✅ 正常'
    END AS '状态'
FROM paper p
LEFT JOIN (
    SELECT paper_id, COUNT(*) AS actual_count
    FROM paper_question
    GROUP BY paper_id
) pq ON p.id = pq.paper_id
WHERE p.deleted = 0;

-- 2.3 孤立题目引用：paper_question引用了已删除或不存在的题目
SELECT
    pq.id AS '关联记录ID',
    pq.paper_id AS '试卷ID',
    pq.question_id AS '题目ID',
    CASE WHEN q.id IS NULL THEN '🔴 题目不存在(已删除?)' ELSE '✅' END AS '题目状态',
    q.content AS '题干(前50字符)'
FROM paper_question pq
LEFT JOIN question q ON pq.question_id = q.id AND q.deleted = 0
WHERE q.id IS NULL;

-- 2.4 试卷总分与题目分值汇总不一致
SELECT
    p.id AS '试卷ID',
    p.title AS '试卷名称',
    p.total_score AS '记录的总分',
    COALESCE(SUM(pq.score), 0) AS '实际分值汇总',
    CASE
        WHEN ABS(p.total_score - COALESCE(SUM(pq.score), 0)) > 0 THEN '🟡 分值不一致'
        ELSE '✅ 一致'
    END AS '状态'
FROM paper p
LEFT JOIN paper_question pq ON p.id = pq.paper_id
LEFT JOIN question q ON pq.question_id = q.id AND q.deleted = 0
WHERE p.deleted = 0
GROUP BY p.id, p.title, p.total_score
HAVING ABS(p.total_score - COALESCE(SUM(pq.score), 0)) > 0 OR p.total_score = 0;


-- ═══════════════════════════════════════════════════════════════════
-- 第三部分：考试数据一致性检查（WARNING）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第三部分：考试数据一致性检查 [🟡警告]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 3.1 考试时间异常：结束时间早于开始时间
SELECT
    id AS '考试ID',
    title AS '考试名称',
    start_time AS '开始时间',
    end_time AS '结束时间',
    duration AS '时长(分钟)',
    TIMESTAMPDIFF(MINUTE, start_time, end_time) AS '实际间隔(分钟)',
    '🔴 时间设置错误！结束早于开始' AS '问题'
FROM exam
WHERE deleted = 0
  AND end_time <= start_time;

-- 3.2 考试时长与起止时间不匹配（差异超过5分钟视为异常）
SELECT
    id AS '考试ID',
    title AS '考试名称',
    duration AS '配置时长(分钟)',
    TIMESTAMPDIFF(MINUTE, start_time, end_time) AS '实际间隔(分钟)',
    ABS(duration - TIMESTAMPDIFF(MINUTE, start_time, end_time)) AS '差异(分钟)',
    '🟡 配置时长与实际时间窗口不匹配' AS '问题'
FROM exam
WHERE deleted = 0
  AND end_time > start_time
  AND ABS(duration - TIMESTAMPDIFF(MINUTE, start_time, end_time)) > 5;

-- 3.3 考试状态与时间不符（定时任务可能未更新）
SELECT
    id AS '考试ID',
    title AS '考试名称',
    status AS '当前状态',
    CASE status
        WHEN 0 THEN '未开始'
        WHEN 1 THEN '进行中'
        WHEN 2 THEN '已结束'
    END AS '状态名',
    start_time AS '开始时间',
    end_time AS '结束时间',
    NOW() AS '当前服务器时间',
    CASE
        WHEN status = 0 AND NOW() >= start_time AND NOW() < end_time THEN '🟡 应为"进行中"'
        WHEN status = 1 AND NOW() >= end_time THEN '🟡 应为"已结束"'
        WHEN status = 0 AND NOW() >= end_time THEN '🔴 已错过考试时间但状态仍为未开始'
        ELSE '✅ 状态与时间一致'
    END AS '状态检查'
FROM exam
WHERE deleted = 0
  AND (
    (status = 0 AND NOW() >= start_time)
    OR (status = 1 AND NOW() >= end_time)
  );

-- 3.4 白名单中引用了不存在的学生
SELECT
    ew.id AS '白名单ID',
    ew.exam_id AS '考试ID',
    ew.student_id AS '学生ID',
    CASE WHEN u.id IS NULL THEN '🔴 学生账号不存在或已删除' ELSE '✅' END AS '学生状态',
    u.username AS '用户名',
    u.real_name AS '姓名'
FROM exam_whitelist ew
LEFT JOIN `user` u ON ew.student_id = u.id AND u.deleted = 0
WHERE u.id IS NULL;

-- 3.5 白名单中引用了已删除的考试
SELECT
    ew.id AS '白名单ID',
    ew.exam_id AS '考试ID',
    CASE WHEN e.id IS NULL THEN '🔴 考试不存在或已删除' ELSE '✅' END AS '考试状态',
    e.title AS '考试名称'
FROM exam_whitelist ew
LEFT JOIN exam e ON ew.exam_id = e.id AND e.deleted = 0
WHERE e.id IS NULL;


-- ═══════════════════════════════════════════════════════════════════
-- 第四部分：考试记录与答题数据完整性（CRITICAL）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第四部分：考试记录与答题数据 [🔴关键]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 4.1 孤立的考试记录：引用了已删除的考试
SELECT
    er.id AS '记录ID',
    er.exam_id AS '考试ID',
    er.student_id AS '学生ID',
    er.status AS '答卷状态',
    CASE WHEN e.id IS NULL THEN '🔴 原始考试已删除！' ELSE '✅' END AS '考试状态'
FROM exam_record er
LEFT JOIN exam e ON er.exam_id = e.id AND e.deleted = 0
WHERE er.deleted = 0 AND e.id IS NULL;

-- 4.2 答题记录引用了不存在的考试记录
SELECT
    ar.id AS '答题ID',
    ar.record_id AS '考试记录ID',
    ar.question_id AS '题目ID',
    CASE WHEN er.id IS NULL THEN '🔴 原始考试记录不存在！' ELSE '✅' END AS '记录状态'
FROM answer_record ar
LEFT JOIN exam_record er ON ar.record_id = er.id AND er.deleted = 0
WHERE er.id IS NULL;

-- 4.3 答题记录引用了已删除的题目
SELECT
    ar.id AS '答题ID',
    ar.record_id AS '考试记录ID',
    ar.question_id AS '题目ID',
    CASE WHEN q.id IS NULL THEN '🔴 题目已删除！' ELSE '✅' END AS '题目状态',
    q.content AS '题干(截断)'
FROM answer_record ar
LEFT JOIN question q ON ar.question_id = q.id AND q.deleted = 0
WHERE q.id IS NULL;

-- 4.4 已提交的考试记录但没有答题记录
SELECT
    er.id AS '考试记录ID',
    er.exam_id AS '考试ID',
    er.student_id AS '学生ID',
    er.status AS '状态',
    er.submit_time AS '提交时间',
    (SELECT COUNT(*) FROM answer_record ar WHERE ar.record_id = er.id) AS '答题数',
    CASE
        WHEN er.status IN (1, 2) AND (SELECT COUNT(*) FROM answer_record ar WHERE ar.record_id = er.id) = 0
        THEN '🔴 已提交但无答题记录！'
        ELSE '✅'
    END AS '问题'
FROM exam_record er
WHERE er.deleted = 0
  AND er.status IN (1, 2)
  AND (SELECT COUNT(*) FROM answer_record ar WHERE ar.record_id = er.id) = 0;

-- 4.5 考试记录得分超出合理范围
SELECT
    er.id AS '记录ID',
    er.exam_id AS '考试ID',
    er.score AS '得分',
    e.pass_score AS '及格分',
    p.total_score AS '试卷满分',
    CASE
        WHEN er.score < 0 THEN '🔴 得分为负数！'
        WHEN er.score > COALESCE(p.total_score, 100) THEN '🟡 得分超过试卷满分'
        ELSE '✅ 正常'
    END AS '状态'
FROM exam_record er
LEFT JOIN exam e ON er.exam_id = e.id
LEFT JOIN paper p ON e.paper_id = p.id
WHERE er.deleted = 0
  AND (er.score < 0 OR er.score > COALESCE(p.total_score, 9999));


-- ═══════════════════════════════════════════════════════════════════
-- 第五部分：阶段二~五扩展表检查（WARNING/INFO）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第五部分：扩展模块数据检查 [🟡警告]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 5.1 题目标签关联中的孤立引用
SELECT
    qt.id AS '标签关联ID',
    qt.question_id AS '题目ID',
    qt.tag_id AS '标签ID',
    CASE WHEN q.id IS NULL THEN '🔴 题目已删除' ELSE '✅' END AS '题目状态',
    CASE WHEN t.id IS NULL THEN '🔴 标签已删除' ELSE '✅' END AS '标签状态'
FROM question_tag qt
LEFT JOIN question q ON qt.question_id = q.id AND q.deleted = 0
LEFT JOIN knowledge_tag t ON qt.tag_id = t.id AND t.deleted = 0
WHERE q.id IS NULL OR t.id IS NULL;

-- 5.2 人脸信息表中引用了不存在的用户
SELECT
    uf.id AS '人脸记录ID',
    uf.user_id AS '用户ID',
    CASE WHEN u.id IS NULL THEN '🔴 用户不存在' ELSE '✅' END AS '用户状态',
    uf.status AS '人脸状态'
FROM user_face uf
LEFT JOIN `user` u ON uf.user_id = u.id AND u.deleted = 0
WHERE u.id IS NULL;

-- 5.3 自助考试(self_study_exam)关联检查
SELECT
    ss.id AS '自助考试ID',
    ss.title AS '标题',
    ss.paper_id AS '关联试卷ID',
    ss.creator_id AS '创建者ID',
    ss.exam_code AS '考试码',
    CASE WHEN ss.paper_id IS NOT NULL AND p.id IS NULL THEN '🔴 关联试卷不存在' ELSE '✅' END AS '试卷状态',
    CASE WHEN u.id IS NULL THEN '🔴 创建者不存在' ELSE '✅' END AS '创建者状态'
FROM self_study_exam ss
LEFT JOIN paper p ON ss.paper_id = p.id AND p.deleted = 0
LEFT JOIN `user` u ON ss.creator_id = u.id AND u.deleted = 0
WHERE ss.deleted = 0
  AND ((ss.paper_id IS NOT NULL AND p.id IS NULL) OR u.id IS NULL);

-- 5.4 练习记录与练习答题的一致性
SELECT
    pr.id AS '练习记录ID',
    pr.student_id AS '学生ID',
    pr.total_count AS '总题数',
    (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id) AS '实际答题数',
    pr.correct_count AS '正确数',
    (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id AND pa.is_correct = 1) AS '实际正确数',
    CASE
        WHEN pr.total_count != (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id)
        THEN '🟡 总题数与答题记录不一致'
        WHEN pr.correct_count != (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id AND pa.is_correct = 1)
        THEN '🟡 正确数与答题记录不一致'
        ELSE '✅ 一致'
    END AS '状态'
FROM practice_record pr
WHERE pr.deleted = 0
  AND (
    pr.total_count != (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id)
    OR pr.correct_count != (SELECT COUNT(*) FROM practice_answer pa WHERE pa.practice_id = pr.id AND pa.is_correct = 1)
  );

-- 5.5 违规记录关联检查
SELECT
    vr.id AS '违规ID',
    vr.exam_id AS '考试ID',
    vr.student_id AS '学生ID',
    vr.violation_type AS '类型',
    CASE WHEN e.id IS NULL THEN '🔴 考试已删除' ELSE '✅' END AS '考试状态',
    CASE WHEN u.id IS NULL THEN '🔴 学生已删除' ELSE '✅' END AS '学生状态'
FROM violation_record vr
LEFT JOIN exam e ON vr.exam_id = e.id AND e.deleted = 0
LEFT JOIN `user` u ON vr.student_id = u.id AND u.deleted = 0
WHERE e.id IS NULL OR u.id IS NULL;

-- 5.6 二进制数据列缺失检查（🔴 CRITICAL - 会导致查询失败）
SELECT '──────────────────────────────────────────────' AS '';
SELECT '🔍 5.6 二进制数据列缺失检查 [🔴关键]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- user表 - avatar_data列
SELECT
    'user.avatar_data' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
              AND COLUMN_NAME = 'avatar_data'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '用户头像二进制数据列，缺失会导致用户查询失败' AS '说明';

-- user表 - gender列
SELECT
    'user.gender' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user'
              AND COLUMN_NAME = 'gender'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '用户性别列，缺失会导致个人中心性别保存失败' AS '说明';

-- user_face表 - photo_data列
SELECT
    'user_face.photo_data' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'user_face'
              AND COLUMN_NAME = 'photo_data'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '人脸注册照片二进制数据列，缺失会导致人脸核验失败' AS '说明';

-- face_verify_record表 - verify_photo_data列
SELECT
    'face_verify_record.verify_photo_data' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'face_verify_record'
              AND COLUMN_NAME = 'verify_photo_data'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '人脸核验照片二进制数据列，缺失会导致核验记录保存失败' AS '说明';

-- video_evidence表 - file_data列
SELECT
    'video_evidence.file_data' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'video_evidence'
              AND COLUMN_NAME = 'file_data'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '视频证据二进制数据列，缺失会导致视频证据保存失败' AS '说明';

-- video_evidence表 - thumbnail_data列
SELECT
    'video_evidence.thumbnail_data' AS '列名',
    CASE
        WHEN EXISTS (
            SELECT * FROM information_schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'video_evidence'
              AND COLUMN_NAME = 'thumbnail_data'
        ) THEN '✅ 存在'
        ELSE '🔴 缺失'
    END AS '状态',
    '视频缩略图二进制数据列，缺失会导致视频证据查询失败' AS '说明';


-- ═══════════════════════════════════════════════════════════════════
-- 第六部分：统计概览与索引健康检查（INFO）
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '──────────────────────────────────────────────' AS '';
SELECT '📋 第六部分：数据统计概览 [💡信息]' AS '';
SELECT '──────────────────────────────────────────────' AS '';

-- 6.1 各表数据量统计
SELECT 'user' AS '表名', COUNT(*) AS '总行数', SUM(deleted) AS '已删除', COUNT(*) - SUM(deleted) AS '有效数' FROM `user`
UNION ALL SELECT 'role', COUNT(*), IFNULL(SUM(deleted),0), COUNT(*) - IFNULL(SUM(deleted),0) FROM `role`
UNION ALL SELECT 'question', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `question`
UNION ALL SELECT 'paper', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `paper`
UNION ALL SELECT 'paper_question', COUNT(*), 0, COUNT(*) FROM `paper_question`
UNION ALL SELECT 'exam', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `exam`
UNION ALL SELECT 'exam_whitelist', COUNT(*), 0, COUNT(*) FROM `exam_whitelist`
UNION ALL SELECT 'exam_record', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `exam_record`
UNION ALL SELECT 'answer_record', COUNT(*), 0, COUNT(*) FROM `answer_record`
UNION ALL SELECT 'violation_record', COUNT(*), 0, COUNT(*) FROM `violation_record`
UNION ALL SELECT 'practice_record', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `practice_record`
UNION ALL SELECT 'self_study_exam', COUNT(*), SUM(deleted), COUNT(*) - SUM(deleted) FROM `self_study_exam`;

-- 6.2 唯一索引冲突预检（exam_record）
SELECT
    CONCAT('exam_id=', exam_id, ', student_id=', student_id) AS '唯一键值',
    COUNT(*) AS '重复次数'
FROM exam_record
WHERE deleted = 0
GROUP BY exam_id, student_id
HAVING COUNT(*) > 1;

-- 6.3 自助考试码冲突预检
SELECT
    exam_code AS '考试码',
    COUNT(*) AS '重复次数'
FROM self_study_exam
WHERE deleted = 0
GROUP BY exam_code
HAVING COUNT(*) > 1;


-- ═══════════════════════════════════════════════════════════════════
-- 第七部分：问题汇总
-- ═══════════════════════════════════════════════════════════════════

SELECT '' AS '';
SELECT '============================================================' AS '══════';
SELECT '📋 检查完成 - 问题汇总' AS '标题';
SELECT '============================================================' AS '══════';

SELECT
    '以上各查询结果中标记 🔴 的项为 CRITICAL 问题，需立即修复' AS '重要提示1';
SELECT
    '标记 🟡 的项为 WARNING 建议，应尽快处理' AS '重要提示2';
SELECT
    '如所有查询结果均为空或显示 ✅，则数据完整无问题' AS '重要提示3';


-- ═══════════════════════════════════════════════════════════════════
-- 第八部分：自动修复脚本（谨慎执行！先备份！）
-- ⚠️ 执行前请务必先备份数据库！
-- ═══════════════════════════════════════════════════════════════════

/*
-- ================================================================
-- 【警告】以下为自动修复SQL，取消注释后逐条执行
-- 建议在维护窗口期执行，并先做全量备份
-- ================================================================

-- Fix 0: 补全缺失的二进制数据列（🔴 必须先执行！否则查询会报错）
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `avatar_data` LONGBLOB DEFAULT NULL COMMENT '头像二进制数据' AFTER `avatar`;
ALTER TABLE `user` ADD COLUMN IF NOT EXISTS `gender` VARCHAR(20) DEFAULT 'unknown' COMMENT '性别(male-男 female-女 unknown-保密)' AFTER `phone`;
ALTER TABLE `user_face` ADD COLUMN IF NOT EXISTS `photo_data` LONGBLOB DEFAULT NULL COMMENT '注册照片二进制数据' AFTER `photo_url`;
ALTER TABLE `face_verify_record` ADD COLUMN IF NOT EXISTS `verify_photo_data` LONGBLOB DEFAULT NULL COMMENT '核验照片二进制数据' AFTER `verify_photo_url`;
ALTER TABLE `video_evidence` ADD COLUMN IF NOT EXISTS `file_data` LONGBLOB DEFAULT NULL COMMENT '文件二进制数据(MP4视频或JPEG截图)' AFTER `file_url`;
ALTER TABLE `video_evidence` ADD COLUMN IF NOT EXISTS `thumbnail_data` LONGBLOB DEFAULT NULL COMMENT '缩略图二进制数据(JPEG)' AFTER `thumbnail_url`;

-- Fix 1: 同步试卷的question_count字段（基于实际关联数）
UPDATE paper p
INNER JOIN (
    SELECT paper_id, COUNT(*) as cnt FROM paper_question GROUP BY paper_id
) pq ON p.id = pq.paper_id
SET p.question_count = pq.cnt
WHERE p.question_count != pq.cnt;

-- Fix 2: 同步试卷的total_score字段
UPDATE paper p
INNER JOIN (
    SELECT paper_id, SUM(score) as total FROM paper_question GROUP BY paper_id
) pq ON p.id = pq.paper_id
SET p.total_score = pq.total
WHERE p.total_score != pq.total;

-- Fix 3: 清理指向已删除考试的考试记录（物理删除）
DELETE FROM answer_record
WHERE record_id IN (
    SELECT id FROM exam_record
    WHERE deleted = 1 OR exam_id NOT IN (SELECT id FROM exam WHERE deleted = 0)
);
DELETE FROM violation_record
WHERE exam_id NOT IN (SELECT id FROM exam WHERE deleted = 0);

-- Fix 4: 清理指向已删除题目的答题记录
DELETE FROM answer_record
WHERE question_id NOT IN (SELECT id FROM question WHERE deleted = 0);

-- Fix 5: 清理无效的白名单记录（学生或考试已删除）
DELETE FROM exam_whitelist
WHERE student_id NOT IN (SELECT id FROM `user` WHERE deleted = 0)
   OR exam_id NOT IN (SELECT id FROM exam WHERE deleted = 0);

-- Fix 6: 清理孤立的题目标签关联
DELETE FROM question_tag
WHERE question_id NOT IN (SELECT id FROM question WHERE deleted = 0)
   OR tag_id NOT IN (SELECT id FROM knowledge_tag WHERE deleted = 0);

-- Fix 7: 更新考试状态（根据当前时间修正）
UPDATE exam SET status = 1 WHERE status = 0 AND NOW() >= start_time AND NOW() < end_time;
UPDATE exam SET status = 2 WHERE status = 1 AND NOW() >= end_time;

-- Fix 8: 清理无效的人脸记录
DELETE FROM user_face
WHERE user_id NOT IN (SELECT id FROM `user` WHERE deleted = 0);

*/
