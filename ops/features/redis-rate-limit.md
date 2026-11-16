# 阶段 1b：Redis 共享限流

状态：已实现（2026-11-16），未部署。任务书：`AI_WORKLOG/plans/2026-11-16-redis-rate-limit-plan.md`；执行与验证记录：`AI_WORKLOG/2026-11-16-redis-rate-limit.md`。上游依据：总方案第 3.4、7.2 节；1a 契约容量保护与 RETRY-001 冷却。

## 1. 目标

给所有付费 AI 调用加按用户、按全站的时间窗口额度，Redis 做共享计数；Redis 不可用时退回本机计数照常服务。它限制一段时间内的总次数，与 1a 的"同一时刻数量"容量保护（排队 100 / 每人 3 / 冷却）互相独立。

## 2. 额度表（默认值，全部可配置）

| 范围 | 窗口 | 默认额度 | 适用调用 | 配置项 |
| --- | --- | --- | --- | --- |
| 每人 · 心情回复 | 每小时 | 20 | 保存创建任务、手动重试 | `xinyu.rate-limit.mood-per-hour` |
| 每人 · 心情回复 | 每天 | 100 | 同上 | `xinyu.rate-limit.mood-per-day` |
| 每人 · 社区 AI | 每小时 | 10 | 发帖、编辑的同步生成 | `xinyu.rate-limit.community-per-hour` |
| 每人 · 回顾信 | 每天 | 10 | 生成 / 重试 | `xinyu.rate-limit.review-per-day` |
| 全站 · 所有 AI 调用 | 每小时 | 1000 | 以上全部 | `xinyu.rate-limit.global-per-hour` |

规则表单点：调用类型到范围的组合在 `RateLimitType`，范围到配置项的映射在 `RateLimitProperties.limitFor`。**改额度只改配置（或这两处）**。

窗口规则：按 `Asia/Shanghai` 的整点和自然日对齐的**固定窗口**（"每天"在当地零点重置）。固定窗口在边界处可能出现两倍突发，这是已接受的取舍。限流窗口时区沿用 `xinyu.review.time-zone`（默认 Asia/Shanghai），与回顾统计共用项目墙上时间约定。

### 2.1 危机文本不计入额度

危机命中由固定 SUPPORT 文案回应、不调用模型、不产生费用，因此**不计入额度，也不应被额度挡住**。实现：三个入口在扣额度之前用 `MoodReviewSafety.matches`（与 Python `safety.py` 正则逐字对应，跨端一致性由集成用例核验）做预检：

- 心情保存：内容在手，命中则不扣、任务照常入队（worker 端 Python 再分流到 SUPPORT，零模型调用）。
- 心情重试 / 回顾生成与重试：预读来源快照做同样豁免；快照与事务内锁定读取存在并发窗口，窗口内误判按"已扣不退"接受。
- 社区：同步生成直连供应商、无危机分流，每次发起都扣额度，无豁免。

## 3. 键设计与 Lua 原子性

- 键：`{前缀}rl:{范围}:{用户ID或global}:{窗口起点}`，例如 `xinyu:prod:rl:mood_hour:42:2026111614`。窗口标签：小时 `yyyyMMddHH`、自然日 `yyyyMMdd`。前缀按环境区分（`xinyu:dev:` / `xinyu:prod:` / 测试 `xinyu:test:`）。**键里只存用户 ID 与窗口，不放任何内容文本。**
- 一次调用同时受多个范围约束（如心情回复 = 每人小时 + 每人天 + 全站小时）。全部检查在一个 Lua 脚本（`src/main/resources/ratelimit/check-and-increment.lua`）内原子完成：所有范围未满才一起 `INCRBY` 并设置 `EXPIRE`；任一超额则一个都不加。不能用先 GET 再 INCR 或分次 INCR 回滚替代。
- **"最早可以再试的秒数"的口径**：必须等**全部**超额范围都重置，请求才可能通过，因此再试秒数与触发范围取"所有超额范围里最晚重置的那一个"（等待最长者）。例如每小时与每天额度同时用完时，错误码为 `rate_limited_day`、`Retry-After` 到当天零点——只提示最早重置的范围会让用户等到整点后再吃一次 429。秒数向上取整、至少 1。
- 过期：窗口长度 + 60 秒余量（小时键 3660 秒、日键 86460 秒），保证计数键一定会消失；脚本对历史遗留的无 TTL 键补设过期。

## 4. 故障降级与已知代价

Redis 命令超时（默认 200ms）、连接失败或脚本报错时，本次改用进程内本机计数器（同一规则表、同样的窗口与原子语义）判断，照常服务。进入降级后的冷却期（默认 30 秒）内不再尝试 Redis；冷却后下一次请求重试，成功即恢复。进入降级、恢复各记一条 WARN / INFO（只写范围与原因分类，不写异常原文）。

已知代价（接受，多实例部署前必须重评）：

- 降级期间本机计数与 Redis 计数互不相通，恢复后可能短时多放一些。
- 服务重启后本机计数归零。
- 当前单实例部署，以上代价可接受。

本机计数器只保留当前与上一个窗口的数据（`rate-limit-local-purger` 守护线程每 60 秒清理），不会无限增长。

未配置 `spring.data.redis.url`（本机开发默认）时不创建任何 Redis 客户端，直接用本机计数，启动日志输出"未配置 Redis，限流使用本机计数"。

## 5. 错误码、用户提示与观测

| 场景 | 后端行为 | 前端 |
| --- | --- | --- |
| 心情保存超额 | 记录照常保存；创建 `status=failed`、`attempt_count=0` 的任务，`last_error_code` 为 `rate_limited_day` / `rate_limited_hour` / `rate_limited_global`（按触发范围，`RateLimitScope.errorCode` 单点映射） | `MoodAiStatus.vue` 按错误码显示对应文案 |
| 心情手动重试超额 | HTTP 429 + `Retry-After`，不改任务状态 | 沿用现有 429 处理（回查任务） |
| 回顾生成 / 重试超额 | HTTP 429 + `Retry-After` + 错误码 `review_rate_limited`，不创建任务 | `stores/review.js` 文案表 |
| 社区发帖 / 编辑超额 | 帖子照常保存，不发起生成，`ai_reply` 保持 NULL，不写失败兜底、不记生成失败；接口 message 说明"小芽这会儿回复不过来" | 发布 / 修改成功 toast 后补一句 |

文案（心情保存）：每天额度 →"今天小芽的回复次数用完了，记录已保存。"；每小时 →"小芽这会儿回复得太多了，记录已保存，稍后可以重试。"；全站 →"小芽这会儿太忙了，记录已保存，稍后可以重试。"

`rate_limited_*` 任务 `attempt=0` 不在冷却中，`retryable=true`；用户重试时若额度仍不足会得到 429。

观测：每次拒绝记一条 INFO（调用类型、范围、后端、用户 ID、再试秒数，不含内容）；后台"概览"页 AI 服务组新增"限流后端"与"今日限流拒绝"卡片（进程内计数，重启归零，卡片注明；降级中用危险色提示）。

## 6. 扣额度时机与不退还

- 在确定要发起付费调用的入口扣：心情任务创建 / 重试重新入队、回顾任务创建 / 重试、社区同步生成之前。
- 额度检查在数据库准入锁与事务**之外**：先问限流组件，再进入 `AiJobAdmission` 事务决定任务是 `queued` 还是 `failed(rate_limited_*)`。Redis 调用绝不包在数据库事务或准入锁里。
- **只对"按快照确定会（重新）发起付费调用"的请求扣额度**，各入口的预判闸门（快照与行锁内的并发窗口按"已扣不退"接受，正式校验仍在事务内）：
  - 心情保存：关闭 AI（`needAiReply=false`）不创建任务，不扣；危机文本豁免（见 2.1）。
  - 心情修改：无变化保存、关闭 AI 不扣。
  - 心情重试：幂等状态（queued/running/succeeded）、不可重试状态、冷却中都不重新入队，不扣；容量满留给事务内 503，按"已扣不退"处理。
  - 回顾生成：本周未结束、无记录、无授权记录、已有进行中任务、指纹未变且非 regenerate 的幂等返回，都不创建任务，不扣。
  - 回顾重试：幂等 / 不可重试 / 冷却中 / 已有其他进行中任务 / 来源已变化（指纹不匹配），不扣。
- **已扣额度不退还**：随后因容量不足、来源已删除或事务失败而未真正调用模型的，一律不退。补偿逻辑的复杂度不值得。

## 7. 组件结构

`com.xinyu.ratelimit`：`RateLimiter` 接口（`check` 返回通过或拒绝+范围+再试秒数）；`RedisRateLimiter`（Lua 脚本）；`LocalRateLimiter`（进程内，方法级同步保证多范围原子）；`FallbackRateLimiter`（唯一入口，切换与恢复）；`RateLimitWindow`（窗口/键名/TTL/再试秒数纯函数，时钟时区可注入）；`RateLimitMetrics`（拒绝观测计数）。无工厂、AOP 等额外层次。

## 8. 配置项

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `xinyu.rate-limit.enabled` | `true` | 关闭时所有检查直接通过 |
| `xinyu.rate-limit.key-prefix` | `xinyu:dev:` | 计数键前缀，按环境区分；YAML 中须带引号 |
| `xinyu.rate-limit.redis-timeout` | `200ms` | 单次 Redis 命令超时，超时即降级 |
| `xinyu.rate-limit.fallback-cooldown` | `30s` | 降级冷却，期内不再尝试 Redis |
| `xinyu.rate-limit.mood-per-hour` 等 5 项 | 见第 2 节 | 各范围额度 |
| `spring.data.redis.url` | 空（`REDIS_URL`） | 留空只用本机计数；支持 `redis://[:密码@]主机:端口/库号`，密码只经环境变量，不写进文件 |

全部支持 Spring 宽松绑定的环境变量覆盖（如 `XINYU_RATE_LIMIT_MOOD_PER_HOUR`）。应用主类排除了 Boot 的 Redis 自动配置（客户端由限流自建），url 为空时应用正常启动、现有测试不需要 Redis。

## 9. 本机开发说明

1. 起本机 Redis：`brew install redis && brew services start redis`（或 `docker run -d -p 6379:6379 redis`）。
2. 让后端用上它：`export REDIS_URL=redis://127.0.0.1:6379/0` 后启动后端；启动日志不再出现"未配置 Redis"，后台概览"限流后端"显示 Redis。
3. 看计数键：`redis-cli --scan --pattern 'xinyu:dev:rl:*'`，`TTL` 可见过期。
4. 体验降级：`redis-cli shutdown nosave` 后继续发请求，服务照常工作，后台概览显示"本机（Redis 降级中）"；重启 Redis，约 30 秒后自动恢复。

## 10. 部署注意（上线前）

- 服务器 Redis 只监听本机或内网；设置密码（`REDIS_URL=redis://:密码@127.0.0.1:6379/0`，密码经环境变量注入）。
- `maxmemory` 设上限、淘汰策略用 `allkeys-lru`：计数键都有 TTL，属可丢数据，不需要持久化（可关 RDB/AOF 或接受默认快照）。
- 生产前缀用 `xinyu:prod:`，避免与本机开发键混淆。
- 多实例部署前必须重评本机降级的计数互通代价（见第 4 节），并先完成总方案第 7.2 节的租约/互斥部分。

## 11. 测试

- 单元（不依赖 Redis）：窗口对齐（整点/零点/跨天/Asia/Shanghai）、本机限流边界与多范围原子、降级切换与日志各一条、三入口行为（照常保存 / 错误码 / 429 与 Retry-After / 危机豁免 / 社区不写兜底）。
- Redis 集成（`RateLimitRedisIT`，需 `AI_TEST_REDIS_URL` 指向本机 Redis 专用库如 `redis://127.0.0.1:6379/15`，键前缀 `xinyu:test:{随机串}:` 且结束自删；未设置时跳过并说明原因）：Lua 多范围原子性、TTL 边界、50 并发恰好 20 通过、坏地址降级与恢复。
- 不设 Redis 时默认测试套件必须全部通过。
