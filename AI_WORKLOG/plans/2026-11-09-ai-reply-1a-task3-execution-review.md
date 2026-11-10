# 心屿 Task3 执行方案 v2（审查修订稿，附 Codex 复核）

日期：2026-11-09
状态：3A已完成；3C单次执行、用户失败收尾及真实MySQL事务／行锁验收已通过。数据库连接／网络等待验收在3D启用worker前完成，11.3整体尚未通过。
用途：用户提供并已授权开始的 Task3 执行依据。本文件在原位置整合 v2，并将沿用的 v1 内部协议、社区步骤、文件清单及命令收进附录，不要求读者另找旧版。v2 的改变优先于沿用内容；Codex复核意见单列第11节，已纳入实施验收要求；权威契约随本次开工同步，代码／验证／带读分别记录。

当前交接：3C的finishFailure由用户参照分步提示填写；领取、预检、complete及fail均用独立5秒短事务，AI HTTP调用在数据库事务外。Java17本轮236项通过（含38项真实MySQL、15项合成供应商Python HTTP），不启扫描或注册Bean；第一轮固定范围复审已通过。下一步先带读领取／预检超时补充，再进入3D+3E。本批按用户授权分批整理本地提交；既有3A提交至6687e52，历史文档整理至63e80d7，未推送。3B冷却函数／CAS仍由用户手写。

## 0. 相对 v1 的修订摘要

| 项目 | v1 | v2 决定 |
| --- | --- | --- |
| 时间预算 | 领取、HTTP、解析、提交共用 50 秒，并核验 JDBC/锁等待遵守剩余预算 | 拆成两段：生成阶段 50 秒；提交与失败收尾各自固定短上限 |
| 冷却规则 | 每 3 次失败固定 180 秒 | 每 3 次失败进入一轮冷却，时长逐轮翻倍：180 秒起，封顶 3600 秒（指数退避） |
| 尝试次数上限 | 无限，并专门处理 INT 溢出 | 不设总上限，删除溢出处理；attempt_count 作为普通递增计数 |
| 冷却 API | retryAvailableAt＋serverTime（UTC），429 错误体携带任务 VO 及专用处理器 | 只返回 retryAfterSeconds（数据库计算）；冷却中 retry 返回 429＋Retry-After，前端重新 GET |
| 执行顺序 | 3A→3B→3C→3D→3E→3F | 3A→3C→3D+3E→3B→3F，先跑通主流程 |
| 独立评审 | 每批一轮，共 6 轮 | 按风险合并为 3 轮 |
| HTTP 客户端 | Apache HttpClient 5 | 取消探针同时验证 JDK HttpClient 与 Apache HC5，择优 |
| 协作 | Codex 写核心代码，用户带读 | 用户亲手实现两处关键逻辑（见第 9 节），其余不变 |

## 1. 目标与范围

让已保存的心情 AI 任务真正执行：后台领取任务、事务外调用 Python、校验结果并条件提交；失败可回查，按冷却规则手动重试。旧版本、已删除来源和旧 attempt 的结果不能覆盖当前记录。

本次交付：
1. Java→Python 内部 HTTP 客户端：内部认证、严格协议、错误分类、有限响应读取、可取消的生成阶段总截止。
2. 单次执行闭环，以及 complete 异常后的新事务失败收尾。
3. worker 启动恢复、扫描、停止领取、在途请求取消与执行退出。
4. 单 Java 进程 3 个执行槽位；Python 单应用进程生成门禁同步为 3。
5. 逐轮翻倍的手动重试冷却。
6. 社区同步 AI 的超时与假成功修复、后台 AI 日志 VO、日志与搜索隐私边界。

不在本次范围：前端轮询与倒计时（Task4）、后续社区异步化、Redis、跨实例限流、租约、跨用户公平调度（1b）、聊天、SSE、周报、自动模型重试、用户取消接口、历史日志批量清理。

Task3 后端完成后不能单独部署，必须与 Task4 联调验收后同批发布。

## 2. 不变量（沿用 v1，必须一直成立）

1. Java／MySQL 管归属、任务与结果权威，Python 不写业务库。
2. 网络调用不在来源行锁或业务事务内。
3. 提交校验 user、target、version、status、enabled、attempt、request_id；旧执行不能覆盖新状态。
4. 来源修改／删除／complete 先锁来源再操作 job；retry 不反向锁来源。
5. 一次执行单元最多领取一条；同一 job 的 running 领取最多成功一次；不自动重试供应商。
6. 只有 MODEL 写模型成功日志；SUPPORT 不伪造成模型成功；失败不猜测供应商是否调用或计费。
7. worker 默认关闭；关闭时不恢复、不扫描、不领取。
8. 三槽只限制心情异步执行；社区同步 AI 是独立路径。

## 3. 关键决定

### 3.1 时间预算：两段式

| 阶段 | 范围 | 上限 |
| --- | --- | --- |
| 生成阶段 | 领取成功后：来源预检 → 构造请求 → HTTP 往返 → 解析与语义校验 | 总计 50 秒（单调时钟绝对截止）；Java connect 2 秒、read 45 秒，均受剩余时间约束；budget_ms 取 min(40000, 剩余时间－预留余量) |
| 提交 | complete 短事务 | 事务超时 5 秒，叠加 MySQL innodb_lock_wait_timeout |
| 失败收尾 | 独立短事务 fail(...) | 事务超时 5 秒 |

- 生成阶段截止后必须真实中止 I/O，槽位在执行线程实际退出后才归还。
- 提交与收尾是毫秒级短事务，不再要求它们"遵守生成阶段剩余预算"。验收只需证明：提交有上限、超时会抛出并走失败收尾、失败收尾本身也有上限。
- 数据库完全不可用时，失败收尾也可能失败；此时只安全记录追踪 ID 与分类，任务留在 running，由下次启动恢复处理。不承诺数据库故障时仍能写入 failed。

### 3.2 冷却：逐轮翻倍（指数退避）

规则：每累计失败 3 次进入一轮冷却，冷却结束后可继续手动重试；attempt_count 累计不清零，不设总上限，不做整数溢出处理。

冷却时长由 Java 纯函数计算，数据库只负责比较时间：

```java
/** 每失败 3 次进入一轮冷却，每轮翻倍，最长 1 小时。 */
static long cooldownSeconds(int attemptCount) {
    if (attemptCount == 0 || attemptCount % 3 != 0) return 0;
    int round = attemptCount / 3;
    return Math.min(180L << Math.min(round - 1, 10), 3600);
}
```

| 失败次数 | 冷却 |
| --- | --- |
| 3 | 3 分钟 |
| 6 | 6 分钟 |
| 9 | 12 分钟 |
| 12 | 24 分钟 |
| 15 | 48 分钟 |
| 18、21、24……（之后每 3 次失败） | 60 分钟 |

requeue 在原有完整 CAS 上增加冷却条件，Java 用同一个预期 attempt_count 计算 cooldownSeconds 后传入：

```sql
AND (#{cooldownSeconds} = 0
     OR finished_at IS NULL
     OR CURRENT_TIMESTAMP >= finished_at + INTERVAL #{cooldownSeconds} SECOND)
```

- finished_at 为 NULL 属于异常数据，按"无需等待"处理，避免任务永久卡死；同时记录分类日志，不回写猜测的时间。
- 容量拒绝产生的 failed（attempt_count=0）冷却为 0。
- 系统中断（execution_interrupted）同样计入 attempt，可能触发冷却。
- 移除所有"永久最多 3 次"限制：领取 SQL 的 attempt_count<3、requeue 的 attempt_count<3、Java isCurrentAttempt 与 MoodAiResponse 的 attempt≤3、Python Correlation 的 le=3。Python attempt 改为正整数（ge=1），不设上限。
- 不提供成功任务的重新生成入口；任务成功即终止。
- 不使用随机抖动（jitter）：重试由用户手动分散触发，且执行受 worker 排队和 3 槽限制。

### 3.3 冷却 API

- AiJobVO 增加 `retryAfterSeconds`，在查询时由数据库计算：

```sql
GREATEST(0, TIMESTAMPDIFF(SECOND, CURRENT_TIMESTAMP,
         finished_at + INTERVAL #{cooldownSeconds} SECOND))
```

  非 failed 状态或 cooldownSeconds=0 时返回 0。
- `retryable` 语义：来源有效、状态 failed、retryAfterSeconds=0。不保证请求时容量仍充足（容量不足仍返回 503）。
- 冷却中调用 retry：先完成归属与来源校验，再返回 HTTP 429＋`Retry-After: <秒数>`，Result 沿用普通错误结构，不携带任务 VO。前端收到后重新 GET 刷新状态。
- GlobalExceptionHandler 的 ResponseStatusException 分支只做一处最小修改：透传异常自带的响应头（以便 Retry-After 生效），不改变其他错误的结构。
- 不再返回 retryAvailableAt、serverTime，不做 UTC 转换，不新增专用异常处理器。

### 3.4 其余沿用 v1 的决定

- 内部 HTTP 协议、严格解析、错误映射表、request_id 为 null 的处理：见本文件附录 A。
- 客户端读取保护：响应体 16 KiB、响应头 8 KiB，禁止重定向，禁止自动重试。
- 配置：AI_WORKER_ENABLED（默认 false）、AI_SERVICE_BASE_URL、AI_INTERNAL_TOKEN、AI_WORKER_CONCURRENCY（默认 3）、AI_MAX_CONCURRENT_REQUESTS（Python，默认 3）、扫描间隔 1 秒。Python 只运行一个应用进程。
- 数据库：不新增表、字段、索引或约束，只修改必要 SQL。只用全新隔离测试库。
- 后台 AI 日志改用 AdminAiReplyVO，keyword 只搜允许字段。
- 社区：连接 2 秒、读等待 15 秒初值，经最多 5 次合成输入真实采样校准；失败不写兜底文案，帖子保留，ai_reply 为 NULL；编辑先显式清旧回复；新日志不存 prompt。

## 4. 执行顺序

```text
3A 内部客户端 → 3C 单次执行与失败收尾 → 3D+3E 生命周期与三并发 → 3B 冷却 → 3F 社区与隐私
```

理由：先让"领取 → 生成 → 提交"端到端跑通，再在可运行的系统上叠加冷却。3B 放宽 attempt 范围会修改 3A 的协议测试，届时一并更新。

### 3A：内部客户端与可取消调用

1. 取消探针：同时用 JDK `java.net.http.HttpClient`（取消 sendAsync 的 Future）与 Apache HttpClient 5（请求 cancel）验证"无响应"和"持续慢正文"两种情况下，到截止时连接被真实关闭。择优选用，记录依据；不升级 Boot／Java。
2. RED：认证头、snake_case、严格类型、关联字段、错误码映射、响应超界、慢响应、重定向拒绝。
3. 实现 MoodAiRequest、内部错误表示、AiServiceClient；复用 MoodAiResponse 语义校验；不改全局 Jackson 规则。
4. 注入单调时钟与短测试预算，验证生成阶段截止。
5. 启动真实 Python 应用＋合成 Provider，按 test-contracts/mood-types.json 跑 13 种心情的 Java→Python 真 HTTP 矩阵。

交付：可独立调用的客户端；worker 仍关闭。

### 3C：单次执行与事务异常收尾

1. 优先写：completeFailureIsMarkedFailedInNewTransactionAndCanRetry、lateFailureDoesNotOverwriteSupersededOrNewAttempt。
2. AiJobWorker.runOne：领取至多一条 → 保存本 attempt 身份 → sourceForAttempt → 构造请求 → 事务外调用客户端。
3. sourceForAttempt 为空：结束，不调用 Python。complete 返回 false：按现有语义结束，不再覆盖状态。
4. 客户端分类失败：条件 fail。complete 抛异常：原事务结束后，以独立短事务 fail(internal_error)。worker 外层不加 @Transactional。
5. fail 也失败：只记录追踪 ID 与分类，任务留在 running 等待启动恢复；不循环调用模型，不打印可能含原文的异常文本或堆栈。
6. 提交与收尾的 5 秒事务超时在此批落实，并用真实 MySQL 锁等待验证超时会抛出、收尾会执行。

交付：手动调用 runOne 可完成真实单次执行闭环。

### 3D+3E：生命周期与三并发

生命周期：
1. 优先写：startupRecoversOnceBeforeFirstScan、recoveryFailureDoesNotStartScanning、workerDisabledDoesNotRecoverOrClaim。
2. 启动：校验配置 → 恢复一次 → 恢复成功后注册唯一扫描；恢复失败不扫描。
3. 停止：关闭领取闸门 → 停扫描 → 取消在途 I/O → 等待执行退出 → 释放资源。已提交的结果不倒退。
4. 重复启动／停止幂等，不重复恢复，不注册第二个定时器。
5. 文档写清单服务器部署顺序：先停旧进程并确认退出，再启动新进程。代码内幂等不等于跨进程互斥，不引入租约或主机锁。
6. 停止时先取消客户端实际I/O，数据库执行器保留到收尾续接结束，再关闭执行器；用屏障验证shutdown不拒绝handleAsync。取消runOne的组合Future不等于取消底层调用；正常超时与停止时，都须确认HTTP／DB实际退出后才归还槽位，不能只看逻辑Future完成。

启用worker Bean前的数据库门槛（承接11.3，当前未通过）：
- [ ] 沿用Hikari，明确有限的connectionTimeout／validationTimeout，以及Connector/J的connectTimeout／socketTimeout；核对实际数据源生效值，不新增池或修改数据库全局参数。
- [ ] 在隔离环境验证连接耗尽／不可用、慢读取／网络停滞、commit／rollback响应停滞时的线程退出与槽位归还；不以事务SQL超时代替这些证据。
- [ ] 若JDBC使用主机名，说明驱动原生DNS等待的限制；按实测写清分段预算和无法严格保证的墙上时间，不宣称所有等待均为硬5秒。

三并发：
1. Java 默认 3 槽；调度只在有空槽时提交执行单元，由执行单元领取任务；数据库是唯一待办队列。
2. 槽位归还：无任务、提交失败、来源失效、调用失败、正常完成、停止，均恰好释放一次；线程或 I/O 未结束前不提前释放。
3. Python 门禁配置化，初值 3；检查与计数之间无 await，finally 递减。
4. 用可控屏障证明：三条同时进入生成、第 4 条保持 queued、释放一条后补一条、最大在途数≤3。测试数据来自至少两个用户。
5. 一个槽超时不影响另两槽；停止期间不再补领。

交付：可控的生命周期与三并发上限证据。

### 3B：逐轮翻倍冷却

1. 先把 3.2、3.3 节同步到权威契约。
2. cooldownSeconds 纯函数：参数化单元测试覆盖 0、1、2、3、4、6、9、12、15、18、30。
3. 移除所有 attempt≤3 限制（Java SQL、Java 校验、Python 契约），并更新 3A 的协议测试。
4. requeue CAS 加冷却条件；AiJobVO 加 retryAfterSeconds；retry 冷却中返回 429＋Retry-After。
5. 真实 MySQL 验证：冷却前被拒、到点放行（通过调整 finished_at 模拟，不真实等待）、第 4 次和第 7 次能正常领取并提交、双击重试只入队一次、冷却期间来源删除后重试返回 404。

交付：服务端执行冷却；前端倒计时在 Task4 实现。

### 3F：社区超时、正确失败与日志隐私

沿用本文件附录 B 的全部步骤，包括：最多 5 次真实合成采样校准超时（缺授权或凭据则标记待校准）；修正 AiReplyService 的空响应、错误结构和异常打印；新帖失败保持 ai_reply 为 NULL；编辑时用指定列 UPDATE 显式清除旧回复；新日志不存 prompt；后台 VO 与允许字段搜索；捕获日志验证无 token、prompt、日记原文和上游敏感正文。

## 5. 验收矩阵

| 编号 | 必须证明 | 证据 |
| --- | --- | --- |
| HTTP-01 | 认证、严格类型、snake_case、关联字段正确 | 真回环 HTTP |
| HTTP-02 | 13 种心情跨 Java→Python 被接受 | mood-types.json＋真 Python＋合成 Provider |
| HTTP-03 | 200 非法结果、错误分类、超界、重定向被拒，无伪成功 | 客户端真实响应读取 |
| TIME-01 | 无响应与持续慢正文在生成阶段截止时中止 I/O，下一任务可执行 | 短预算探针＋连接关闭证据 |
| TIME-02 | 提交与失败收尾有固定上限，超时后走收尾 | 真实 MySQL 锁等待探针 |
| TX-01 | 日志插入失败 → 回复与终态回滚 → 新事务 failed → 可重试 | 真实隔离 MySQL |
| TX-02 | 删除／新 attempt 后，旧结果与旧失败收尾不能覆盖 | 两种先后时序 |
| LIFE-01 | 恢复一次且先于扫描；恢复失败不扫描 | 生命周期测试 |
| LIFE-02 | worker 关闭时零恢复零领取；停止后不再补领 | 启动／停机竞争测试 |
| CONC-01 | 三条真实重叠，第 4 条留 queued，最大在途≤3 | 屏障／事件，至少两个用户 |
| CONC-02 | 一槽超时或失败不阻塞另两槽；槽位与门禁恰好回收 | Java＋Python 真实回环 |
| RETRY-01 | 冷却时长表、冷却前拒绝、到点放行、第 4／7 次可领取 | 纯函数测试＋真实 DB CAS |
| RETRY-02 | 双击重试只入队一次；429 带 Retry-After；冷却中删除来源返回 404 | 服务＋真实 DB＋MVC |
| COMM-01 | 慢 AI 时帖子保留、无公开兜底、编辑无旧回复 | 真延迟 HTTP＋真实社区路径 |
| PRIV-01 | 管理端无原文和原始错误，keyword 不搜隐藏全文 | 真实 MVC＋SQL 验证 |
| AUTH-01 | 未登录、跨用户、来源软删、旧版本被正确拒绝 | JWT＋拦截器真实路径 |
| LOG-01 | 新日志无 token、prompt、日记和敏感上游正文 | 捕获应用日志 |
| CAL-01 | 社区超时有采样校准记录 | 真实供应商，缺环境则明确未验 |

跳过的测试必须单列数量，不能写成"真实数据库通过"。

## 6. 独立评审（3 轮）

| 轮次 | 范围 | 重点 |
| --- | --- | --- |
| 第 1 轮 | 3A＋3C | 协议校验、取消与资源释放、事务回滚与新事务收尾、迟到执行隔离 |
| 第 2 轮 | 3D＋3E | 恢复顺序、停止竞争、槽位泄漏或重复释放、Python 门禁与线程安全 |
| 第 3 轮 | 3B＋3F | 冷却 SQL 与 CAS、429 行为、权限、社区失败语义、原文泄露与隐藏搜索 |

评审前固定 commit SHA（或基准 SHA＋文件清单＋逐文件哈希）。影响下一批的阻断问题必须先处理。

## 7. 每批交付

1. 实际 diff 与新增／修改文件清单。
2. 真实源码主链带读，1～2 个关键预测或复述。
3. 代表性测试说明、命令、结果、未验证范围。
4. 评审发现与处理（评审轮次结束时）。
5. 更新原计划勾选与接续状态记录。

六批是实施单位，不等于自动提交；获准提交时按目的拆分，不把整个 Task3 合成一次大提交。

## 8. 回退与发布边界

实施期间 worker 保持关闭，用隔离环境显式启动测试。Task3 不执行云端迁移、提交、推送或部署。完整 1a 发布顺序沿用既有约定：停旧应用并确认退出 → 备份与迁移核验 → 启动新版本（worker 先关闭）→ 基础验证 → 确认唯一 worker 后启用。

## 9. 协作分工

Codex 负责客户端、生命周期、并发、社区修复、测试与文档。以下两处由用户亲手实现，Codex 提供脚手架、测试和评审，不提前代写：

1. **3C 的失败收尾**：runOne 中捕获 complete 异常后，以独立短事务调用 fail(internal_error)，以及 fail 本身失败时的处理。
2. **3B 的冷却逻辑**：cooldownSeconds 纯函数与 requeue 的冷却 CAS 条件。

这两处是 Task3 最能体现事务边界与并发设计理解的部分，适合作为面试时深入讲解的实例。

## 10. 实施检查单

- [x] 用户确认开始，3.1～3.3节已同步权威契约；实现状态单独记录。
- [x] 3A客户端、取消与DNS修复、13种心情真HTTP通过；独立客户端预审及delta复核完成。带读入口与调用链已交付，用户理解不等于测试通过；第一轮3A＋3C评审已通过。
- [ ] 3C单次执行、新事务失败收尾及SQL行锁验收已通过；领取／预检超时补充待带读，11.3剩余边界由3D接续。
- [x] 第1轮3A＋3C固定范围独立评审完成，两个SQL超时遗漏已修复；3D接入前置项见上方任务，不代表其已通过。
- [ ] 3D+3E 生命周期、三并发、槽位与门禁回收通过并带读。
- [ ] 第 2 轮独立评审完成。
- [ ] 3B 冷却函数、CAS、429、attempt 范围放宽通过并带读。
- [ ] 3F 社区、后台、日志修复通过，采样状态如实记录。
- [ ] 第 3 轮独立评审完成。
- [ ] 两端回归与后端全量完成，跳过与未验项单列。
- [ ] 权威契约、总计划、进度与接续状态一致，明确 Task4 接续与不可部署边界。

## 11. Codex 复核补充（已纳入实施验收，未实测部分不是完成结果）

### 11.1 冷却剩余秒数的 NULL 分支需要补齐

v2 第 3.2 节选择 finished_at=NULL 时放行并记录异常，这是用户修订提案的取舍；本轮保留该选择，不改回 v1 的拒绝策略。

但第 3.3 节的 GREATEST(0, TIMESTAMPDIFF(...)) 在 finished_at=NULL 时返回 NULL，不会得到 0。这会使 VO、retryable 和 requeue 的放行条件不一致。建议在查询中显式将“非 failed／冷却为 0／finished_at 为 NULL”都归为剩余 0 秒，合法时间再计算；429 的 Retry-After 必须是正整数。用户手写的冷却 CAS 条件仍由用户完成，Codex 只给边界契约和 RED 测试，不提前写核心答案。

依据：[MySQL GREATEST 的 NULL 规则](https://dev.mysql.com/doc/refman/8.4/en/comparison-operators.html#function_greatest)。本轮为文档／源码复核，尚未执行真实 SQL。

### 11.2 当前秒精度应一致，避免未来出现提前 0 秒

当前 finished_at 使用 DATETIME，CURRENT_TIMESTAMP 未指定小数精度，按相同整秒口径比较时，可沿用 v2 的 SECOND 差值。不要无意只将查询端改成微秒时间，再用整秒截断作为资格判断；若以后保留亚秒精度，应对正剩余向上取整，并保持 GET、CAS 和 Retry-After 一致。

补充验收：finished_at=NULL、刚到点／点前、非 failed、有小数时间的探针输入。该验收补充不提前代写用户负责的纯函数或 CAS。

依据：[MySQL 时间函数、TIMESTAMPDIFF 的整数单位和精度](https://dev.mysql.com/doc/refman/8.4/en/date-and-time-functions.html#function_timestampdiff)。

### 11.3 5 秒必须区分配置目标与实际有界证据

采用 v2 的分段方向：领取成功后的生成阶段 50 秒，complete 与失败收尾各自配置 5 秒事务超时；不继续要求它们消耗生成阶段剩余时间。但“设置事务超时 5 秒＋innodb_lock_wait_timeout”本身不能证明整个方法在 5 秒内结束，更不能将故障场景描述成实际都是毫秒级。

3C 必须核验实际 MyBatis／驱动是否应用 statement timeout，连接获取、行锁、网络阻塞、commit／rollback 的等待边界，以及超时后的线程退出。innodb_lock_wait_timeout 只约束相应锁等待，session 级配置在连接池复用时还须恢复／保持明确策略；不修改全局数据库参数。生成阶段中的来源预检，以及 50 秒开始前的 claim，也不能因分段而无限占住槽位。

补充验收：真实锁等待之外，覆盖连接不可用／慢数据库读取及收尾失败。槽位必须在线程与 I/O 实际结束后归还。如果当前驱动无法兑现 5 秒的严格墙上时间上限，先如实记录约束与可测清理界限，再修订这句承诺；不扩大成新的复杂控制平台，也不把仅有配置的测试当资源释放证据。

2026-11-10核验：MyBatis确实把Spring事务剩余时间应用到Statement查询超时；claim、预检、complete、fail的真实行锁探针通过。连接获取／事务启动发生在该计时边界之前，commit／rollback也不受同一查询计时器覆盖，因此本条整体保持未完成；剩余配置与故障注入已列为上方3D启用worker前的明确门槛。

依据：[Spring DataSourceTransactionManager 的事务超时应用条件](https://docs.spring.io/spring-framework/docs/6.2.x/javadoc-api/org/springframework/jdbc/datasource/DataSourceTransactionManager.html)。

### 11.4 不增加溢出专项，但不宣称物理无限

按用户 v2，不新增 attempt 溢出的专门业务机制、字段迁移或专项练习，Python 保留 ge=1。当前 MySQL INT 和 Java Integer 仍有类型边界，所以“不设总上限”指不设置业务尝试次数上限，不能解释成现有类型可无限递增；异常沿普通数据库失败处理，不承诺自动绕过类型边界。

### 11.5 客户端选择以实际 Java 17 探针为准

JDK HttpClient 与 Apache HC5 各测无响应、持续慢正文、响应超界后关闭与重复取消。JDK 路径要确认 Future 覆盖的完成点是否包含正文消费，不能响应头一到就把正文 deadline 丢失；仅调用 cancel 返回成功不算连接释放。选型后将依据写到现有进度记录，不维护两套生产客户端。探针允许放在隔离临时目录，不运行真实模型。本轮Java17实测两者均能取消，采用HC5 5.5.2的明确关闭及读／报文控制；JDK并非取消失败。预审发现同步DNS缺口，改为初始化地址快照；失败不阻止业务启动、生成安全provider_error，恢复解析／地址更新需重建或重启。原生DNS忽略中断时单条初始化daemon可能留待OS返回，不能夸大线程清理保证。

### 11.6 局部手写的真实分工

本 Task 以用户提供的第 9 节为局部覆盖：3C 异常后的独立失败收尾，以及 3B cooldownSeconds 和冷却 CAS，由用户亲手实现；其他代码由 Codex 完成。本文第 3.2 节代码是用户提供的方案草案，不是 Codex 已实现的业务代码。实际练习先讲调用链、给未填核心的脚手架和 RED 测试，再检查用户保存文件；不先代写再象征性提问。实现通过与独立掌握分别记录，也不将这些协作经历包装成用户独立设计整套系统。

### 11.7 DNS自动恢复：共享查询并异步续接（2026-11-09已实现、验证及带读）

**执行要求**：用户随后明确“记一笔面试可用、别忘了方案、必须执行”。本项为必做，不是候选增强；当前先完成小段带读，随后作为3A补全、在3C worker接入前落实并验证，避免worker先按旧同步接口铺开再返工。未取得实现／测试／独立评审证据前保持待办，不能以旧版173项回归视为本项完成。

用户在带读中指出永久失败快照不能从临时DNS故障自动恢复，随后比较两种行为，最终确认采用“请求登记后续动作、同域名共享进行中的查询、成功后当前请求继续发送”的方案。它替代此前讨论的“触发后台查询的当前请求立即失败，只供后续请求使用结果”建议；不混用两种请求语义。采纳时源码仍为初始化一次的FixedAiDnsResolver；本轮已按11.8落地，180项回归和独立评审通过，证据见progress。

**请求行为**：

1. 已有可用IP时使用缓存建立HTTP连接；没有可用IP且没有正在执行的同域名查询时，发起一次查询并得到共享Future。
2. 请求A登记解析完成后的连接／发送动作，请求B、C到来时共享同一查询、各自登记后续动作；请求流程尚未完成，处理线程可返回处理其他工作，不使用get／join／sync阻塞等DNS。
3. 查询成功后更新缓存，在各请求仍有效、未超时且客户端未关闭的前提下继续各自HTTP请求；共享的是查询结果，不是HTTP请求、模型输出或任务身份。仍遵守总执行／连接容量，不能成功后绕过三槽限制一起无限发送。
4. 查询失败时分别结束当前等待请求，安全分类provider_error；缓存失败10秒作为当前可配置初值。有效期内新请求直接失败，到期后的新请求才允许触发下一次查询，不常驻定时反复查询，也不自动重新发起已经失败的模型请求。
5. “已有查询进行中”和“失败缓存未过期”是两个独立状态。即使等待超时，也不能让忽略中断的系统解析不断触发新的线程／查询；必须将逻辑超时、实际解析仍在执行与资源释放的边界单独验证。

**与现有客户端的衔接**：落地前generate返回MoodAiResponse，内部为HC5经典阻塞HTTP调用，不能只加addListener就声称整条链非阻塞。实施时需要明确异步结果入口（建议CompletionStage／CompletableFuture），让DNS完成后续接发送；阻塞HTTP如继续使用，应放到有界执行资源，禁止在DNS回调线程中等待网络，禁止将回调里再get当作异步完成。异步请求的总截止从本次调用开始覆盖DNS等待和HTTP／校验，不在查到IP后重新获得50秒。超时或取消一个请求不能错误取消其他请求共享的查询；关闭客户端须阻止迟到查询结果启动新HTTP并结束未完成请求。接口与关闭实现先给具体小方案，再做RED／GREEN；不改变3C／3B用户手写分工。

**适用依据与取舍**：Java InetAddress支持成功／失败缓存；已静态核对Netty固定发布标记netty-4.1.118.Final的DnsNameResolver中doResolveAllCached→未命中时doResolveAllNow，以及配置启用时inflightLookups共享Future并登记监听的调用链。该依据支持缓存与查询合并，不证明心屿已有Netty异步连接。采纳这种行为不等于已经决定替换整个HTTP客户端或引入Netty依赖；具体库与资源成本须在落地方案中说明。当前只补“失败后恢复”，成功IP长期变化的TTL刷新策略另行明确，不把它伪称已覆盖。

**验收**：同域名A／B／C只触发一次物理查询，成功后当前三请求均可续接；解析失败三请求均失败，失败缓存内零新增查询，10秒边界后新请求只触发一项重查且恢复可用；控制线程／事件证明DNS等待期间处理线程可继续工作；单请求超时不伤其他等待者；超时后迟到成功不发送HTTP；客户端关闭不再续接；底层解析忽略中断时资源不持续增长。测试通过注入时间／解析替身和真实回环HTTP组合完成，不付费调用模型，不读取业务库。

**项目追问／面试素材（真实过程）**：用户在带读中指出“初始化DNS临时失败却永久缓存空地址，恢复需要重启”的可恢复性问题，并提出按请求触发重查；在Codex解释Future监听和Netty查询合并后，用户选择当前请求登记续接、同域名共享查询。可讨论为什么不用每请求同步解析、为什么短缓存失败与进行中去重是独立状态、为什么一个请求取消不能取消其他等待者，以及线程资源与总deadline的代价。本轮代码／测试由Codex完成，180项软件回归和独立评审通过，另有两个独立边界探针；没有生产／性能／模型质量结果，不写成已上线、性能提升或用户独立实现整套机制。新增异步主体和两项代表性测试已带读，用户独立掌握未验证。

### 11.8 DNS落地细化（2026-11-09，用户授权继续实施）

- 接口：新增 `CompletableFuture<MoodAiResponse> generateAsync(request, remaining)`，从入口开始计总deadline；同步generate仅作兼容，明确阻塞，3C必须使用异步入口。无外部HTTP／数据库结构变化。
- 资源：沿用HC5，最多concurrency个逻辑请求、同数目的阻塞HTTP线程，交接队列上限为concurrency；超容量安全overloaded。队列用于已完成／取消任务尚未归还物理线程的短暂交接，不增加逻辑请求容量。DNS单线程、最多一个实际查询，等待DNS不占HTTP线程。回调只做轻量续接，后续worker的数据库操作须使用自己的显式执行器。
- 固定主机的resolver由一次快照改为按请求prepare共享查询；HC5 resolve只读成功快照，绝不做系统查询。失败缓存默认10秒（AI_DNS_FAILURE_CACHE_SECONDS）；单次解析等待沿用connect上限2秒。逻辑解析超时后即使原生解析仍卡住也不启动新查询；迟到结果丢弃，实际查询退出后才可按缓存期限重新查询。关闭不宣称强制终止原生DNS。
- 成功地址本轮缓存到客户端关闭；动态IP／成功TTL刷新仍未覆盖，不擅自增加定时刷新。单请求取消或到期仅终止本请求，不能取消共享DNS；超时或关闭后不得发出HTTP。
- 涉及：AiServiceClient、FixedAiDnsResolver（名字表示固定服务主机）、AiServiceClientConfig及相应测试／本计划和接续记录。保留已在工作区的Claude可读性结构及其他用户改动；实现阶段不自动提交，带读后用户已授权整理本地提交，不扩展为推送。
- 步骤：先添加共享／失败缓存恢复、无HTTP线程等待、取消隔离、迟到成功、关闭、卡住解析和容量测试并观察RED；实现后运行目标测试及Java普通回归／显式Java→Python合成Provider IT；固定文件哈希交独立agent审并处理发现。

**11.8执行结果**：实现及180项Java普通／合成Python IT验证通过；review_dns_recovery固定9文件SHA256评审通过，无必须修复项，并实测原生式DNS卡住退出后的重查恢复、HTTP Future取消断连及复用。完成回调立即续接曾误报overloaded，已单项RED→GREEN修复；队列上限concurrency不增加HTTP线程或逻辑容量。两项独立探针纳入自动回归为低优先级后续建议，当前未并入JUnit。原生DNS可能仍留单条daemon至系统返回，成功IP TTL刷新暂缓；未验生产，未访问业务库／真实模型，用户在新增段／测试带读后授权整理本地提交；下一步进入3C，用户独立掌握未验证。

### 11.9 3C单次执行与验证（2026-11-10）

- 用户授权“推进”，先实现领取至多一条、保存attempt／requestId／版本及deadline、来源预检、事务外generateAsync、数据库执行器handleAsync续接、complete的独立5秒事务。新类不注册Spring Bean，不启动自动worker，不新增表／外部接口。涉及新AiJobWorker、AiJobWorkerTests、AiJobWorkerFailureLearningCases；保留工作区其余7文件改动。
- 用户手写位置为AiJobWorker.finishFailure：独立短事务按本attempt调用fail，CAS未命中不覆盖新状态，收尾也失败时只记录安全追踪字段并如实返回未收尾状态；原TODO已由用户参照分步提示填写；Codex清除占位异常残留，3项替身验收转GREEN，完整数据库验收前仍不接入自动worker。该共用入口同时用于客户端失败，避免Codex先写同一核心再让用户象征性补一行。success／complete=false链可测试，不能据此称完整闭环已完成。
- 设计依据：Spring Framework v6.2.18 TransactionTemplate.execute，先getTransaction、运行回调，回调异常时rollbackOnException后向外抛；正常时commit后返回。固定源码：https://github.com/spring-projects/spring-framework/blob/v6.2.18/spring-tx/src/main/java/org/springframework/transaction/support/TransactionTemplate.java 。因此catch放在execute之外，现有AiJobService.complete内部admission.transaction加入外层短事务。选择现有Spring模板，不增加消息队列／重试框架；5秒配置不是锁／网络／commit已验证的硬上限，MySQL实测仍待后续。
- 先写普通主体测试观察类缺失RED，再实现；新增领取后交接排队预算测试明确RED（预期48秒，实际50秒），修复为claim成功立即保存deadline。Java17普通*Tests＋合成Python契约IT共187项通过（0失败／错误／跳过），7项新主体测试为服务／客户端／事务管理器替身，不证明真实MySQL回滚。
- 用户失败收尾的3条LearningCases起初为RED，用户填写后转GREEN；当时普通回归＋合成PythonIT＋显式3项共197通过。LearningCases不由默认*Tests发现，仍须显式运行；用户参照代码填写，不记为独立掌握。
- 本轮在/tmp独立MySQL8.4实例的新建空合成库运行：日志插入失败时回滚回复／成功终态，新事务failed(internal_error)可落库且可重试；迟到成功／失败不能覆盖修改、删除和新attempt。新增8项真实数据库测试，持久化IT共38项通过；未访问业务库／真实模型，测试后独立实例已关闭，数据／日志保留。
- 评审发现claim与来源预检缺少SQL超时；分别真实行锁RED→GREEN，复用独立5秒短事务，并补预检前过期检查RED→GREEN。最新同次Java17回归236通过、0失败／错误／跳过，含15项真实Python合成供应商HTTP、38项MySQL及显式3项LearningCases。日志`/tmp/heartisland-3c-db-full.log`：claim5020ms、complete5090ms、fail5017ms、预检与fail同时被锁住合计10046ms，锁保持到断言后才释放。它们证明SQL行锁等待退出，不证明整个方法硬5秒。
- 第一轮3A＋3C复审固定22文件于`/tmp/heartisland-3a3c-final-review/manifest.json`，review_3c_db_boundaries核验全部哈希、只读审查通过，无新增必须修复项；核对上述日志，未独立重跑测试。新worker仍不注册Bean／扫描，3D前置门槛尚待完成；本批按用户授权分批整理本地提交，不推送或部署。

- 提交前可读性复核（2026-11-10）：Standards与Spec分别固定快照审查；补齐新worker及测试的分支／循环分段、单调纳秒命名，修正加锁前读取的事务注释，去除测试进度注释，均仅写法调整。两轴delta复核通过；本轮Java17普通／合成HTTP／显式LearningCases共198项、Python离线／回环114项通过，未重跑MySQL，前述38项保留为本轮写法调整前的真实组件证据。

## 12. 后续社区异步 AI 回复（三并发意向，2026-11-09）

用户在提供 v2 后明确要求记录：社区发帖／编辑也在后续采用“先保存、后台异步生成 AI 回复”，社区执行并发初值同样设为 3，并保留可配置。该需求不插入当前 Task3／Task4；3F 仍完成同步路径的超时与失败语义修复。

暂按心情 3 槽、社区另 3 槽的独立容量方案记录，这是 Codex 当前规划解释，不把用户的“也是 3”扩大为已经确定全部池拓扑；设计时核对是否共用执行池／Python 门禁和供应商全局预算。若两池独立且同时满载，合计可能有 6 条生成请求；当前 Python 门禁 3 的心情方案不能原样声称支持这种总容量。

实施前核验真实社区流程和相近开源源码，再给具体方案：帖子版本与更新／删除／关闭 AI 的失效规则、公开列表／详情状态、任务类型及来源关联、重试与恢复、前端回查，以及回复日志和权限。能否复用 ai_job 需要按来源字段及唯一约束核验，不自动加表、加列或复制一套系统；也不预先要求建立独立社区进程。后续独立规划、用户确认后实施，尚未排固定阶段或日期。

## 附录 A：沿用的内部 HTTP 协议与错误映射

接口：`POST /internal/v1/mood-replies`，沿用现有 Python 路径。

权限：固定服务地址＋`Authorization: Bearer <internal token>`。内部 token 由 AI_INTERNAL_TOKEN 注入，不传用户 JWT，不记录 token，不跟随 HTTP 重定向。仅用于回环／私网，不新增公开代理接口。

请求：request_id、job_id、attempt、source_version、mood_type、content、budget_ms。ID／版本为字符串，attempt 为严格整数，budget_ms≤40000；正文≤500 个 UTF-16 单位，心情使用现有 13 值。

成功回包：原关联字段＋reply_content、result_kind、model_called、template_version、model_name。先严格解析，再校验关联与语义；200 本身不算成功。

- 禁止字符串、数字、布尔之间的隐式转换，拒绝重复字段、缺失必需字段、尾随 JSON 和未知字段。
- MODEL：model_called=true、模型名非空；SUPPORT：model_called=false、模型名为 null。
- 回复去首尾空白后非空、≤2000 个 UTF-16 单位；拒绝非法 Unicode、错误模板版本及错关联字段。
- 拟将 Java 成功／错误响应正文读取上限统一设为 16 KiB，响应头上限 8 KiB；这是本稿新增的客户端保护初值，须审查后落实并测试超界中止。Python 现有请求头 8 KiB／正文 16 KiB 限制继续保留。

错误映射：

| 情况 | job 错误分类／处理 |
| --- | --- |
| Python 400＋invalid_request | invalid_request |
| Python 401＋unauthorized | unauthorized |
| Python 504＋provider_timeout／本地超时 | provider_timeout |
| Python 502＋provider_error | provider_error |
| Python 502＋invalid_model_response／200 非法数据 | invalid_model_response |
| Python 503＋overloaded | overloaded，不自动重发 |
| 连接拒绝或其他传输失败 | provider_error |
| 非法错误体、状态与 code 不匹配、非空 request_id 不匹配 | invalid_model_response |
| Python 未知 5xx | provider_error，仅保留分类，不保存原始正文 |
| 本地编排／结果提交内部异常 | internal_error |

Python 在认证／解析请求前失败时 request_id 可能为 null，不能将所有 null 错误体都误判为关联冲突；非空追踪 ID 必须匹配。失败无法确认模型是否调用时，model_called 保留 NULL。

## 附录 B：沿用的社区同步修复步骤

目的：修复 Task3 涉及的现有共享调用问题，不将社区全面异步化。

1. 在既有授权范围内先做最多 5 次顺序合成输入的真实社区模型采样，沿用真实 prompt、max_tokens=200；记录日期、模型／参数、耗时、中位数、最大值及失败数。缺少授权或可用凭据则记待校准，不自行寻找仓库外密钥。
2. 根据证据确认／调整社区连接 2 秒、读等待 15 秒初值；Task4 前端发布／编辑等待初值 20 秒，其他请求仍 5 秒。未采样可实现未校准初值并测试机制，但不能把校准验收勾为完成。
3. 修正 AiReplyService 的空响应、错误结构、正文类型、空白、截断／异常分支：失败无成功兜底，无自动重试，无异常直接打印。
4. 新帖先保存；AI 失败保持 NULL。编辑在生成前显式清旧回复，失败不恢复旧内容对应回复；使用指定列 UPDATE，不能依赖 updateById 忽略 null 的行为。
5. AiReplyLogService 新写入不存 prompt，失败 reply_content=NULL，错误只存允许分类；后台返回最小 VO，移除隐藏原文搜索。
6. 真实回环上游接收后不返回正文，用短注入读时限验证实际超时；断言帖子保留、旧回复清除、失败日志及公开 AI 卡片条件。
7. 捕获代表性结构化日志及 HTTP 错误，验证没有 token、用户日记、prompt、上游敏感 body；默认关闭 HTTP wire／正文日志。不声称完成全项目历史日志清理或所有第三方日志审计。
8. 验证成功正文仍展示、无 AI 选择零调用、管理权限／分页回归；独立评审正确性与隐私。

社区同步调用有阶段超时，不冒充整个发帖请求的总截止。社区多次并行编辑的版本化提交保护不在现有 1a 确认范围；本批如发现可复现的旧结果覆盖新内容竞态，先给最小修复方案，不把它混同于心情域已有版本保护或默默扩表。

学习重点：帖子保存成功和 AI 成功分别成立；只隐藏字段但保留全文搜索仍可能泄露信息。

## 附录 C：文件影响清单与源码入口

以下以类名／模块名列职责，新增名称为拟定，不代表文件已经存在。实现中按职责保持小范围，不为每个辅助函数拆一个类。

| 位置 | 预计新增／修改 |
| --- | --- |
| Java service | 新增 AiServiceClient、AiJobWorker 及生命周期／调度职责；修改 AiJobService、AiJobAdmission（仅确有事务需要时）、AiReplyService、AiReplyLogService、CommunityPostService |
| Java config | 内部客户端／worker 配置；修改社区 RestTemplateConfig；不混用两条调用链的超时资源 |
| Java dto／vo | MoodAiRequest、内部安全错误对象；修改 MoodAiResponse、AiJobVO；新增 AdminAiReplyVO |
| Java mapper | AiJobMapper 冷却／领取 SQL；必要的指定列社区回复更新，不扩表 |
| Java controller／exception | 管理列表 VO；冷却 429＋Retry-After，普通错误体无任务 data；现有 ResponseStatusException 分支透传自带响应头 |
| Python app | contracts.py attempt 范围，settings.py 并发配置，main.py 门禁；provider.py 仅在必要的资源边界有问题时修改 |
| Java tests | 客户端、worker、生命周期、三并发、冷却、真实 DB、社区超时、HTTP 权限与隐私 |
| Python tests | attempt 范围、三并发门禁、超时／异常回收与既有安全分支回归 |
| 文档／配置样例 | 当前契约、总实施计划、接续状态、原进度记录、Python README／环境样例和必要 Java 配置说明 |
| 前端 | Task3 不实施，Task4 配套接新 API／后台 VO／社区等待与提示 |
| SQL migrations | 当前无新增或修改计划 |

主要现有源码入口：

- [AiJobService.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/service/AiJobService.java)
- [AiJobMapper.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/mapper/AiJobMapper.java)
- [MoodAiResponse.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/dto/MoodAiResponse.java)
- [AiJobVO.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/vo/AiJobVO.java)
- [Python main.py](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-ai/app/main.py)
- [Python contracts.py](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-ai/app/contracts.py)
- [CommunityPostService.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/service/CommunityPostService.java)
- [AiReplyLogService.java](/Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend/src/main/java/com/xinyu/service/AiReplyLogService.java)

## 附录 D：测试命令与隔离环境

执行命令在仓库根目录启动 Python 测试，Java 测试在后端目录执行；以下是实施后命令模板，不表示现在已存在所有测试类或已经运行：

```bash
# 仓库根目录：两端协议／Python 回归，默认隔离真实模型
uv run --directory xinyu-ai --locked pytest -q

# 工作目录切换到后端后执行对应批次目标测试
cd /Users/jiangqiuyu/pre/heartisland/heart-island/xinyu-backend
sh ./mvnw '-Dtest=AiServiceClientTests,AiJobWorkerTests' test
sh ./mvnw '-Dtest=AiJobStateTests,AiJobLifecycleTests,MoodAiResponseTests' test
sh ./mvnw '-Dtest=RestTemplateConfigTests,CommunityAiTimeoutTests,AiReplyHttpTests,AuthHttpTests' test

# 真实 DB：先人工准备全新、显式命名的隔离库，并安全注入测试凭据
sh ./mvnw '-Dtest=AiJobPersistenceIT' test

# Task3 收口运行实际新增类与既有相关模块回归，再运行后端全量
sh ./mvnw test
```

生命周期、并发、HTTP 矩阵和 worker＋DB 用例可按职责扩展新测试类，最终命令以真实文件为准。AiJobPersistenceIT 目前只接受 AI_TEST_JDBC_URL 指定的全新 xinyu_test_ 前缀空库；每次重新运行前核验空库要求，不复用业务库。AI_TEST_DB_USER／AI_TEST_DB_PASSWORD 仅通过安全环境注入，不写进文档或输出。

缺 DB 配置时既有 IT 可能跳过；必须单列通过／失败／跳过数量，跳过不能写成“真实数据库通过”。回环端口、驱动或模型环境不具备时继续能独立完成的批次，并准确留下未验项，不扩大验证结论。

## 附录 E：关联资料

- [AGENTS.md](/Users/jiangqiuyu/pre/heartisland/heart-island/AGENTS.md)
- [学习与开发协作约定](/Users/jiangqiuyu/pre/heartisland/heart-island/ops/11-learning-collaboration.md)
- [当前 1a 功能契约](/Users/jiangqiuyu/pre/heartisland/heart-island/ops/features/ai-reply-service-v1a.md)
- [1a 总实施计划](/Users/jiangqiuyu/pre/heartisland/heart-island/AI_WORKLOG/plans/2026-11-07-ai-reply-1a-plan.md)
- [当前接续状态](/Users/jiangqiuyu/pre/heartisland/heart-island/ops/features/xinyu-next-session.md)
- [既有执行与决策记录](/Users/jiangqiuyu/pre/heartisland/heart-island/AI_WORKLOG/2026-11-07-ai-reply-1a-progress.md)
- [Task2 实现审查快照](/Users/jiangqiuyu/pre/heartisland/heart-island/AI_WORKLOG/2026-11-09-ai-reply-1a-task2-review.md)
