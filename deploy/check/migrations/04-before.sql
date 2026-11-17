-- 期望0行；检查 user 主键、mood_record 版本/开关列与来源查询索引。
SELECT table_name FROM information_schema.tables WHERE table_schema=DATABASE()
AND table_name IN ('mood_review','mood_review_source');
SHOW CREATE TABLE `user`;
SHOW CREATE TABLE mood_record;
SHOW CREATE TABLE ai_job;
