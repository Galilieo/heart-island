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
