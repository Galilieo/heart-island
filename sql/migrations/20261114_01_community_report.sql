-- 社区举报与处理增量迁移（治理线 G1）：在正确的目标库中人工执行一次，不由应用自动运行。
-- 执行前确认目标库与完整备份，确认 content_report 表及 hide_reason 列尚不存在；MySQL DDL 会隐式提交。
-- 回退思路：停用前端举报入口与后台举报处理页；可保留本表及可空列，不自动 DROP 表或删列。

CREATE TABLE content_report (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '举报ID',
    reporter_id BIGINT NOT NULL COMMENT '举报人用户ID',
    target_type VARCHAR(10) NOT NULL COMMENT '举报对象类型：POST 帖子 / REPLY 回复',
    target_id BIGINT NOT NULL COMMENT '被举报帖子或回复ID（多态，应用层校验）',
    post_id BIGINT NOT NULL COMMENT '所在帖子ID；举报帖子时等于 target_id',
    target_user_id BIGINT NOT NULL COMMENT '被举报内容作者用户ID，用于后台展示与隐藏次数统计',
    reason VARCHAR(20) NOT NULL COMMENT '举报原因代码：ATTACK/AD/PRIVACY/ILLEGAL/SAFETY/OTHER',
    detail VARCHAR(200) NULL DEFAULT NULL COMMENT '补充说明，选择 OTHER 时必填',
    content_snapshot TEXT NOT NULL COMMENT '举报时的内容全文快照',
    status VARCHAR(12) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING 待处理 / HIDDEN 已隐藏 / DISMISSED 不违规 / CLOSED 内容已被作者删除',
    hide_reason VARCHAR(20) NULL DEFAULT NULL COMMENT '管理员隐藏原因，status=HIDDEN 时有值',
    handler_id BIGINT NULL DEFAULT NULL COMMENT '处理的管理员用户ID',
    handle_note VARCHAR(200) NULL DEFAULT NULL COMMENT '管理员内部备注，仅管理员可见',
    handle_time DATETIME NULL DEFAULT NULL COMMENT '处理时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '举报时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_report_reporter_target (reporter_id, target_type, target_id),
    KEY idx_report_status_target (status, target_type, target_id),
    KEY idx_report_reporter_time (reporter_id, create_time),
    CONSTRAINT fk_content_report_reporter FOREIGN KEY (reporter_id) REFERENCES `user` (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区举报记录';

ALTER TABLE community_post
    ADD COLUMN hide_reason VARCHAR(20) NULL DEFAULT NULL
        COMMENT '管理员隐藏原因，status=2 时有值' AFTER status,
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '1 正常，0 作者删除，2 管理员隐藏';

ALTER TABLE community_reply
    ADD COLUMN hide_reason VARCHAR(20) NULL DEFAULT NULL
        COMMENT '管理员隐藏原因，status=2 时有值' AFTER status,
    MODIFY COLUMN status TINYINT NOT NULL DEFAULT 1
        COMMENT '1 正常，0 作者删除，2 管理员隐藏';
