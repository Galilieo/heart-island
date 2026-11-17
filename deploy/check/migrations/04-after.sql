SHOW CREATE TABLE mood_review;
SHOW CREATE TABLE mood_review_source;
-- 核对 JSON、uk_review_revision、idx_review_user_type_start、fk_review_user，
-- 来源复合主键、idx_review_source_record、fk_review_source_review。
SELECT COUNT(*) AS reviews_expected_zero FROM mood_review;
SELECT COUNT(*) AS sources_expected_zero FROM mood_review_source;
