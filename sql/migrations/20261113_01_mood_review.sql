-- 仅新增回顾表；本轮仅在全新隔离测试库执行。业务库需另行授权迁移。
CREATE TABLE mood_review (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    review_type VARCHAR(16) NOT NULL COMMENT 'WEEK / RECENT_30',
    range_start DATE NOT NULL COMMENT '含',
    range_end DATE NOT NULL COMMENT '不含',
    revision INT NOT NULL COMMENT '同一用户、类型、范围内第几次生成，从1开始',
    source_fingerprint CHAR(64) NOT NULL COMMENT '来源记录(id,content_version)集合的SHA-256',
    status VARCHAR(16) NOT NULL COMMENT 'queued/running/succeeded/failed',
    stats_json JSON NOT NULL COMMENT '生成时由Java统计的数字快照',
    body TEXT NULL COMMENT '信的正文，含引用标记',
    ps VARCHAR(400) NULL COMMENT '可选P.S.',
    result_kind VARCHAR(16) NULL COMMENT 'MODEL / SUPPORT',
    template_version VARCHAR(64) NULL,
    model_name VARCHAR(128) NULL,
    last_error_code VARCHAR(64) NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    finished_at DATETIME NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_revision (user_id, review_type, range_start, revision),
    KEY idx_review_user_type_start (user_id, review_type, range_start, status),
    CONSTRAINT fk_review_user FOREIGN KEY (user_id) REFERENCES user (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='心情回顾与周报';

CREATE TABLE mood_review_source (
    review_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    content_version BIGINT NOT NULL COMMENT '生成时的记录版本',
    sent_to_model TINYINT NOT NULL COMMENT '是否发给了模型（D1）',
    PRIMARY KEY (review_id, record_id),
    KEY idx_review_source_record (record_id),
    CONSTRAINT fk_review_source_review FOREIGN KEY (review_id) REFERENCES mood_review (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='回顾的来源记录';
