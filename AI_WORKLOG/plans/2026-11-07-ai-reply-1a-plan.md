# 心屿阶段 1a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 先由用户确认方案，再进入业务代码；本轮仅准备计划，不选择或启动代理执行。

修订：2026-11-07校准社区等待初值与失败展示；已补充update防复活、社区HTTP超时、发布/恢复边界与retry CAS；已包含deleted_at和实际行锁；以下删除均指status=0语义。1a增加删除时间，定期硬删除/匿名化仍不在1a实施范围。

**Goal:** 单实例下实现心情先保存、AI 异步生成、可回查 / 手动重试，并防止假成功和旧结果回写。

**Architecture:** Java / MySQL 管日记、任务与最终结果，单进程 worker 按空闲并发槽位扫描数据库；Python 只做内部认证、风险分支和单次模型生成。任务领取使用 CAS，最终提交在短事务里校验来源版本与尝试标识。

**Tech Stack:** 现有 Java 17 / Spring Boot 3.5.14 / MyBatis-Plus / MySQL / Vue3；新 Python 3.12 / FastAPI / Pydantic / httpx，依赖实施时锁定。

**Spec:** [1a 功能设计](../../ops/features/ai-reply-service-v1a.md)；[总方案 v0.3](../../ops/features/xinyu-upgrade-master-plan.md)。先读两者及根目录 AGENTS.md，冲突先改设计，不在代码里暗改范围。

## Global Constraints

- 初始部署限定一个启用worker的Spring Boot进程；用户2026-11-09选定Task3单进程执行并发初值为3，Java执行槽位与Python内部生成门禁同步设为3，上限保留可配置。供应商额度与实测用于后续校准，不将初值写成性能已验证。扫描间隔保留1秒，只领取空闲执行槽位可容纳的任务；不做多实例租约／Redis／SSE／聊天。
- 任务状态 queued / running / succeeded / failed / cancelled / superseded；3B已按确认的v2实现为每三次失败冷却180秒起逐轮翻倍、封顶3600秒后可手动继续、累计attempt不重置；Java/Python协议和SQL保护同步调整，不自动重试模型。
- content 最大 500 个 UTF-16 code unit；回复最多 2000；max_tokens由OPENAI_MAX_TOKENS配置，默认4096，本服务上限8192；thinking样例disabled，也允许enabled，预算与40秒deadline独立生效；未经真实模型校准。
- Java connect 2秒／read 45秒，领取成功后生成阶段总时限50秒；complete与失败收尾分别配置5秒事务超时（3C实测有效边界）；Python 总时限 40 秒，供应商 connect 2 秒 / read 35 秒且受总时限约束。
- 持久 queued 上限 100；单用户 queued/running 上限 3；单实例短事务准入锁只包容量检查与保存。
- 轮询间隔 2 秒、单次超时 5 秒、最多观察 120 秒；仅可见页与打开详情，最多 6 个，不重叠。
- 风险分支使用内置具体支持文案，不依赖求助资源文件、地区或核验日期；不推测地区或输出电话号码。按用户2026-11-07修正移除90天启动门禁，无此配置也须能启动和生成普通回应。
- 沿用Result包装与Long转字符串；add/update的data从布尔改为MoodSaveVO；Task3到Task4完成之间禁止部署，前后端同批验收发布；先迁移再启动新应用，新旧应用／worker不得同时处理业务。
- 社区共享RestTemplate初值connect=2000ms/read=15000ms；发布与同步编辑单独Axios timeout=20000ms，其他接口5000ms。实施时先以最多5次真实模型合成样例采样确认/调整初值，无自动重试；阶段等待限制不冒充整个请求总deadline。
- 社区生成失败不写公开兜底：新帖community_post.ai_reply保持NULL；编辑先清除旧内容对应回复，失败不恢复。仅成功非空结果可公开展示，失败日志reply_content=NULL。
- 仅AI_WORKER_ENABLED=true且旧worker已停止时启动恢复/扫描；关闭的实例不得调用recoverInterrupted或claimNext。
- 只增量迁移，禁止执行破坏性初始化SQL；1a保留现存历史prompt，仅全站ADMIN后台允许查看和搜索；后续专项清理按保留期删除/匿名化其关联数据。
- 本次迁移增加deleted_at DATETIME NULL DEFAULT NULL；首次软删除写数据库当前时间，普通更新/AI提交不写此列，重复删除不重置。历史status=0行保留NULL表示时间未知，不以update_time/迁移时间推算。
- 软删除同一事务置status=0、content_version+1、deleted_at=本次删除时间、enabled=0、ai_reply=NULL，queued/running/failed置superseded，保留来源/job行；提交WHERE同时校验status=1与来源版本。重试、状态读取和unavailable分类前也校验status=1。
- update/delete/complete必须在同一实际DB事务/连接中先SELECT ... FOR UPDATE锁日记，后用带id/user_id/status=1/content_version条件的UPDATE更新指定列，再操作job；事务结束释放锁，网络不在锁内。禁止无锁selectById→updateById整实体回写；update不得写status或deleted_at，必须SQL内version+1。retry只改job，不锁来源，使用带预期attempt_count的CAS并遵守同样准入限制。
- 不自动提交、推送、部署或操作生产数据库；用户2026-11-07改为Codex实现核心SQL／状态判断，取消强制手写；保留设计讲解、带读、关键预测、测试快速说明与独立评审。

## Review Focus

- emoji / 非法代理字符：两端用同一 UTF-16 上限，合法输入可保存，越界与非法输入拒绝（任务 1、2）。
- 来源更新/关闭/软删除与提交交错：先锁来源后操作任务，版本递增与status=1条件双重阻止回写；软删后任务不可重试/读出回复（任务2、3、4）。
- 日记已保存但刷新 / 网络失败：不得报保存失败或重复生成；迟到页面回包不得复活旧状态（任务 4）。
- Python 连接超时或缓慢发送正文：总时限真实结束本地 I/O，唯一 worker 能继续下一任务（任务 3）。
- 模型没有调用 / 空正文 / SUPPORT：分别保留真实结果类型，日志和 UI 不记成正常供应商成功（任务 1、3、4）。

---

## 文件与责任

以下路径相对 heart-island 根目录。列出的是将来创建 / 修改的文件，本轮没有创建这些业务文件。

| 单元 | 文件 |
| --- | --- |
| Python 服务 | 新建 `xinyu-ai/pyproject.toml`、`uv.lock`、`.env.example`、`README.md`；`app/__init__.py`、`main.py`、`settings.py`、`contracts.py`、`provider.py`、`safety.py` |
| Python 验证 | 新建 `xinyu-ai/tests/test_contract.py`、`test_provider.py`、`test_safety.py`、`stub_provider.py`、`__init__.py` |
| 数据与状态 | 新建 `sql/migrations/20261107_01_ai_reply_job.sql`；Java 新 `entity/AiJob.java`、`mapper/AiJobMapper.java`、`service/AiJobService.java`；修改 `entity/MoodRecord.java`、`mapper/MoodRecordMapper.java`、`service/MoodRecordService.java` |
| HTTP 与执行 | 新建 Java `config/AiServiceConfig.java`、`dto/MoodAiRequest.java`、`dto/MoodAiResponse.java`、`service/AiServiceClient.java`、`service/AiJobWorker.java`；修改 `config/RestTemplateConfig.java`、`XinyuBackendApplication.java`、配置示例与 `pom.xml`（仅确有新测试依赖时） |
| 公共返回与隐私 | 新建 Java `vo/MoodSaveVO.java`、`MoodRecordVO.java`、`AiJobVO.java`、`AdminAiReplyVO.java`、`controller/AiJobController.java`；修改 `controller/MoodRecordController.java`、`AdminAiReplyController.java`、`service/AiReplyService.java`、`AiReplyLogService.java`、`CommunityPostService.java` |
| Java 验证 | 新建测试 `ai/AiJobStateTests.java`、`AiJobPersistenceIT.java`、`AiServiceClientTests.java`、`AiJobWorkerTests.java`、`AiReplyHttpTests.java`、`AiReplyContractIT.java`、`config/RestTemplateConfigTests.java`、`community/CommunityAiTimeoutTests.java`；fixture `src/test/resources/ai/schema.sql` |
| Vue | 修改 `src/api/mood.js`、`src/api/community.js`、`stores/community.js`、`views/PostList.vue`、`views/PostDetail.vue`、`stores/mood.js`、`views/Home.vue`、`views/AdminAiReplies.vue`、`components/mood/MoodCard.vue`、`MoodDetailDialog.vue`；新建 `src/utils/aiJobPolling.js`、`tests/ai-job-polling.test.js` |

Java 主路径前缀 `xinyu-backend/src/main/java/com/xinyu/`，测试前缀 `xinyu-backend/src/test/java/com/xinyu/`；Vue 路径前缀 `xinyu-vue/`。配置只补所需非秘密示例 / 环境项，不读取或输出真实 token。

## Task 1：可独立验证的 Python 单次回应服务

**Interfaces**

- `app.main.create_app(settings: Settings, provider: Provider | None = None) -> FastAPI`，便于用 httpx 预设替换供应商，不替换风险 / 认证 / 校验逻辑。
- `Provider.generate(request: MoodReplyRequest, deadline: float) -> ModelReply` 为 async；失败抛分类异常；Provider 是可注入接口，默认实现 httpx 非流式调用。
- `POST /internal/v1/mood-replies` 请求 / 响应字段与状态码按 spec 第 6 节；`GET /health` 只返回 ready，不泄露配置。
- `safety.build_support_reply(content: str) -> str | None`，命中返回内置完整支持文本，否则 None；模板版本固定 `mood-reply-v1`。

- [x] 写 `test_contract.py`：合法 token 与 500 UTF-16 输入得到符合 schema 的 MODEL；无 / 错 token 拒绝；非法 ID、501 UTF-16、孤立代理、错请求结构拒绝，provider 不被调用。用 250 / 251 个 emoji 校验跨端边界。
- [x] 写 `test_provider.py`：预设 200 / 空 choices / 空文本 / 非 JSON / 401 / 超时，断言只有合法非空文本可成功；supplier 参数为配置的max_tokens（默认4096）及thinking适配值、零自动重试、错误不含原始 body。
- [x] 写 `test_safety.py`：固定高风险／对照样例，SUPPORT 非空且长度合法、model_called=false、模型名为空、provider零调用；不配置资源文件或遗留无效路径时，仍可从环境启动并分别处理 MODEL／SUPPORT；保留正则耗时回归。旧资源门禁测试按本次契约修正替换，不保留日期或临时资源文件样板。
- [x] RED：根目录运行 `uv run --directory xinyu-ai pytest tests/test_contract.py tests/test_provider.py tests/test_safety.py -q`；应因功能缺失失败，不能把安装 / 网络错误记为 RED。
- [x] 实现上述接口与配置校验；为供应商请求及整个处理设置真实总 deadline；供应商和内部认证五项必需环境配置及可选OPENAI_MAX_TOKENS按spec，风险分支不加载外部资源或时钟；环境样例不含可用秘密。锁定依赖，README 写运行与有限规则边界。
- [x] GREEN：运行同一命令，全通过；静态 / 预设 HTTP 只证明协议和分支，不记真实模型质量。

交付物：带认证、可启动、无原文日志的内部服务，尚未接 Java。

## Task 2：版本化日记与可恢复任务状态

用户已明确选择生成中允许修改与随时删除：修改令旧任务失效，按新版本及回应开关重新申请；不采用生成中锁定编辑，避免任务卡住后的解禁机制。短事务锁不跨模型调用，不等待上游结束才删除。

**历史执行状态（2026-11-09）：Task2本地实现与验证完成。外部评审修订后全后端130项通过（含30真实隔离MySQL）；独立初审及小delta复核无阻断项，评审者另跑54项服务／DTO测试通过。本轮未改表结构或迁移，未执行云端操作；Task3网络客户端／worker调度及Task4页面接入尚未实现，不可部署。主体带读与用户掌握单独记录。**

**Interfaces**

- `AiJobService.claimNext() -> AiJob | null`：最早 queued 的原子领取，attempt_count +1 并更新 request_id。
- `AiJobService.complete(long jobId, int expectedAttempt, String requestId, MoodAiResponse result) -> boolean`：短事务锁来源、校验全部关联条件、写回复与日志及终态；false 表示失效 / 不再归本次尝试。
- `AiJobService.fail(long jobId, int expectedAttempt, String requestId, String errorCode) -> boolean`：只改变本次 running。
- `AiJobService.retry(long userId, long jobId) -> AiJobVO`、`getOwned(long userId, long jobId) -> AiJobVO`，按spec校验来源、状态与次数；retry用无锁来源快照检查和带预期attempt_count的job CAS，不显式锁来源，CAS失败后回查且不循环入队。
- `AiJobService.recoverInterrupted() -> int`：仅worker启用且旧worker已停止的启动流程调用，running→failed、queued不变；worker关闭时禁止调用。
- `MoodRecordService.add(...) / update(...)`保留原参数，返回`MoodSaveVO`；update必须改写：selectOwnedForUpdate锁来源→校验status=1→三项相同返回原状态，否则带版本条件只更新内容/心情/开关/清空回复、version+1→失效旧未完成job→按意愿和容量创建新版本job，全在一个事务内，不写status/deleted_at，不执行网络。
- `MoodRecordService.delete(Long id, Long userId) -> Boolean`保留现有接口，但改写实现：事务内锁本人来源行，再做条件软删除、写deleted_at并失效未完成job，重复删除不递增/不改时间。
- `MoodRecordMapper.selectOwnedForUpdate(Long id, Long userId) -> MoodRecord | null`：显式SELECT ... FOR UPDATE，供update/delete/complete共用；所有后续写入都在同一事务连接执行。
- `MoodRecordMapper.updateContentAtVersion(Long id, Long userId, Long expectedVersion, String moodType, String content, boolean enabled) -> int`：指定列条件UPDATE，WHERE带归属/status=1/版本，SQL内版本+1，清空旧回复；不写status或deleted_at。
- `MoodRecordMapper.softDeleteAtVersion(Long id, Long userId, Long expectedVersion) -> int`：条件UPDATE只写status、content_version、deleted_at、ai_reply_enabled、ai_reply及正常修改时间，返回影响行数；版本自增与删除时间在SQL内写入。complete同样使用指定列条件UPDATE，禁止整实体updateById。

- [x] 先写 `AiJobStateTests`，用 mapper 预设验证 claim 条件、失败 / 重试限制、关联字段错 / 旧 attempt 拒绝；写保存/关闭/500 UTF-16/容量不足仍保存并生成failed(capacity_exceeded, attempt=0)、重试重新检查容量的断言；补`softDeleteBumpsVersionAndSupersedesJobs`、`repeatDeleteDoesNotBumpVersion`、`deletedSourceCannotRetryOrBecomeUnavailable`。
- [x] RED：在 `xinyu-backend` 运行 `sh ./mvnw '-Dtest=AiJobStateTests' test`，确认因待实现行为失败。
- [x] 编写非破坏性迁移和最小隔离测试基表，增加deleted_at DATETIME NULL DEFAULT NULL及实体映射；验证有效/历史软删行都初始为NULL，历史未知时间不伪造回填。实现spec第3—5节，明确改写旧update/delete的selectById→updateById流程，update/delete/complete共用FOR UPDATE来源锁和指定列条件UPDATE；检查事务代理/连接确实生效，不仅添加注解。容量锁只包短DB事务，任务表不存正文。
- [x] 新建`AiJobPersistenceIT`，用两个真实DB连接与可控barrier验证领取/重试竞争、来源修改/关闭、准入竞争、失败回滚、旧attempt与启动恢复。软删除测试逐项列为：
  - `deleteBeforeCompleteRejectsLateResult`：先软删除再释放模型返回，断言来源行仍存在、status=0、版本原值+1、deleted_at已写入、enabled=false、ai_reply为空，原未完成job=superseded；complete返回false且ai_reply日志无新增。
  - `completeBeforeDeleteClearsReplyAndHidesSource`：先成功提交，再软删除；断言当前回复为空、来源不在列表/详情，已有日志保留至后续清理，任务接口不返回该回复。
  - `statusGuardRejectsDeletedSourceWithMatchingVersion`：直接构造status=0但版本仍相同的历史/异常行，验证complete写入0行且无日志，证明不是只靠版本号挡住回写。
  - `softDeleteRollsBackAsOneTransaction`：在任务失效处制造DB异常，断言status、版本、deleted_at、enabled、ai_reply和job状态全部回滚；重复删除另验版本与deleted_at不变、不创建job。
  - `updateCannotResurrectDeletedSource`：两个连接/barrier，先让删除事务锁行并置status=0但暂不提交，再启动update；正确实现应在来源FOR UPDATE处等待，旧无锁读取会读到已提交的status=1。用barrier/数据库锁等待证据控制后续删除提交，再让update继续并必须拒绝；断言status=0、删除版本/deleted_at/内容不被改回、无新job。交换先后，update先提交后delete仍须保持最终删除。断言事务失败时来源修改/旧job失效/新job创建整体回滚。
  - `retryRacesDeleteSafely`：无锁来源预检后暂停retry；删除先提交则failed→superseded且retry CAS=0；retry先CAS入队则随后删除将queued置superseded。关闭自动worker控制时序，核对没有新增来源或跨版本任务；再验旧预期attempt_count不能操作另一轮failed。
  - `rowLockSerializesDeleteAndComplete`：两个独立连接/事务，A先取得来源FOR UPDATE锁并用barrier保持，B进入相反操作；用数据库锁等待状态或受控非阻塞断言证明B未在A提交前写入，再释放并检查最终状态。交换A/B角色覆盖两种先后，不以单纯sleep或串行mock冒充锁竞争证明。
  - `deletedAtChangesOnlyOnFirstSoftDelete`：普通修改/AI提交保持NULL；首次软删除写时间；重复删除时间不变。另验证历史status=0且deleted_at=NULL在重复删除时仍保持未知，后续清理规则另定。
  - `deletedSourceCannotBeClaimedForGenerationOrRetried`：删除已提交且尚未通过生成前校验时不产生供应商调用；failed任务retry不入队，保留的succeeded任务get不泄露replyContent。
- [x] 在专用测试库验证迁移：`AI_TEST_JDBC_URL` 必须指向显式创建的 `xinyu_test_` 前缀空库，测试启动拒绝生产 / 普通 xinyu 库；每次用独立测试 ID，清理只针对本次创建数据。先验证历史有 / 无回复回填，再运行竞态；不执行初始化脚本。
- [x] GREEN：`sh ./mvnw '-Dtest=AiJobStateTests' test`；已准备隔离库时显式运行 `sh ./mvnw '-Dtest=AiJobPersistenceIT' test`。环境未准备必须记未验，不能以 mock 通过替代并发证明。

交付物：数据库层面可保存、去重、恢复并拒绝旧结果，尚未开启真实 worker。

## Task 3：Java→Python 契约、有界并行执行与日志

用户2026-11-09明确“可以开始”，按 [v2执行稿及复核补充](/Users/jiangqiuyu/pre/heartisland/heart-island/AI_WORKLOG/plans/2026-11-09-ai-reply-1a-task3-execution-review.md)实施，当前契约已同步。六个功能单元按3A→3C→3D+3E→3B→3F推进，独立评审按三轮合并；小diff不构成提交授权。后续社区异步三并发另列总方案，不插入本Task。

- [x] **3A补全DNS自动恢复（2026-11-09实现及验证）**：generateAsync共享查询并在剩余预算内续接；失败缓存默认10秒、到期按需重查；单请求取消隔离、关闭／过期不续发，卡住原生解析不增加物理查询。最多3逻辑请求／HTTP线程，队列上限3。Java17普通测试＋合成Python IT 180项全通过，独立review_dns_recovery固定9文件哈希审查通过，另有DNS退出恢复／HTTP取消断连复用探针。证据见progress，具体选择见执行稿11.7／11.8。成功IP TTL暂缓；新增异步段及代表性测试已带读，用户授权整理本地提交，未部署；不记为用户独立掌握。

- [x] **3A内部客户端**：认证、严格snake_case／类型／关联校验、16KiB正文与8KiB状态行／响应头限制、错误分类、无重试／重定向、连接／读取／绝对总截止、可取消与关闭；真Java→Python共享13心情矩阵。当前实现／回归和客户端预审完成，旧主体及新增DNS异步段已带读；第一轮3A＋3C整体评审已完成。未以测试通过代替掌握。
- [x] **3C单次执行**：claim→来源预检→事务外generate→条件complete。优先测completeFailureIsMarkedFailedInNewTransactionAndCanRetry与lateFailureDoesNotOverwriteSupersededOrNewAttempt；真实DB证明日志失败回滚后新事务failed及可重试。complete异常后的独立fail与fail失败处理由用户手写，Codex不提前代写；DB持续故障不伪称收尾成功，不自动重调模型。complete／fail的5秒事务配置和实际锁／连接／网络边界在本批验证。
- [x] **第一轮独立评审3A＋3C**：固定SHA／逐文件哈希快照；3A可先预审，3C用户完成后补齐同轮结论。
- [x] **3D启动停止＋3E三并发**：配置校验→唯一worker且旧进程退出前提→恢复一次→成功后扫描；恢复失败不扫描、worker关闭零恢复／领取，重复事件不注册第二份调度。停止先关领取闸门再清理I/O、等待执行退出。每秒按空槽补领，Java默认3槽、Python单应用进程门禁3；无无限内存待办。至少两用户及屏障证明3条重叠、第4条数据库queued、空槽补领、超时不影响另两槽、槽位／门禁回收和删除／修改交错不回写旧结果。跨实例租约仍1b。
- [x] **第二轮独立评审3D＋3E**：恢复、停止竞争、资源释放与门禁一致性。
- [x] **Task3外部复核领取补丁（2026-11-11用户授权落地）**：领取事务以FOR UPDATE SKIP LOCKED选择未锁定任务并保留原CAS，不改表／扫描策略。正式回归先RED复现仅1领取成功，改后定向4项及全后端298项通过（含49真实MySQL、5 JDBC故障、17合成Python HTTP）；另3项失败收尾通过；社区完成后最终同次301项通过，结果见progress。固定6文件独立评审无阻断项，评审未重跑测试。首轮3条running／第4条queued及锁定队头跳过已验；非严格FIFO，目标库兼容性／复制模式上线前核验。未提交／部署，新增SQL待快速带读。
- [x] **3B冷却（实现／验证／独立预审及主体／代表性测试带读完成）**：每3次失败180秒起翻倍、封顶3600秒，累计attempt不重置；同步SQL／Java／Python去掉永久三次限制。用户手写cooldownSeconds与requeue冷却CAS，Codex脚手架／测试／评审。数据库计算retryAfterSeconds，异常NULL显式0；冷却retry经归属检查后429＋Retry-After，普通错误体无VO，前端GET回查。验证到期／NULL／第4与7次／双击／旧身份／删除与服务重建；跨job请求限频留1b。
- [x] **3F社区与隐私主体**：同步阶段超时、失败NULL、编辑先清旧回复、新日志无prompt／失败正文、后台按用户2026-11-10修订恢复全站ADMIN的prompt／回复／错误详情查看与全文搜索；主体已带读至后台搜索；权限修订后Java299项全回归＋独立社区库9项通过，相关70项回归及独立评审通过；权限变更与代表性测试已交付快速补读，不等于独立掌握。Python上轮120项通过，本轮未改且未重跑。
- [x] **3F有限真实模型校准（2026-11-11）**：5次顺序合成社区调用全部成功，耗时1489／1284／1199／1379／1285ms，中位1285ms、最大1489ms；保留2秒连接／15秒读等待，Task4发布／编辑20秒。当前样本不代表稳定P95或总deadline，正式模型质量测评另做。
- [x] **Task4联调前风险决策**：用户2026-11-11确认社区生成标识方案，已实施并完成25项隔离MySQL回归；条件回写覆盖编辑／关闭／ABA／删除／隐藏，互动计数改为指定列更新以免恢复旧快照。具体方案与评审结果见下方，新增段待快速带读。
- [x] **第三轮独立评审3B＋3F**：固定11文件及delta复核通过；连接池等待超时误分类已RED→GREEN修复，评审未独立运行测试。
- [x] 相关模块回归完成：Java299项＋独立社区库9项，含真实MySQL／JDBC／合成Python HTTP；Python上轮120项通过且本轮未改。有限真实模型校准已完成；用户已同意本批分批本地提交，Task4前不可部署，不推送。

接口保持AiServiceClient.generate(MoodAiRequest, Duration remaining)→MoodAiResponse；worker.runOne最多领取一条，网络不包业务事务。3B已统一放宽两端为attempt≥1，累计次数不重置。配置AI_SERVICE_BASE_URL、AI_INTERNAL_TOKEN、AI_WORKER_ENABLED（默认false）、AI_WORKER_CONCURRENCY（默认3）；关闭实例不要求内部服务凭据。具体读取保护、时限与停止证据、每批带读和最终命令见v2同一文件，不另立执行口径。

## Task 4：页面轮询与跨服务闭环验收

### 社区旧生成回写保护：已确认方案（2026-11-11）

目标：社区仍同步生成，但只有当前这次保存所发起的生成能够回写，覆盖改正文／心情／话题、同正文关闭AI及A→B→A。

- 表结构：community_post增加ai_generation_id VARCHAR(36) NULL，无新增索引；每次需要AI的新建／编辑生成独立UUID，关闭AI写NULL。标识与正文、心情、话题及清除旧回复通过同一条INSERT／UPDATE保存，网络调用在其后。相比递增数字版本，生成标识无需额外读回版本或新增领取事务；作用同样是区分生成代次。
- 回写：在id／user_id／status=1之外核对本次ai_generation_id；0行代表生成已失效，丢弃回复。删除／管理员隐藏时同时清空标识，防止隐藏后恢复又接受旧请求。生成标识仅服务端使用，不接受前端赋值、不加入公开JSON。
- 影响文件：CommunityPost实体、CommunityPostService、CommunityPostMapper、CommunityReplyService、独立迁移SQL、全新建库sql/xinyu.sql、CommunityAiPersistenceIT及其合成schema。点赞／评论增删只更新对应计数，避免整实体回写旧正文／回复／生成标识。前端请求／返回接口保持不变，不新增任务表、后台执行器或付费调用。
- 历史与回退：旧行标识默认NULL，已有回复保留；发版前旧进程须退出，避免旧代码绕过条件写入。回退需停用或回退本次生成路径；不能直接退回无保护的旧写入逻辑并称仍有保护，新增可空列可暂留。
- 验证：真实隔离MySQL＋受控合成供应商，安排旧响应迟到，覆盖正文／心情／话题变更、关闭AI、ABA、删除／隐藏后恢复、当前生成正常成功及历史回复保留；检查旧回复不能覆盖新回复。完成相关回归与固定范围独立评审后带读。

该方案已按用户确认落地，包含新增可空列及业务条件回写；迁移仅在隔离测试库执行。社区全面异步化及并发3仍按后续计划设计。最终后端301项＋独立社区库25项全部通过，共326个不同Java用例；固定9文件独立评审无阻断项，未独立重跑。临时MySQL已关闭；已按用户授权分批本地提交，未推送／部署。

### 前端接入与发布验收

2026-11-11：用户明确转入Task4，当前前端主体与本地合成供应商完整链路已实现并验证；源码尚待集中带读。正式模型效果测评、生产环境／迁移／部署仍单独待办。

- [x] 隐私页面与入口已实现：用户确认运营者姜秋雨、联系邮箱jianggalilieo@gmail.com；登录／注册／全站页脚均可访问/privacy，说明管理员排障访问、AI外发、账号关联与删除保留边界。页面只交付告知，不作为整体合规认证；保留管理员访问审计暂缓、成功DNS TTL在容器化／动态地址部署前补齐的待办。

- [ ] 完整1a发布前完成正式模型效果小基线，按总方案14.1节准备固定样例／评分规则／版本和对照证据；当前工程测试与两次真实链路调用不替代该项。具体付费批次与预算另行确认，不自动增加调用；不挤占当前3C主线。

- [x] 接入RETRY-001的前端交互：重试POST在途、queued／running、冷却期均防止连续提交；使用后端retryAfterSeconds显示等待，到期只恢复手动入口，不自动生成；刷新／多页面不能绕过后端规则。

**Interfaces**

- `moodApi.aiJob(id)` / `retryAiJob(id)`分别封装两个任务接口；store消费MoodSaveVO，区分业务保存与后续列表刷新失败。社区createPost/updatePost单次请求timeout=20000ms（5次有限采样已完成，非稳定P95），不修改Axios全局5000ms。超时不自动重发，提示结果未确认并引导刷新核对；已收到保存成功但刷新失败时单独提示刷新失败。
- `createAiJobPoller({ fetchJob, onUpdate, intervalMs=2000, maxObserveMs=120000, scheduler }) -> { start({ jobId, recordId, contentVersion }), stop() }`；纯 JS 可注入时钟 / 调度器，完成后才安排下一次请求。
- `onUpdate` 仅在 ID / version / 本地代次均匹配时应用；组件负责可见性、路由 / 登录生命周期及最多 6 个任务的聚合调度。

- [x] 写 `tests/ai-job-polling.test.js`：延迟请求不重叠、stop 后迟到响应忽略、编辑后旧版本忽略、终态停、网络失败停且不调用 retry、观察 120 秒停而不改服务端状态；双击retry共用pending标记，不插新日记；删除后stop使迟到回包无法复活卡片，来源404停止轮询且不重试/不转为unavailable。
- [x] RED：在 `xinyu-vue` 运行 `node --test tests/ai-job-polling.test.js`。
- [x] 实现API/store/poller；给社区发布/编辑单独设置20秒等待并校对Axios实际请求选项，保留全局5秒；模拟超时检查“发布结果尚未确认，帖子可能已保存，请先刷新核对”，无自动重发、保留输入，正常成功/失败无回复的公开卡片展示正确。修改首页提示、卡片与详情状态及失败按钮；保留日记 CRUD / 趋势。后台保留全站ADMIN的prompt／回复／错误详情展示，按当前AdminAiReplyVO字段回归；不删除这些UI。保存响应成功后刷新失败单独提示“已保存，列表刷新失败”。
- [x] GREEN：`npm test` 与 `npm run build`；浏览器用合成输入实际检查普通完成、关闭 AI、失败 / 重试、修改 / 删除、刷新 / 切换 / 隐藏页面、管理列表，不能仅以 JS 单测代替页面验证。
- [x] 复用既有tests/stub_provider.py并新增tests/full_stack_server.py（仅测试用途）和AiReplyContractIT；启动真实 Python 监听回环端口、以隔离 MySQL 的 Java 应用发 HTTP：保存先返回，GET 查询无生成副作用，随后终态与回复一致，SUPPORT 零供应商调用，空回复失败。stub 用 barrier 控制返回，避免依赖偶然快慢。
- [x] 显式跨服务测试：AiReplyContractIT自动启动真实Spring与tests.full_stack_server（Python内部应用及真实HttpProvider，供应商HTTP仅合成响应）。先建显式本机空xinyu_test_库并配置AI_TEST_JDBC_URL／AI_TEST_DB_USER／AI_TEST_DB_PASSWORD，再在backend以Java17执行 `sh ./mvnw -o -Dtest=AiReplyContractIT test`。测试只提取完整schema中的建表语句并执行01迁移，固定所有依赖为回环地址、清空Python子进程环境，不读取.env或付费供应商。
- [x] 回环测试增加`deleteDuringGenerationDiscardsLateReply`：保存入队、等待供应商stub已接收、软删除、释放返回、回查隔离DB，断言旧结果/新日志均未写入，任务查询/重试不可用，不能靠页面隐藏冒充后端安全。
- [x] 回环验证Python不可用仍能保存，Python2秒测试预算超时后真实Runtime执行槽归零且后续任务成功；Java仍用生产50秒预算。真实Spring关闭后人为构造遗留running，再次启动恢复为execution_interrupted且不新增供应商调用。旧“资源配置无效”要求随资源门禁移除而废止。
- [ ] 真实进程kill／重启整链故障演练另做；当前应用重启案例不冒称已执行kill或供应商计费验证。
- [x] 最终回归：隔离库与测试配置下 `sh ./mvnw test`、显式相关 `*IT`、Vue test / build、Python tests；现有 contextLoads 如缺环境须先准备测试 profile，不能忽略失败后宣称全绿。两仓库 `git diff --check`，更新功能文档的已验 / 未验和启动回退说明。

交付物：本地预设供应商的实际 Java→Python→数据库→Vue 闭环。真实模型、资源可用性与生产环境仍分别待验；付费 / 上线操作继续按用户授权范围执行。

验证证据：Vue27项＋build、Python120项、Java普通及既有IT301项、独立社区库25项、新完整HTTP契约7项，共333个不同Java用例（分3次运行）。前端独立评审3项P2与集成测试2项证据缺口均修复并复审；末次微调复核见progress。浏览器使用实际Vue＋本机Java／Python／临时MySQL，检查普通完成、关闭、失败手动重试与第3次冷却、删除、社区成功／失败无卡片、全站ADMIN搜索及详情、隐私页和320／375px布局。隐藏／登出／最多6项由真实Vue生命周期＋替身GET自动测试验证，不能扩大为所有浏览器网络故障场景已手工验证。性能／效果测评仍未执行；已分批本地提交，未推送／部署。

## 计划自查与交接

- 覆盖映射：spec数据/状态/修改与软删除事务/retry CAS→任务2；内部服务/风险→任务1；执行/失败/认证与删除后API拒绝→任务3；UI/迟到回包/删除中生成回环→任务4。定期硬清理单列后续专项，不以1a测试冒充完成。
- 参数与签名：全篇复用同一状态、UTF-16 限制、DTO 关联字段、40 / 50 秒和 2 / 5 / 120 秒口径；公共 API 的响应变化明确同步修改前端。
- 源码与实测边界：任务勾选与接续记录表示当前交付状态，规划示例本身不作执行证据；各轮测试及未验证范围以对应实际日志和固定评审快照为准。
- 代码开工前请用户确认 1a 的范围与接口变更；按根 AGENTS.md 要求确认后实施。本计划不触发提交、推送或部署。


## 已授权插入项：12种心情＋其他（2026-11-07）

用户确认完整13项：正向开心／满足／期待／感动／平静；负向焦虑／难过／生气／委屈／孤独／迷茫／疲惫；其他。当前单选，后续再考虑强度和多选，不提前增加字段。已有字符串接口与VARCHAR字段无需变化，保留旧值和历史记录。

参考：DailyMood固定提交affcaefcc143a13d31937bd38de6133c73ca0fce的src/lib/moods.ts采用五档整体感受＋独立标签，不能直接套用其评分；心屿按用户选择采用具体情绪单选，分组仅帮助查找，不是临床分类或评分。

- [x] 先补Python与Java全部13项的接受测试，以及前端选项／分组和未知值显示回归，实际观察旧枚举拒绝新增项的RED。
- [x] 前端utils/mood.js统一分组、扁平选项、颜色与图标，新增共用MoodPicker.vue替换MoodForm与MoodDetailDialog重复选择区域；MoodFilterBar沿用扁平选项，MoodTrendPanel按7列两行显示13项。
- [x] Java MoodRecordService枚举与Python contracts.mood_type同步13项，普通prompt正向陪伴规则覆盖新增正向类型；不改SQL／API形状，不改Task2事务或任务语义。
- [x] GREEN：三端目标回归、Vue构建，独立临时页面检查桌面／手机新增编辑和统计，使用合成数据不登录业务环境。
- [x] 固定范围交独立agent审查，修复重要问题，更新现有契约与进度；不自动提交、推送、部署。强度和多选仅记录为后续设计。


## 展示反馈修订（2026-11-07）

用户直接审核本轮低风险展示调整，不再默认派独立agent。心情选择先用纯文字、无标题间距分组5＋8；其他并入末组并使用相同尺寸。小芽v3已有静态logo／头像／favicon可以接入，不生成缺失情绪表情，不删除旧素材。

阶段2再做并列主心情修正、排序横向条形图、仅显示出现项、前5项与更多。1a完成后单独接入xiaoya-thinking/reply，不计入Task4，不扩散当前轮询／迟到／重试任务。
