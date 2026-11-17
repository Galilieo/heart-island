SHOW CREATE TABLE ai_reply;
-- 期望 varchar(128)、YES、NULL，行数与执行前一致。
SELECT column_type, character_maximum_length, is_nullable, column_default
FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ai_reply' AND column_name='model_name';
SELECT COUNT(*) AS total, MAX(CHAR_LENGTH(model_name)) AS max_model_length FROM ai_reply;
