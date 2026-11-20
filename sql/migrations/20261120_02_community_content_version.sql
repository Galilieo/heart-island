-- 社区 AI 任务的来源版本。既有帖子取默认 1，不创建历史任务。
ALTER TABLE community_post
    ADD COLUMN content_version BIGINT NOT NULL DEFAULT 1
        COMMENT 'AI生成依据版本；编辑、作者删除、管理员隐藏时递增' AFTER ai_result_kind;
