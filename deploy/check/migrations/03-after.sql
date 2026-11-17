SHOW CREATE TABLE community_post;
-- 期望新列 varchar(36) 可空、全部NULL；行数和原AI回复非空数不变。
SELECT COUNT(*) AS total, COUNT(ai_reply) AS nonnull_ai_reply,
       COUNT(ai_generation_id) AS generation_ids_expected_zero FROM community_post;
