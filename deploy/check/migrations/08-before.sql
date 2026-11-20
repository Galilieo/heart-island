-- 预期 content_version 不存在。保存帖子及社区任务行数供比较。
SELECT column_name, column_type, is_nullable, column_default, ordinal_position
FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='community_post'
  AND column_name IN ('ai_result_kind', 'content_version', 'ai_generation_id')
ORDER BY ordinal_position;
SELECT COUNT(*) AS total FROM community_post;
SELECT COUNT(*) AS community_jobs FROM ai_job WHERE task_type='COMMUNITY_REPLY';
