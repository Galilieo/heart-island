SELECT column_name, column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='community_post' AND column_name='ai_result_kind';
SELECT COUNT(*) AS total, COUNT(ai_reply) AS nonnull_ai_reply FROM community_post;
