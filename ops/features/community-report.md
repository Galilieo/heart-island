# 社区举报与处理（治理线 G1）

状态：已实现（待最终验收与提交）

## 功能目标

为匿名社区补齐内容举报、用户举报反馈与管理员人工裁决闭环，并将管理员隐藏拆分为独立状态（`status=2`）与可追溯原因，确保匿名展示不向普通用户暴露作者用户 ID。

- **本轮范围**：帖子/回复举报、`SAFETY` 优先队列、管理员单条聚合裁决（`HIDE`/`DISMISS`）、帖子/回复状态 `1 <-> 2` 与详情弹窗、我的举报（近 90 天）、作者可见隐藏原因与删除、`mine` 替代普通社区接口中的 `userId`。
- **明确不做**：举报数量自动隐藏、地区求助热线、站内信通知、举报数据自动清理（并入数据保留专项）、管理员审计日志、用户/话题管理详情弹窗。

## 状态定义与写保护规则

帖子与回复 `status` 约定：`1` 正常可见，`0` 作者删除，`2` 管理员隐藏（附 `hide_reason`）。

| 动作 | 允许的前置状态 | 结果 | 附带影响 |
| --- | --- | --- | --- |
| 作者删除 | `1` | `0` | 帖子清 `ai_generation_id`；回复将所属帖子 `reply_count` 减一 |
| 作者删除已隐藏内容 | `2` | `0` | 帖子清 `ai_generation_id`；回复不再重复减 `reply_count` |
| 管理员隐藏 | `1` | `2`，写 `hide_reason` | 帖子清 `ai_generation_id`；回复将所属帖子 `reply_count` 减一 |
| 管理员恢复 | `2` | `1`，清 `hide_reason` | 回复将所属帖子 `reply_count` 加一；不恢复旧 AI 生成 |
| 作者编辑 | `1` | `1` | 被隐藏（`2`）与已删除（`0`）均不可编辑 |

- 所有状态写入使用条件更新 `WHERE id = ? AND status = ?`，影响行数为 `0` 时按状态已变化（`409 Conflict`）处理。
- 回复状态变更与 `reply_count` 增减在同一数据库事务内提交。

## 数据库设计

迁移文件：[20261114_01_community_report.sql](../../sql/migrations/20261114_01_community_report.sql)

### 新表 `content_report`

- 字段：
  - `id BIGINT AUTO_INCREMENT PRIMARY KEY`
  - `reporter_id BIGINT NOT NULL`（外键 `user(id) ON DELETE CASCADE`）
  - `target_type VARCHAR(10) NOT NULL`（`POST` / `REPLY`）
  - `target_id BIGINT NOT NULL`（多态目标 ID，应用层校验）
  - `post_id BIGINT NOT NULL`（所属帖子 ID；举报帖子时等于 `target_id`）
  - `target_user_id BIGINT NOT NULL`（被举报内容作者用户 ID）
  - `reason VARCHAR(20) NOT NULL`（`ATTACK` / `AD` / `PRIVACY` / `ILLEGAL` / `SAFETY` / `OTHER`）
  - `detail VARCHAR(200) NULL`（选 `OTHER` 时必填，去除首尾空白后非空）
  - `content_snapshot TEXT NOT NULL`（举报时原文全文快照）
  - `status VARCHAR(12) NOT NULL DEFAULT 'PENDING'`（`PENDING` / `HIDDEN` / `DISMISSED` / `CLOSED`）
  - `hide_reason VARCHAR(20) NULL`（`HIDDEN` 时必填：`ATTACK` / `AD` / `PRIVACY` / `ILLEGAL`）
  - `handler_id BIGINT NULL`
  - `handle_note VARCHAR(200) NULL`
  - `handle_time DATETIME NULL`
  - `create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP`
  - `update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP`
- 唯一约束与索引：
  - `uk_report_reporter_target (reporter_id, target_type, target_id)`：同一用户对同一内容只能举报一次。
  - `idx_report_status_target (status, target_type, target_id)`：支撑后台队列聚合与单次裁决关闭全部待处理举报。
  - `idx_report_reporter_time (reporter_id, create_time)`：支撑「我的举报」90 天窗口查询与自然日限额统计。
- 数据保留：本轮不自动清理，回滚时停用接口与页面、保留表结构不自动 DROP。

### 现有表增量

- `community_post`、`community_reply` 各新增 `hide_reason VARCHAR(20) NULL`，`status` 注释更新为 `1 正常，0 作者删除，2 管理员隐藏`。

## API 契约

### 1. 社区匿名性修正（普通用户侧）

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/community/post/list` | 公开（可选登录） | 不返回 `userId`，返回 `mine`（未登录为 `false`） |
| GET | `/community/post/detail` | 公开（可选登录） | 同上；`status=2` 仅作者本人可见，返回 `hidden=true` 与 `hideReason` |
| GET | `/community/post/favorites` | 登录用户 | 不返回 `userId`，返回 `mine`；自动跳过非 `status=1` 帖子 |
| GET | `/community/reply/list` | 公开（可选登录） | 不返回 `userId`，返回 `mine`；登录作者额外可见自己在该贴下 `status=2` 回复（带 `hidden=true`、`hideReason`） |

### 2. 用户举报接口

| 方法 | 路径 | 权限 | 请求参数 / 字段 | 响应 / 错误语义 |
| --- | --- | --- | --- | --- |
| POST | `/community/report` | 登录用户 | JSON: `targetType`(`POST`/`REPLY`), `targetId`, `reason`, `detail`(选填；`OTHER`必填，≤200) | `200` 成功；`400` 参数非法/自举/`OTHER`缺说明/超长；`404` 目标不存在或不可举报；`409` 重复举报；`429` 今日（`Asia/Shanghai` 自然日）满 20 次 |
| GET | `/community/report/mine` | 登录用户 | Query: `pageNum`(默认1), `pageSize`(默认10) | 最近 90 天按时间倒序；每项含 `id`/`targetType`/`targetId`/`postId`/`excerpt`(前60字)/`reason`/`status`/`hideReason`/`createTime`/`handleTime`；不返回 `handleNote`、`handlerId`、`targetUserId` |
| GET | `/community/report/reported-targets` | 登录用户 | Query: `postId`(必填) | 当前用户在该帖子及其回复下已举报的 `(targetType, targetId, status)` 列表 |

### 3. 后台治理接口（均校验登录态且 `role == ADMIN`）

| 方法 | 路径 | 用途 | 关键规则与返回值 |
| --- | --- | --- | --- |
| GET | `/admin/report/page` | 分页聚合队列 | Query: `status`(`PENDING`/`DONE`), `pageNum`, `pageSize`；按 `(target_type, target_id)` 聚合，`PENDING` 按含 `SAFETY` 优先再首次举报正序，`DONE` 按处理时间倒序 |
| GET | `/admin/report/detail` | 举报详情 | Query: `targetType`, `targetId`；返回最早快照、`currentContent`/`currentStatus`/`contentChanged`、`authorHiddenCount`、`suggestedHideReason`（最多原因若为 `SAFETY`/`OTHER` 则默认 `ILLEGAL`）及全部举报记录 |
| POST | `/admin/report/handle` | 裁决处理 | JSON: `targetType`, `targetId`, `action`(`HIDE`/`DISMISS`), `hideReason`(`HIDE`必填), `note`(≤200)；先对目标主键行加 `FOR UPDATE` 锁再条件更新 `PENDING` 行，并发冲突返回 `409`「已被其他管理员处理」；目标已被作者删除时统一闭环为 `CLOSED`；已处于 `2` 时只关闭举报不重复扣减回复数 |
| GET | `/admin/report/pending-count` | 侧栏角标计数 | 按内容对象去重聚合后的待处理条数 |
| GET | `/admin/post/{id}` | 后台帖子详情 | 含全文、话题名、`aiReply`、`hideReason`、`pendingReportCount`、`pendingReasons` |
| GET | `/admin/reply/{id}` | 后台回复详情 | 含回复全文、所属帖子正文、`hideReason`、`pendingReportCount`、`pendingReasons` |
| PUT | `/admin/post/{id}/status`<br>`/admin/reply/{id}/status` | 隐藏 / 恢复 | Query: `status`(`1`/`2`), `hideReason`(`status=2` 必填)；传 `0` 返回 `400`；状态已变化返回 `409` |
| DELETE | `/admin/post/{id}`<br>`/admin/reply/{id}` | 兼容隐藏入口 | Query: `hideReason`(必填)；行为与 `status=2` 一致，由后续迭代评估下线 |

## 验收标准

1. 被隐藏（`status=2`）帖子与回复在列表、收藏、详情（非作者）、抱抱、收藏、新增回复、举报、编辑入口均不可用；作者本人查看时可看隐藏提示与原因并执行删除，无法再编辑或互动。
2. 普通用户社区接口响应中不再出现 `userId`，本人内容返回 `mine: true`，他人及未登录返回 `mine: false`。
3. 同一用户对同一内容无法重复举报（含数据库唯一索引兜底返回 `409`）；同一账号自然日第 21 次举报返回 `429`；我的举报仅返回本人近 90 天记录且不含备注、处理人、被举报人。
4. 两名管理员并发处理同一内容时仅一人成功，另一人收到 `409`「已被其他管理员处理」，回复计数仅扣减一次且无死锁。
5. 前端在桌面 1440 与手机 375、日间与夜间主题下完成全部菜单、弹窗、底抽屉、表格、详情弹窗与长文滚动验证，控制台与网络无意外报错。
