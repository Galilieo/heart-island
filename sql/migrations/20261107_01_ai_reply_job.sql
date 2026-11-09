-- 心情异步回应增量迁移：在正确的目标库中人工执行一次，不由应用自动运行。
-- MySQL DDL 会隐式提交，执行前备份并安排停写窗口；不含破坏性初始化或自动 DROP 回退。
ALTER TABLE mood_record
    ADD COLUMN content_version BIGINT NOT NULL DEFAULT 1 COMMENT '生成依据版本',
    ADD COLUMN ai_reply_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '本版本是否需要AI回应',
    ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL COMMENT '首次软删除时间，历史未知保留NULL';

-- 不为历史日记创建任务，不推算历史删除时间，只标记当前已有回复的有效日记。
UPDATE mood_record
SET ai_reply_enabled = 1, update_time = update_time
WHERE status = 1 AND ai_reply IS NOT NULL AND CHAR_LENGTH(TRIM(ai_reply)) > 0;

CREATE TABLE ai_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    target_id BIGINT NOT NULL COMMENT '来源日记ID',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型，当前MOOD_REPLY',
    target_version BIGINT NOT NULL COMMENT '任务对应来源版本',
    status VARCHAR(24) NOT NULL COMMENT 'queued/running/succeeded/failed/cancelled/superseded',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '执行尝试次数',
    request_id VARCHAR(64) DEFAULT NULL COMMENT '本次尝试追踪标识',
    template_version VARCHAR(64) DEFAULT NULL COMMENT '模板版本',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    result_kind VARCHAR(24) DEFAULT NULL COMMENT 'MODEL或SUPPORT',
    model_called TINYINT DEFAULT NULL COMMENT '未知NULL/未调用0/已发起1',
    last_error_code VARCHAR(64) DEFAULT NULL COMMENT '错误分类，不存原文',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_job_target_version (task_type, target_id, target_version),
    KEY idx_ai_job_status_id (status, id),
    KEY idx_ai_job_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI异步任务元数据';
