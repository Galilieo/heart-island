-- 预期 content_version 紧随 ai_result_kind，为不可空 BIGINT，默认 1。
SELECT column_name, column_type, is_nullable, column_default, ordinal_position
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='community_post'
  AND column_name IN ('ai_result_kind', 'content_version', 'ai_generation_id')
ORDER BY ordinal_position;
SELECT COUNT(*) AS total, COUNT(CASE WHEN content_version <> 1 THEN 1 END) AS noninitial_versions FROM community_post;
SELECT COUNT(*) AS community_jobs FROM ai_job WHERE task_type='COMMUNITY_REPLY';
