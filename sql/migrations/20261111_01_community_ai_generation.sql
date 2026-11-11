-- 社区同步AI生成的条件回写标识；不修改历史正文或回复，不创建后台任务。
-- 执行前确认目标库、备份及该列尚不存在；仅执行一次，MySQL DDL会隐式提交。
-- 发布时先停止旧进程及其在途生成，再迁移并启动新版本，防止旧代码绕过回写条件。
-- 回退时先停止生成路径；可保留该可空列，不自动删列或退回无保护的旧写入逻辑。
ALTER TABLE community_post
    ADD COLUMN ai_generation_id VARCHAR(36) NULL DEFAULT NULL
    COMMENT '当前AI生成标识，NULL表示无有效生成' AFTER ai_reply;
