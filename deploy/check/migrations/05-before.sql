-- 期望新表0行、hide_reason新列0行；任一已存在都先排查部分执行。
SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='content_report';
SELECT table_name,column_name,column_type FROM information_schema.columns WHERE table_schema=DATABASE()
AND table_name IN ('community_post','community_reply') AND column_name='hide_reason';
SHOW CREATE TABLE community_post;
SHOW CREATE TABLE community_reply;
SELECT 'post' AS target,status,COUNT(*) AS rows_count FROM community_post GROUP BY status
UNION ALL SELECT 'reply',status,COUNT(*) FROM community_reply GROUP BY status;
