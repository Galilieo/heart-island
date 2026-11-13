-- 仅供显式创建的空 xinyu_test_ 库使用，不包含 DROP 或业务数据导入。
CREATE TABLE user (id BIGINT PRIMARY KEY) ENGINE=InnoDB;
CREATE TABLE mood_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    mood_type VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    ai_reply TEXT DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_mood_record_user_status_time (user_id, status, create_time),
    FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO user(id) VALUES (1);
INSERT INTO mood_record(id,user_id,mood_type,content,ai_reply,status) VALUES
    (101,1,'平静','历史有效有回复','历史回复',1),
    (102,1,'平静','历史有效无回复','  ',1),
    (103,1,'平静','历史已删除','历史回复',0);
-- 固定旧时间，防止回填触发 ON UPDATE 而被“恰好当前时间”的测试数据掩盖。
UPDATE mood_record SET create_time='2000-01-01 00:00:00', update_time='2000-01-02 00:00:00';

CREATE TABLE ai_reply (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    prompt TEXT DEFAULT NULL,
    reply_content TEXT DEFAULT NULL,
    model_name VARCHAR(100) DEFAULT NULL,
    status TINYINT NOT NULL DEFAULT 1,
    error_message TEXT DEFAULT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 扩列回归使用的历史调用记录，确保长度变化不改写既有数据。
INSERT INTO ai_reply(id,user_id,target_type,target_id,prompt,reply_content,model_name,status,create_time)
VALUES (501,1,'MOOD_RECORD',101,'历史合成提示','历史合成回复',REPEAT('m',100),1,'2000-01-03 00:00:00');

-- STAGE2_REVIEW_TABLES
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
