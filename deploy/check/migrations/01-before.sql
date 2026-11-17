-- 期望：新字段 0 行、新表 0 行。冲突或部分完成则停靠点4，不重跑。
SELECT table_name, column_name, column_type FROM information_schema.columns
WHERE table_schema=DATABASE() AND table_name='mood_record'
AND column_name IN ('content_version','ai_reply_enabled','deleted_at');
SELECT table_name FROM information_schema.tables
WHERE table_schema=DATABASE() AND table_name='ai_job';
SHOW CREATE TABLE mood_record;
-- 记录基线：符合回填条件的数量，迁移后 enabled=1 应等于此数。
SELECT COUNT(*) AS total,
       COALESCE(SUM(status=1 AND ai_reply IS NOT NULL AND CHAR_LENGTH(TRIM(ai_reply))>0),0) AS enabled_expected
FROM mood_record;
-- update_time 的逐行保全及 status/content/ai_reply 校验由备份恢复对比验证，不导出正文到报告。
