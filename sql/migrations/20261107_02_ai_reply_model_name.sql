-- 仅放宽 AI 调用记录的模型名长度，与内部回应契约的 128 字符上限一致。
-- 本迁移独立于 20261107_01：云端仅修复长度时无需执行其他任务表变更。
-- 执行前核对目标库与实际字段定义并备份；预期原长度为 100，已为 128 时跳过。
-- MySQL DDL 会隐式提交；不自动缩回 100，以免截断扩列后写入的长模型名。
ALTER TABLE ai_reply
    MODIFY COLUMN model_name VARCHAR(128) NULL DEFAULT NULL COMMENT '模型名称';
