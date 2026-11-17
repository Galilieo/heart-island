SHOW CREATE TABLE mood_record;
SHOW CREATE TABLE ai_job;
-- 期望：三个异常计数均为0；enabled_total等于执行前enabled_expected；新任务数为0。
SELECT COALESCE(SUM(content_version<>1),0) AS invalid_version,
       COALESCE(SUM(deleted_at IS NOT NULL),0) AS invalid_deleted_at,
       COALESCE(SUM(ai_reply_enabled<>(status=1 AND ai_reply IS NOT NULL AND CHAR_LENGTH(TRIM(ai_reply))>0)),0) AS invalid_enabled,
       COALESCE(SUM(ai_reply_enabled=1),0) AS enabled_total FROM mood_record;
SELECT COUNT(*) AS jobs_expected_zero FROM ai_job;
