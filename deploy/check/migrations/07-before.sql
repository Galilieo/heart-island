-- 预期无新增列。分别记录行数供迁移后比对。
SELECT table_name, column_name, ordinal_position, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name IN ('community_post', 'community_reply')
  AND column_name IN ('risk_status', 'risk_hit_time', 'risk_reviewed_time')
ORDER BY table_name, ordinal_position;
SELECT COUNT(*) AS total FROM community_post;
SELECT COUNT(*) AS total FROM community_reply;
