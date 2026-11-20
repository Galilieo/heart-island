-- 社区危机关注状态。执行前确认两表新增列均不存在，回退应用时保留可空列。
ALTER TABLE community_post
    ADD COLUMN risk_status VARCHAR(10) NULL DEFAULT NULL
        COMMENT '危机识别：NULL 未命中，PENDING 待关注，REVIEWED 已关注' AFTER hide_reason,
    ADD COLUMN risk_hit_time DATETIME NULL DEFAULT NULL
        COMMENT '最近一次命中时间' AFTER risk_status,
    ADD COLUMN risk_reviewed_time DATETIME NULL DEFAULT NULL
        COMMENT '管理员标为已关注的时间' AFTER risk_hit_time;

ALTER TABLE community_reply
    ADD COLUMN risk_status VARCHAR(10) NULL DEFAULT NULL
        COMMENT '危机识别：NULL 未命中，PENDING 待关注，REVIEWED 已关注' AFTER hide_reason,
    ADD COLUMN risk_hit_time DATETIME NULL DEFAULT NULL
        COMMENT '最近一次命中时间' AFTER risk_status,
    ADD COLUMN risk_reviewed_time DATETIME NULL DEFAULT NULL
        COMMENT '管理员标为已关注的时间' AFTER risk_hit_time;
