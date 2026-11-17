-- B 段只读：先由用户选择正确连接；不要在报告记录连接信息。
SELECT VERSION() AS mysql_version, @@version_comment AS mysql_distribution,
       @@global.log_bin AS log_bin, @@global.binlog_format AS binlog_format,
       @@session.time_zone AS session_time_zone, @@global.time_zone AS global_time_zone,
       @@system_time_zone AS system_time_zone, NOW() AS database_now;
SELECT table_name, engine, table_collation
FROM information_schema.tables WHERE table_schema=DATABASE() ORDER BY table_name;
SELECT 'user' AS table_name, COUNT(*) AS rows_count FROM `user`
UNION ALL SELECT 'mood_record', COUNT(*) FROM mood_record
UNION ALL SELECT 'ai_reply', COUNT(*) FROM ai_reply
UNION ALL SELECT 'topic', COUNT(*) FROM topic
UNION ALL SELECT 'community_post', COUNT(*) FROM community_post
UNION ALL SELECT 'community_reply', COUNT(*) FROM community_reply
UNION ALL SELECT 'post_like', COUNT(*) FROM post_like
UNION ALL SELECT 'favorite', COUNT(*) FROM favorite;
-- 如 B 段发现新增表已存在，再逐表 SELECT COUNT(*)，不要盲跑迁移。
SELECT 'post' AS target, status, COUNT(*) AS rows_count FROM community_post GROUP BY status
UNION ALL SELECT 'reply', status, COUNT(*) FROM community_reply GROUP BY status;
-- 日志只证明曾发生模型调用，不能证明谁删除/隐藏，不能据此回填状态。
SELECT COUNT(*) AS deleted_posts,
       COALESCE(SUM(EXISTS(SELECT 1 FROM ai_reply a WHERE a.target_type='POST' AND a.target_id=p.id)),0)
           AS deleted_posts_with_ai_logs
FROM community_post p WHERE p.status=0;
-- 不输出原文、用户 ID、账号或日志正文；现有 ai_reply 不含管理员操作证据。
SHOW CREATE TABLE `user`;
SHOW CREATE TABLE mood_record;
SHOW CREATE TABLE ai_reply;
SHOW CREATE TABLE topic;
SHOW CREATE TABLE community_post;
SHOW CREATE TABLE community_reply;
SHOW CREATE TABLE post_like;
SHOW CREATE TABLE favorite;
