SHOW CREATE TABLE content_report;
SHOW CREATE TABLE community_post;
SHOW CREATE TABLE community_reply;
-- 期望新表空、新列全NULL、状态分布与执行前完全一致（迁移不修正历史状态）。
SELECT COUNT(*) AS reports_expected_zero FROM content_report;
SELECT 'post' AS target,status,COUNT(*) AS rows_count,COUNT(hide_reason) AS reasons_expected_zero FROM community_post GROUP BY status
UNION ALL SELECT 'reply',status,COUNT(*),COUNT(hide_reason) FROM community_reply GROUP BY status;
