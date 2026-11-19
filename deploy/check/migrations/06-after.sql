SHOW CREATE TABLE community_post;
SELECT COUNT(*) AS total, COUNT(ai_reply) AS nonnull_ai_reply,
       COUNT(ai_result_kind) AS result_kinds_expected_zero FROM community_post;
