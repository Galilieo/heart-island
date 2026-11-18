# 近期回顾与周报（阶段2）功能契约

状态：已实现，2026-11-17 随生产 `fefb09f` 上线。本文于 2026-11-18 从源码归纳，补齐此前只存在于本地任务书（`AI_WORKLOG/plans/2026-11-13-stage2-review-plan.md`）的约定；字段与行为以源码为准，二者不一致时以源码为事实、在本文登记差异。

依据：[总方案第 8 节](xinyu-upgrade-master-plan.md#8-阶段2近期分析与情绪周报)；任务与重试沿用 [1a 契约](ai-reply-service-v1a.md)；额度见 [Redis 限流](redis-rate-limit.md)。

源码入口：`MoodReviewController`、`MoodReviewService`、`MoodReviewStatsService`、`MoodReviewPayloadBuilder`、`MoodReviewSafety`、`MoodReviewMapper`、`AiJobService`／`AiJobWorker`（回顾分支）；Python `app/reviews.py`、`app/review_prompt.py`、`app/main.py`；前端 `views/Review.vue`、`stores/review.js` 与 `components/review/`。

## 1 目标与范围

- 数字由 Java 统计；用户点击才生成小芽的“信”，经 1a 任务队列交给 Python；信保存后可回看、会过期、可显式重新生成。**打开页面（GET）不调用模型、不写库。**
- 两种回顾：**周报**（`WEEK`，只针对已结束的完整一周）与**近 30 天回顾**（`RECENT_30`，按生成当天往前 30 天）。
- 不做：情绪强度与标签、月报、导出、自动定时生成与推送、多轮聊天、统计缓存。

## 2 已定决定（2026-11-13 用户确认）

| # | 决定 | 实现 |
| --- | --- | --- |
| D1 | 只把“AI 回复”开关打开的记录发给模型；统计仍计入全部有效记录 | 生成前无授权记录返回 `no_ai_enabled_records`；`aiEnabledCount` 随结果返回，前端写明“根据你开了 AI 回复的 N 条记录” |
| D2 | 范围内任一记录命中危机规则时不调用模型，显示固定支持文案，数字照常显示 | Java `MoodReviewSafety` 预检，命中时由 Java 直接构造 `SUPPORT` 结果；Python 端同样逐条检查 |
| D3 | 被引用的来源记录删除后，旧信隐藏正文，只留数字与“重新生成” | `view()`：存在已删除来源时 `body`／`ps` 置空、`sources` 为空、`stale=true` |
| D4 | 复用 1a 的 `ai_job` 与 worker，新增任务类型 `MOOD_REVIEW`；`MOOD_REPLY` 的 SQL 不改 | `AiJobMapper` 有一组回顾专用领取、收尾、恢复 SQL |

## 3 时间与统计口径

- 时区由 `xinyu.review.time-zone`（环境变量 `XINYU_REVIEW_TIMEZONE`）决定，默认 `Asia/Shanghai`，注入 `Clock`。`create_time` 为无时区 `DATETIME`，按该时区墙上时间解释。
- 周：`[周一 00:00, 下周一 00:00)`。`start` 缺省取上一个完整周；必须是周一，不能晚于本周一；本周可查统计，但不能生成周报（`week_not_finished`）。
- 近 30 天：`[今天-29 天 00:00, 明天 00:00)`。
- 只统计 `status=1` 的记录。同日多条计多次；“有记录的天数”按日期去重。
- 心情分布按次数降序，次数相同按固定心情顺序排。接口不返回“最多的心情”字段；信里不能说“X 最多”，除非确实唯一最多（由提示词与离线检查 C5 约束）。
- 首页“近 7 天趋势”（`/mood/trend/recent7`）沿用旧口径 `LocalDate.now()`，与本功能时区注入不同，未统一。

## 4 数据

### `mood_review`

| 字段 | 说明 |
| --- | --- |
| `user_id`、`review_type` | `WEEK`／`RECENT_30` |
| `range_start`（含）、`range_end`（不含） | 日期 |
| `revision` | 同一用户、类型、`range_start` 内第几次生成，从 1 起；唯一键 `uk_review_revision(user_id, review_type, range_start, revision)` |
| `source_fingerprint` | 来源记录 `(id, content_version)` 集合按 id 排序后的 SHA-256 |
| `status` | `queued`／`running`／`succeeded`／`failed` |
| `stats_json` | 生成时的统计快照（另含 `aiEnabledTotal`）；展示数字永远取此快照，模型输出中的数字不被读取 |
| `body`、`ps` | 信正文（含 `{{rN}}` 引用标记）与可选 P.S. |
| `result_kind`、`template_version`、`model_name`、`last_error_code`、`finished_at` | 结果元数据；模板为 `mood-review-v1` |

### `mood_review_source`

主键 `(review_id, record_id)`；`content_version` 为生成时版本；`sent_to_model` 标记该条是否进入本次发给模型的子集（D1 与抽样后的结果）。

### `ai_job`（回顾任务）

`task_type='MOOD_REVIEW'`，`target_id` 指向 `mood_review.id`，`target_version` 等于 `revision`。

## 5 接口

均需登录，只访问本人数据；无 token 返回 401 `login_required`。错误以 HTTP 状态加原因码返回。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/mood/report/week?start=YYYY-MM-DD` | 周统计＋最新成功周报（`report`）、进行中（`active`）、最近失败（`failed`）、`aiEnabledCount` |
| POST | `/mood/report/week/generate` | body `{"start": "YYYY-MM-DD"}`；见 5.1 |
| POST | `/mood/report/week/regenerate` | 同上，但即使已有未过期成功版本也新建一版；旧成功版本保留到新版本成功 |
| GET | `/mood/report/week/history?page=` | 每页 5 条，只含范围、版本、状态、条数，不含正文 |
| GET | `/mood/analysis?range=30d` | 近 30 天统计＋回顾，结构同周报 GET；`range` 只接受 `30d`，其他值 400 `invalid_range` |
| POST | `/mood/analysis/generate` | 生成近 30 天回顾，规则同 5.1 |
| GET／POST | `/mood/ai-job/{id}`、`/mood/ai-job/{id}/retry` | 与心情回复共用；按任务类型分派到回顾逻辑 |

GET 返回的顶层字段保留第 1 步统计结构：`rangeStart`、`rangeEnd`、`total`、`daysWithRecords`、`days[]`（日期与当日心情）、`moodCounts[]`，再加 `report`、`active`、`failed`、`aiEnabledCount`。

`Report` 结构：`id`、`revision`、`status`、`body`、`ps`、`resultKind`、`stale`、`jobId`、`rangeStart`、`rangeEnd`、`aiEnabledCount`、`generatedAt`、`sources[]`（只含正文实际引用到的来源：`ref`、`recordId`、日期、星期、心情、当前全文）。

### 5.1 生成规则（`generate`／`regenerate`）

在准入事务内先锁用户行（跨实例去重），再依次判断：

1. 周报且该周未结束 → 400 `week_not_finished`。
2. 范围内无有效记录 → 400 `no_records`；有记录但没有开启 AI 的 → 400 `no_ai_enabled_records`。
3. 已有进行中版本 → 直接返回它。
4. 非 `regenerate`，且最新成功版本的指纹与当前来源一致 → 直接返回它。
5. 容量：全站排队任务（含心情回复）≥ 100 或本人进行中任务 ≥ 3 → 503 `capacity_exceeded`。
6. 新建 `mood_review`（下一个 `revision`）、来源行与 `ai_job`，返回 `queued` 版本。

**限流**（准入事务外预检）：只有“确定会新建任务”时才扣每日回顾额度；上述 1～4 不扣；含危机记录的回顾不扣（不产生费用）。超额返回 429 `review_rate_limited` 与 `Retry-After`。预检与事务内读取之间的窗口误判按“已扣不退”接受。

### 5.2 重试

只有 `failed` 可重试；`queued`／`running`／`succeeded` 幂等返回当前状态。来源已变化 → 409 `source_changed`；同范围已有其他进行中版本 → 409 `review_already_active`；冷却未到 → 429 `review_cooldown` 与 `Retry-After`（沿用 1a 冷却策略）；容量与限流同 5.1。

## 6 任务执行

- **领取**：与心情回复共用单 worker 与三槽。worker 先取心情任务，没有时才取回顾任务（心情优先；心情队列持续非空时回顾可能等待，见生产评审 F3，当前流量下未出现）。
- **发送前预检**（短事务，锁来源后锁任务）：重算指纹，与创建时不一致 → 任务 `superseded`、报告 `failed`／`source_changed`，不调用模型。任一来源命中危机规则 → 不发网络，直接按 `SUPPORT` 收尾。
- **请求内容**：只含开启 AI 的记录；超过 60 条时按时间均匀抽样；每条用内部别名 `r1…`（不发数据库 ID）、日期、星期、心情、正文。正文按 12000 UTF-16 总预算水位分配，超长记录按首尾约 2:1 保留并插入 `〔中间省略〕`，不拆代理对；附统计与总条数。不发送用户名、昵称或账号信息。供应商预算 40 秒。
- **Python**：`POST /internal/v1/mood-reviews`，认证、总超时、并发门禁与心情回复端点相同；请求体上限 128 KiB（心情回复端点仍为 16 KiB）。模型输出 JSON `{"body", "ps"}`；校验 JSON 合法、引用标记都在本次输入中、正文硬上限（周报 500、近 30 天 600 UTF-16）、`ps` ≤ 60、无诊断与编造联系方式（记录原文出现的“微信／电话／QQ”允许复述）；不合法返回 `invalid_model_response`，不自动重试。
- **提交**（一个事务，先锁来源再锁任务）：再次核对尝试身份与指纹，拒绝迟到结果；按本次请求校验返回；危机场景只接受固定 `SUPPORT` 文案。成功后两表一起进入终态。
- **失败**：错误码限定为安全集合（`provider_timeout`、`provider_error`、`invalid_model_response`、`overloaded`、`execution_interrupted`、`capacity_exceeded`、`internal_error` 等），其他一律记 `internal_error`。旧成功版本继续显示。
- **重启恢复**：唯一 worker 启动时把中断的回顾任务与报告标为失败，保留历史成功版本。

## 7 展示与过期

- `stale=true` 的情况：来源集合或版本与生成时不同；近 30 天回顾的范围已随日期滚动；或任一来源已被删除（此时还隐藏正文，D3）。
- `failed` 只在最近一次尝试失败、且对应任务仍为 `failed`、且未过期时返回。
- 前端：`/review` 页面；引用标记换成日期小标签，点开显示来源卡片；轮询沿用 `ai-job-polling.js`，离开页面停止、回来从 GET 恢复；区分无数据、无授权记录、生成中、失败可重试、旧版可用与需更新。首页“这一周”卡片有“看周报”入口。

## 8 测评

离线合成测评集 `xinyu-ai/evals/review_set.jsonl`（主集）与 `review_set_long.jsonl`（长记录），规则检查 C1～C15 见 `xinyu-ai/evals/checks_review.py`。定稿 `mood-review-v1`（对应 `evals/prompts/mood-review-v1a.txt`）的结果与取舍见[测评计划](xinyu-evaluation-plan.md)“阶段 2”与首次上线归档中的定稿记录：结构与引用合法 37/37，正文无统计数字 30/37（低于 90% 门槛，为已知差距），引用忠实率未单独测量。

## 9 与设计稿的差异

| 总方案第 8 节设计 | 实现 |
| --- | --- |
| 表名 `mood_report`／`report_source` | `mood_review`／`mood_review_source` |
| 近期分析 `range=7d|30d` | 只支持 `30d`；近 7 天沿用首页趋势接口 |
| 未列历史接口 | 新增 `GET /mood/report/week/history` |
| 统计查询可加 Redis 短时缓存 | 未做（查询量小，任务书明确不做） |
| 任务状态复用 `/ai/jobs/{id}` | 实际为 `/mood/ai-job/{id}`，按任务类型分派 |

## 10 已知限制与延后项

- 心情优先领取下回顾可能等待（评审 F3）；回顾路径的 `prepare` 在预算检查之前执行，与心情路径顺序不同（评审 F7）。
- `NBLK-2`（提示条快照 `snapshotTotal` 口径）、`NBLK-5`（`jobId()` 空值防御与 `generateRecent` 异常处理对称性）、`MoodReviewService` 等大文件拆分延后。
- 繁简一致暂缓（只面向大陆用户）；离线检查 C12 只记录。
- 长回顾读感（2026-11-18，模型预评＋用户抽查 2 份）：用户两份均为“还行”；长度与统计数字无问题，有一处引用日期标签错位，长记录集未出现 P.S.。确认一个提示词缺口：模型可能点评记录数量本身（如“记录量这么大，是真的累”），现行提示词未禁止，修改另行确认。
