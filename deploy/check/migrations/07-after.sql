-- 预期两表各三列均可空、默认 NULL、位于 hide_reason 之后。
SELECT table_name, column_name, ordinal_position, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema=DATABASE()
  AND table_name IN ('community_post', 'community_reply')
  AND column_name IN ('risk_status', 'risk_hit_time', 'risk_reviewed_time')
ORDER BY table_name, ordinal_position;
-- 与迁移前逐表比对行数，三个非 NULL 计数均应为 0。
SHOW CREATE TABLE community_post;
SELECT COUNT(*) AS total, COUNT(risk_status) AS nonnull_risk_status,
       COUNT(risk_hit_time) AS nonnull_risk_hit_time,
       COUNT(risk_reviewed_time) AS nonnull_risk_reviewed_time
FROM community_post;
SHOW CREATE TABLE community_reply;
SELECT COUNT(*) AS total, COUNT(risk_status) AS nonnull_risk_status,
       COUNT(risk_hit_time) AS nonnull_risk_hit_time,
       COUNT(risk_reviewed_time) AS nonnull_risk_reviewed_time
FROM community_reply;
