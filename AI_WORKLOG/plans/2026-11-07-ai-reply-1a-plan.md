# 心屿阶段 1a Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 先由用户确认方案，再进入业务代码；本轮仅准备计划，不选择或启动代理执行。

修订：2026-11-07校准社区等待初值与失败展示；已补充update防复活、社区HTTP超时、发布/恢复边界与retry CAS；已包含deleted_at和实际行锁；以下删除均指status=0语义。1a增加删除时间，定期硬删除/匿名化仍不在1a实施范围。

**Goal:** 单实例下实现心情先保存、AI 异步生成、可回查 / 手动重试，并防止假成功和旧结果回写。

**Architecture:** Java / MySQL 管日记、任务与最终结果，单进程 worker 按空闲并发槽位扫描数据库；Python 只做内部认证、风险分支和单次模型生成。任务领取使用 CAS，最终提交在短事务里校验来源版本与尝试标识。

**Tech Stack:** 现有 Java 17 / Spring Boot 3.5.14 / MyBatis-Plus / MySQL / Vue3；新 Python 3.12 / FastAPI / Pydantic / httpx，依赖实施时锁定。

**Spec:** [1a 功能设计](../../ops/features/ai-reply-service-v1a.md)；[总方案 v0.3](../../ops/features/xinyu-upgrade-master-plan.md)。先读两者及根目录 AGENTS.md，冲突先改设计，不在代码里暗改范围。

## Global Constraints

- 初始部署限定一个启用worker的Spring Boot进程；Task3按用户要求支持单进程有界并发，实际并发上限结合供应商额度和实测确定。扫描间隔保留1秒，只领取空闲执行槽位可容纳的任务；不做多实例租约／Redis／SSE／聊天。
- 任务状态 queued / running / succeeded / failed / cancelled / superseded；当前Task2代码最多3次尝试，Task3按RETRY-001修订为每三次失败冷却180秒后可手动继续、累计attempt不重置；Java/Python协议和SQL保护同步调整，不自动重试模型。
- content 最大 500 个 UTF-16 code unit；回复最多 2000；max_tokens由OPENAI_MAX_TOKENS配置，默认4096，本服务上限8192；thinking样例disabled，也允许enabled，预算与40秒deadline独立生效；未经真实模型校准。
- Java connect 2 秒 / read 45 秒 / 尝试总时限 50 秒；Python 总时限 40 秒，供应商 connect 2 秒 / read 35 秒且受总时限约束。
- 持久 queued 上限 100；单用户 queued/running 上限 3；单实例短事务准入锁只包容量检查与保存。
- 轮询间隔 2 秒、单次超时 5 秒、最多观察 120 秒；仅可见页与打开详情，最多 6 个，不重叠。
- 风险分支使用内置具体支持文案，不依赖求助资源文件、地区或核验日期；不推测地区或输出电话号码。按用户2026-11-07修正移除90天启动门禁，无此配置也须能启动和生成普通回应。
- 沿用Result包装与Long转字符串；add/update的data从布尔改为MoodSaveVO；Task3到Task4完成之间禁止部署，前后端同批验收发布；先迁移再启动新应用，新旧应用／worker不得同时处理业务。
- 社区共享RestTemplate初值connect=2000ms/read=15000ms；发布与同步编辑单独Axios timeout=20000ms，其他接口5000ms。实施时先以最多5次真实模型合成样例采样确认/调整初值，无自动重试；阶段等待限制不冒充整个请求总deadline。
- 社区生成失败不写公开兜底：新帖community_post.ai_reply保持NULL；编辑先清除旧内容对应回复，失败不恢复。仅成功非空结果可公开展示，失败日志reply_content=NULL。
- 仅AI_WORKER_ENABLED=true且旧worker已停止时启动恢复/扫描；关闭的实例不得调用recoverInterrupted或claimNext。
- 只增量迁移，禁止执行破坏性初始化SQL；1a保留现存历史prompt，不在普通管理接口暴露；后续专项清理按保留期删除/匿名化其关联数据。
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

**当前执行状态（2026-11-09）：Task2本地实现与验证完成。外部评审修订后全后端130项通过（含30真实隔离MySQL）；独立初审及小delta复核无阻断项，评审者另跑54项服务／DTO测试通过。本轮未改表结构或迁移，未执行云端操作；Task3网络客户端／worker调度及Task4页面接入尚未实现，不可部署。主体带读与用户掌握单独记录。**

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

**实施批次（2026-11-09用户要求小diff，当前均未开始）**

按可独立验证的功能依次交付：①内部HTTP客户端与协议／错误映射；②RETRY-001后端冷却及两端attempt契约；③worker单次执行与提交失败收尾；④启动／停止编排；⑤有界并行及Python门禁；⑥社区超时与日志隐私。每批包含相关测试与必要文档、适用的独立评审，控制变更范围，不积攒成整个Task一个大提交。获准提交时按这些目的拆分，保持worker默认关闭，不擅自重写旧历史；各批完成不等于1a已可部署。

**Interfaces**

- `AiServiceClient.generate(MoodAiRequest request, Duration remaining) -> MoodAiResponse`；由 worker 在事务外调用，执行前按来源重新构造最少输入；HTTP body 关联字段统一字符串。
- `AiJobWorker.runOne() -> boolean`：领取至多一条、调用、条件提交或分类失败，外层总 deadline 50 秒，返回是否领取；调度按空闲执行槽位领取；单次runOne处理一个任务，多个runOne可有界并行，不提前领取超过执行容量的任务。
- 配置 `AI_SERVICE_BASE_URL`、`AI_INTERNAL_TOKEN`、`AI_WORKER_ENABLED`，worker 默认关闭，启用前必须提供前两项；worker启用但服务不可用时把对应尝试明确失败，普通日记照常保存；worker关闭时不得恢复或扫描。
- `AiReplyLogService` 的心情 / 社区新写入都不复制 prompt，只保存错误分类；旧 `AiReplyService.parseReply` 对空 / 非法回复必须失败，不用默认文字冒充成功。

**单进程并发排期（用户2026-11-09要求记录，当前未实现）**

- [ ] worker支持同一Java进程同时执行多条不同任务，使用可配置并发上限和有界执行资源；只按空闲槽位领取，不建立无限内存等待队列。Task2的同job CAS、来源／尝试校验继续使用；不存在“同一张表只能同时执行一条任务”的前提。
- [ ] 同步调整Python当前active>=1的内部生成请求门禁、HTTP连接池容量与并发／总deadline清理；Java增加线程数而Python仍限1会返回overloaded，不算实现并行。并发上限应结合供应商并发／RPM／token额度和实测确定，不在本轮带读随意定值，也不宣称CPU低就可无限并发。
- [ ] 用可控回环服务证明不同job确实重叠执行且不超过配置上限，同job只能一次成功claim；一个任务超时／失败不能阻塞其他槽位，删除／修改与多任务完成交错仍拒绝旧结果。停机时先停止新领取，再关闭／取消所有在途I/O并确认执行退出。
- [ ] 多Java进程／多机器共享任务时的租约、持有者、恢复归属仍在1b另行设计；单进程多线程不等于已解决跨实例恢复，不因此擅加表结构。

**RETRY-001排期（用户2026-11-09明确）**

- [ ] Task3实现单job每三次失败冷却180秒后可继续手动重试的后端规则，保留累计attempt和request_id关联保护，同步Java／Python字段范围、领取／重排SQL及服务端可重试时间。冷却期间不调用模型，到期不自动生成，容量未准入且attempt=0不消耗本轮失败次数。
- [ ] 保留排队／生成中的重复retry幂等，并落实冷却最终由后端判定；评估单实例按用户请求节流的最小实现，不把并发名额当时间限流，不因本项擅加表／索引或引入Redis。跨实例共享限流仍按1b范围设计。
- [ ] 验证第三次失败、180秒边界、到期第四次、并发重试只入队一次、旧结果不得覆盖新attempt，以及刷新／服务重启后的冷却一致性；与Task4的服务端时间展示和按钮状态同批验收。细节按既有契约第10节核对，不边带读边改当前Task2代码。

**外部代码复核后的优先验收（2026-11-09）**

先把下面两个边界写成测试并实现，再将正常调用接入调度；它们属于Task3完成条件，当前均未实现。

- **提交失败收尾**：`completeFailureIsMarkedFailedInNewTransactionAndCanRetry`模拟日志插入失败，确认来源回复／成功终态回滚后，原事务已结束；worker在新的事务内用原jobId/attempt/request_id调用fail(internal_error)，最终failed且可重试。`lateFailureDoesNotOverwriteSupersededOrNewAttempt`确认删除或新尝试之后的失败收尾不能覆盖当前状态。
- **恢复调用时机**：启动顺序为配置校验／确认单worker进程部署前提 → 仅启用worker执行一次恢复 → 恢复成功后开始按空闲槽位扫描。增加`startupRecoversOnceBeforeFirstScan`、`recoveryFailureDoesNotStartScanning`及`workerDisabledDoesNotRecoverOrClaim`；关闭调度后不再claim，取消／关闭在途I/O并确认旧执行线程退出，再允许部署流程启动新worker。重复生命周期事件不得注册第二份扫描或重复恢复。

唯一启用worker且旧进程已停止是单实例发布／进程管理前提，Task3要把生命周期顺序和部署核验落实；不能仅在注释里假设，也不能冒充已具备跨主机防双开能力。当前不新增租约／持有者／心跳字段；多实例需另行评审1b方案。

- [ ] worker在complete抛异常且原提交事务结束后，使用新的短事务调用fail(internal_error)，带原attempt/request_id；补“日志插入失败→整体回滚→新事务failed→可重试”和失效／新attempt不被失败收尾覆盖的回归。fail也因持续DB故障失败时分类记录，不伪称已收尾、不自动重调模型。
- [ ] 按共享test-contracts/mood-types.json的13值运行真实Java→Python回环HTTP矩阵；单端测试不替代跨服务证据。
- [ ] 配置改动前完成社区耗时校准：沿用实际prompt与max_tokens=200，最多5次真实供应商顺序合成输入，记录模型/参数/日期、各次耗时、中位数、最大值和失败数至AI_WORKLOG；据结果确认或调整connect=2秒/read=15秒及前端20秒初值，保持前端等待余量。未获得实际数据则明确记录待校准，不伪造“正常生成均小于阈值”的证据，不将小样本当稳定P95。
- [ ] 保留`RestTemplateConfigTests`和`CommunityAiTimeoutTests`的短时限注入方案：真实回环上游接收后不返回正文，验证读超时分类；断言生产默认connect=2000ms/read=15000ms或采样后明确记录的校准值。实际社区add保留帖子、ai_reply为NULL、AI日志失败且无兜底reply_content；编辑生成失败也不保留旧内容对应回复。禁止真实公网“黑洞地址”测试。
- [ ] 写 `AiServiceClientTests` / `AiJobWorkerTests`：真实回环 stub HTTP 验证认证头、snake_case DTO、空正文 / 错版本 / 错 attempt / 错 request_id、超时、连接失败；未知调用状态不填 false，SUPPORT 不插供应商成功记录。
- [ ] RED：`sh ./mvnw '-Dtest=AiServiceClientTests,AiJobWorkerTests,RestTemplateConfigTests,CommunityAiTimeoutTests' test`，确认失败与预期行为有关。
- [ ] 给现有RestTemplateConfig显式设置社区共享客户端，初值connect=2000ms/read=15000ms并按采样记录校准；改写CommunityPostService.attachAiReply的成功分支，失败不写公开兜底，编辑先清除旧回复。保留同步流程，不把两个阶段超时称为总deadline。
- [ ] 实现客户端与总 deadline、可关闭资源、按空闲槽位有界调度；仅worker启用时注册启动恢复/扫描，增加`workerDisabledDoesNotRecoverOrClaim`，断言关闭实例的启动不会改动已有running任务；明确关闭调度后再清理在途本地任务。总 deadline 不能仅 cancel Future 而遗留长时间网络阻塞；测试用较短注入时限，生产值固定在全局约束。
- [ ] 写 `AiReplyHttpTests`：用现有 JWT / AuthInterceptor 的真实 MVC 路径 + mock service，验证本人状态/重试、未登录/跨用户拒绝、add/update返回新VO、已软删来源get/retry拒绝且无replyContent或unavailable、管理列表无prompt/replyContent/raw error；控制器不直接返回 Entity。同步删掉后台 keyword 对隐藏全文的搜索。
- [ ] 回归社区：200但无正文记失败且不展示兜底，正常成功正文仍展示；公开列表/详情无回复时不挂载AI卡片；捕获结构化日志，断言没有 token、原始日记、prompt 和上游敏感 body；社区新日志同样不复制 prompt。SUPPORT 的模型名必须空，普通 MODEL 非空。
- [ ] GREEN：`sh ./mvnw '-Dtest=AiServiceClientTests,AiJobWorkerTests,RestTemplateConfigTests,CommunityAiTimeoutTests,AiReplyHttpTests,AuthHttpTests,CorsConfigTests,JacksonConfigTests' test`。

交付物：预设HTTP下任务、权限与隐私可验证。此时前端尚未按MoodSaveVO完成适配，不视为兼容交付，禁止部署；必须待Task4联调验收后同批发布。

## Task 4：页面轮询与跨服务闭环验收

- [ ] 接入RETRY-001的前端交互：重试POST在途、queued／running、冷却期均防止连续提交；使用后端可重试时间显示等待，到期只恢复手动入口，不自动生成；刷新／多页面不能绕过后端规则。

**Interfaces**

- `moodApi.aiJob(id)` / `retryAiJob(id)`分别封装两个任务接口；store消费MoodSaveVO，区分业务保存与后续列表刷新失败。社区createPost/updatePost单次请求timeout=20000ms（待采样校准），不修改Axios全局5000ms。超时不自动重发，提示结果未确认并引导刷新核对；已收到保存成功但刷新失败时单独提示刷新失败。
- `createAiJobPoller({ fetchJob, onUpdate, intervalMs=2000, maxObserveMs=120000, scheduler }) -> { start({ jobId, recordId, contentVersion }), stop() }`；纯 JS 可注入时钟 / 调度器，完成后才安排下一次请求。
- `onUpdate` 仅在 ID / version / 本地代次均匹配时应用；组件负责可见性、路由 / 登录生命周期及最多 6 个任务的聚合调度。

- [ ] 写 `tests/ai-job-polling.test.js`：延迟请求不重叠、stop 后迟到响应忽略、编辑后旧版本忽略、终态停、网络失败停且不调用 retry、观察 120 秒停而不改服务端状态；双击retry共用pending标记，不插新日记；删除后stop使迟到回包无法复活卡片，来源404停止轮询且不重试/不转为unavailable。
- [ ] RED：在 `xinyu-vue` 运行 `node --test tests/ai-job-polling.test.js`。
- [ ] 实现API/store/poller；给社区发布/编辑单独设置20秒等待并校对Axios实际请求选项，保留全局5秒；模拟超时检查“发布结果尚未确认，帖子可能已保存，请先刷新核对”，无自动重发、保留输入，正常成功/失败无回复的公开卡片展示正确。修改首页提示、卡片与详情状态及失败按钮；保留日记 CRUD / 趋势。后台按新 VO 展示，删除 prompt / 原始错误 UI。保存响应成功后刷新失败单独提示“已保存，列表刷新失败”。
- [ ] GREEN：`npm test` 与 `npm run build`；浏览器用合成输入实际检查普通完成、关闭 AI、失败 / 重试、修改 / 删除、刷新 / 切换 / 隐藏页面、管理列表，不能仅以 JS 单测代替页面验证。
- [ ] 加 `tests/stub_provider.py`（仅测试用途）和 `AiReplyContractIT`；启动真实 Python 监听回环端口、以隔离 MySQL 的 Java 应用发 HTTP：保存先返回，GET 查询无生成副作用，随后终态与回复一致，SUPPORT 零供应商调用，空回复失败。stub 用 barrier 控制返回，避免依赖偶然快慢。
- [ ] 显式运行跨服务测试：终端 A `uv run --directory xinyu-ai python -m tests.stub_provider`；终端 B 为应用配置测试资源和内部 token 后 `uv run --directory xinyu-ai uvicorn app.main:app --host 127.0.0.1 --port 18081`；终端 C 在 backend 运行 `sh ./mvnw '-Dtest=AiReplyContractIT' test`。测试 profile 的供应商 URL 固定本机，拒绝意外访问云端；使用仅本机测试的虚构 token / 资源；测试入口用 env 传配置，不把真实秘密写进命令历史。
- [ ] 回环测试增加`deleteDuringGenerationDiscardsLateReply`：保存入队、等待供应商stub已接收、软删除、释放返回、回查隔离DB，断言旧结果/新日志均未写入，任务查询/重试不可用，不能靠页面隐藏冒充后端安全。
- [ ] 回环测试额外验证 Python 不可用 / 资源配置无效仍能保存，慢速上游到时释放 worker 后下一任务可执行；重启时旧 running 失败可见且不自动再付费。记录默认 40 / 50 秒配置与缩短测试时限的区别。
- [ ] 最终回归：隔离库与测试配置下 `sh ./mvnw test`、显式两项 `*IT`、Vue test / build、Python tests；现有 contextLoads 如缺环境须先准备测试 profile，不能忽略失败后宣称全绿。两仓库 `git diff --check`，更新功能文档的已验 / 未验和启动回退说明。

交付物：本地预设供应商的实际 Java→Python→数据库→Vue 闭环。真实模型、资源可用性与生产环境仍分别待验；付费 / 上线操作继续按用户授权范围执行。

## 计划自查与交接

- 覆盖映射：spec数据/状态/修改与软删除事务/retry CAS→任务2；内部服务/风险→任务1；执行/失败/认证与删除后API拒绝→任务3；UI/迟到回包/删除中生成回环→任务4。定期硬清理单列后续专项，不以1a测试冒充完成。
- 参数与签名：全篇复用同一状态、UTF-16 限制、DTO 关联字段、40 / 50 秒和 2 / 5 / 120 秒口径；公共 API 的响应变化明确同步修改前端。
- 源码与实测边界：文件路径按当前包结构拟定；所有 RED / GREEN 和 IT 命令都是将来执行，不表示本轮已通过。
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
