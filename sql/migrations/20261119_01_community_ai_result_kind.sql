ALTER TABLE community_post
  ADD COLUMN ai_result_kind VARCHAR(10) NULL DEFAULT NULL
  COMMENT 'AI回复类型：MODEL 模型生成，SUPPORT 危机固定文案；NULL 表示无回复或历史数据'
  AFTER ai_reply;
