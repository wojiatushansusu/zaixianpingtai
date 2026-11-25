-- -------------------------------------------
-- 修复「中文双重编码」乱码（存量库）
-- -------------------------------------------
-- 症状：所有来自数据的中文都变成 `ç®¡çå`、`å¼ èå¸` 这样的乱码，
--       而前端写死的文字正常。接口 200、日志干净、页面能开——只有肉眼能发现。
--
-- 成因：docker-entrypoint-initdb.d 执行 schema.sql 时，mysql 客户端默认以 latin1 连接。
--       脚本里的 UTF-8 中文被当作 latin1 解释，再以 utf8mb4 存进去 → 每个汉字编码两次。
--       schema.sql 已加 `SET NAMES utf8mb4`，但**只对新建库生效**，已经乱掉的库要跑本脚本。
--
-- ⚠️ 执行前必须做两件事：
--   1. 备份：mysqldump 整个 exam_platform
--   2. 确认确实是双重编码（见下面「第一步」），**对正确数据执行会造成不可逆损坏**
--
-- 只对「乱码」列执行，不要全库盲跑。

USE exam_platform;

-- ── 第一步：确认是不是双重编码 ──
-- 乱码的 hex 会是 E7 9B ... 这种「UTF-8 字节被再编码」的样子；
-- 正确的中文（如 管理员）hex 应为 E7AEA1E79086E59198。
SELECT username, real_name, HEX(real_name) AS hex_value
FROM `user`
WHERE username = 'admin';

-- 如果 hex_value 长度是正常值的两倍左右（如 20 字节），即为双重编码，可以继续。

-- ── 第二步：列出所有文本列，生成修复语句 ──
-- 把输出的每一行拿去执行（或复制出来批量跑）。
-- 注意排除已经是正确 UTF-8 的表/列。
SELECT CONCAT(
    'UPDATE `', table_name, '` SET `', column_name,
    '` = CONVERT(CAST(CONVERT(`', column_name, '` USING latin1) AS BINARY) USING utf8mb4)',
    ' WHERE `', column_name, '` IS NOT NULL;'
) AS fix_sql
FROM information_schema.columns
WHERE table_schema = 'exam_platform'
  AND data_type IN ('char', 'varchar', 'text', 'mediumtext', 'longtext', 'tinytext')
ORDER BY table_name, ordinal_position;

-- ── 第三步：演示一条（按需逐条执行）──
-- UPDATE `user`
-- SET `real_name` = CONVERT(CAST(CONVERT(`real_name` USING latin1) AS BINARY) USING utf8mb4)
-- WHERE `real_name` IS NOT NULL;

-- ── 第四步：验证 ──
-- 期望：管理员 / 张老师 / 李同学 / 王同学
SELECT username, real_name, HEX(real_name) AS hex_value
FROM `user`
WHERE username IN ('admin', 'teacher1', 'student1', 'student2');

-- 期望全是 0 行：如果还有「仍像乱码」的行，说明该列不是双重编码，别再动它。
