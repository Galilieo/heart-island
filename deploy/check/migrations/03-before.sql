-- 期望0行；已存在则核对是否完整执行，不能重复 ADD COLUMN。
SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='community_post' AND column_name='ai_generation_id';
SHOW CREATE TABLE community_post;
SELECT COUNT(*) AS total, COUNT(ai_reply) AS nonnull_ai_reply FROM community_post;
