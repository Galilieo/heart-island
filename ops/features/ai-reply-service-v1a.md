# 阶段 1a：单实例异步心情回应设计

2026-11-07 · 已评审契约 v0.6（简化风险回应，移除资源启动门禁） · 对应[总方案 v0.3](xinyu-upgrade-master-plan.md)阶段 1a，已获用户授权实施。本文确定接口与验收；[实施计划](../../AI_WORKLOG/plans/2026-11-07-ai-reply-1a-plan.md)拆执行任务，实际进度见[接续状态](xinyu-next-session.md)。

## 1. 目标与范围

用户写完日记即收到保存结果，AI 在后台生成。页面能区分保存、排队、生成、失败及回应完成，刷新后可回查；失败可手动重试。

保留 Java / MySQL 的业务权威，新增独立 Python 服务。初始部署限定一个启用 worker 的 Spring Boot 进程；定时扫描任务表，单线程执行，数据库中 queued 是待办队列，不把全部待办塞进内存。

1a 包含内部认证、总超时、最小风险分支与可配置资源、日志隐私、条件提交。Redis 限流 / 租约 / 独立 attempt 表在 1b 按场景加入；聊天、SSE、周报、RAG 不在本阶段，周报仍是紧接着的阶段 2。

## 2. 当前代码与兼容面

当前 `MoodRecordService` 保存 / 修改时同步调用 `AiReplyService`；空响应可能返回默认文字并记成功。`RestTemplateConfig` 未显式设超时，Vue Axios 超时为 5 秒。后台 AI 列表直接返回含 prompt 的实体。`delete()`目前只置status=0，是软删除；列表和详情已过滤删除状态。上述来自源码检查，未检查线上数据。

保留 `/mood/add`、`/mood/update/{id}` 路径与 `Result` 包装。本方案把两者 `data: true` 改为保存结果 VO；这是已声明的响应变化，Vue API / store / 页面必须同批修改。已有列表、详情的日记字段保持，增加状态字段；`Long` 沿用当前 Jackson 转字符串的约定。社区回应保留同步入口，同时修复共享解析器假成功与RestTemplate超时。待实测校准的初值为connect=2000ms、read=15000ms，无自动重试；前端仅communityApi.createPost/updatePost的Axios timeout=20000ms，其他请求保持5000ms。编辑也会同步调用attachAiReply，因此使用相同等待策略。

实施时先以当前社区prompt、max_tokens=200、实际供应商/模型做最多5次顺序合成样例采样，记录日期、模型/参数、每次耗时、失败类型、中位数与最大值，再确认/调整后端及前端等待值；少量样例不用于宣称稳定P95或通用SLA。采样记录放AI_WORKLOG，未采样不得把初值标为已验证。连接/读等待不是整条发布请求的总deadline；前端超时只说明未收到结果，提示“发布结果尚未确认，帖子可能已保存，请先刷新核对”，不自动重发、不声称保存失败，也不声称必定已保存。完整社区异步化不在1a。

社区失败展示决定：新帖生成失败时community_post.ai_reply保持NULL，attachAiReply不得把getDefaultReply等兜底文字写入；编辑先清除旧内容对应的回复，失败不恢复旧回复。仅成功且非空的生成结果才写入公开回复，失败日志的status=失败且reply_content=NULL。公开列表/详情沿用非空才显示AI卡片的规则；旧数据不在本次批量清洗。回归真实延迟HTTP，保留已插入帖子并记分类失败，不能只mock抛异常。Task3后端返回契约变化与Task4前端必须同批验收发布，Task3完成时不可单独部署。

## 3. 增量数据设计

迁移文件：`sql/migrations/20261107_01_ai_reply_job.sql`。仅增量，不运行 `sql/xinyu.sql` 初始化脚本，不在本轮执行迁移。

`mood_record` 新增：

| 字段 | 类型 / 初值 | 规则 |
| --- | --- | --- |
| content_version | BIGINT NOT NULL DEFAULT 1 | 每次成功修改内容、心情、回应开关或首次软删除都 +1；重复删除和只写 AI 结果不递增 |
| ai_reply_enabled | TINYINT NOT NULL DEFAULT 0 | 持久化本版本的回应意愿；请求仍叫 needAiReply，沿用既有缺省 true |
| deleted_at | DATETIME NULL DEFAULT NULL | 首次status=1→0的删除UPDATE写入数据库当前时间；普通更新/AI提交不写此列，重复删除不改时间 |

历史记录不自动创建任务；迁移只把status=1且已有非空ai_reply的enabled回填为1，其余为0，历史回应标记 legacy，不追认其供应商成功。历史版本从1起；deleted_at全部初始为NULL，其中旧status=0记录表示删除时间未知，不使用update_time或迁移时间回填。有效记录直到首次软删除才写入；沿用项目DATETIME约定，部署时核对数据库会话与产品时区Asia/Shanghai。修改时清除旧 ai_reply，旧任务失效；关闭回应后无新任务。

新增 `ai_job`：

| 字段 | 类型 / 说明 |
| --- | --- |
| id / user_id / target_id | BIGINT；自增主键 / 所属用户 / 来源日记 |
| task_type | VARCHAR(32)，1a 只有 MOOD_REPLY，为阶段 2 留枚举扩展点 |
| target_version | BIGINT，来源版本 |
| status | VARCHAR(24)：queued / running / succeeded / failed / cancelled / superseded |
| attempt_count | INT NOT NULL DEFAULT 0，领取时 +1，计执行尝试，不计供应商实际调用 |
| request_id | VARCHAR(64)，每次领取更新为新的追踪 ID |
| template_version / model_name | VARCHAR(64) / VARCHAR(128)，模型名允许空 |
| result_kind / model_called | VARCHAR(24) 可空（MODEL / SUPPORT）/ TINYINT 可空（未知 / 未调用 / 已发起调用） |
| last_error_code | VARCHAR(64)，只放错误分类，不存上游正文或日记原文 |
| create_time / update_time / started_at / finished_at | DATETIME；运行 / 结束前相应字段可空 |

唯一键 `(task_type, target_id, target_version)`，扫描索引 `(status, id)`，归属查询含 user_id。不在 job 复制日记正文、提示词或模型完整响应，结果仍在 mood_record.ai_reply。普通模型调用按现有 ai_reply 兼容记录落库，prompt 为空，errorMessage 只保存分类；SUPPORT 不插入一条“模型调用成功”记录，job 保留结果类型和 model_called=false。model_called=true 仅表示已发起供应商 HTTP 调用，不证明供应商实际执行或计费；失败无法确认时保留 NULL。

日记沿用软删除。首次删除在同一事务中将来源status置0、content_version加1、deleted_at写数据库当前时间、ai_reply_enabled置0、ai_reply显式置NULL，并将该来源queued/running/failed任务置superseded；只清理私密心情域，不改变社区对象。重复删除沿用现有失败/不存在结果，不再增加版本或重写deleted_at。来源和job行仍在，不能以“job已删除”作为拒绝回写依据。

软删除后普通日记查询、任务查询/重试和新任务申请都检查来源status=1；旧ai_reply日志不作为当前回复读取。1a不实现定期硬清理：后续Java清理任务按配置保留期硬删/不可逆匿名化来源及关联ai_job、ai_reply（含历史prompt与回复）；关联条件包含target_type=MOOD_RECORD、target_id、user_id。1a已增加deleted_at作为未来保留期起点；期限与deleted_at未知的历史行处理另行细化，生命周期见总方案第4.2节；不将待实现清理描述为已完成。

## 4. 状态与事务

```text
保存事务：日记 + queued job → 提交 → 返回
worker：读取 queued → CAS queued→running / attempt+1 → 校验当前来源status=1 / 版本 / enabled → 调 Python
提交事务：锁定来源并校验status=1 / 版本 / enabled → 校验 job 为本次 running → 写回复与兼容日志 → succeeded
```

修改来源的update/delete及提交结果的complete统一先锁日记，再操作job；只修改job的retry见下文CAS例外。update、delete、complete必须在事务生效的连接上先执行SELECT ... FOR UPDATE（按id/user_id锁来源），重新检查status/版本后，再执行只更新指定列的条件UPDATE；锁持有至事务提交或回滚。删除UPDATE的WHERE包含id、user_id、status=1和锁定行的content_version，SET中content_version=content_version+1并写deleted_at；complete使用同样归属/status/版本约束并检查enabled，且不改deleted_at。update只改mood_type/content/ai_reply_enabled/ai_reply及正常修改时间，SQL中content_version+1，WHERE带id/user_id/status=1/预期版本；不写status或deleted_at。随后在同一事务内失效旧未完成job，按开关和容量创建新版本job，失败整体回滚。不能沿用旧update()/delete()无锁selectById后updateById整实体回写；Java进程锁不能替代数据库行锁。claim只执行job的短CAS，不持锁等模型。外部网络调用始终在事务外；扫描一条、领取一条，配置扫描间隔 1 秒、执行并发 1，不存在无限内存任务队列。

提交还须匹配user_id、target_id、target_version、attempt_count、request_id；最终日记UPDATE的WHERE同时带status=1与content_version=预期版本，enabled=true也必须成立。来源软删除、缺失或版本不匹配时丢弃结果，只把仍属本次执行的未完成job标为superseded；不得写ai_reply日志或succeeded。不要假设软删除会移除job。若删除发生在生成前校验之后，外部调用仍可能执行；删除保证结果不可提交，不承诺供应商停止计算/计费。回复、兼容日志和succeeded在同一事务中；提交失败不显示完成。先提交后删除时，删除清空当前回复并撤销可见性；先删除后提交时，status与版本两道条件均阻止回写。

失败只把本次 running 条件更新为 failed。手动重试仅接受同用户、来源status=1、当前版本、enabled=true的failed，且须通过同样的容量准入；CAS条件含job/user/target_version、status=failed、预期attempt_count及attempt_count<3，再failed→queued，清理最后结果 / 错误与运行时间，不改来源版本。所有幂等返回前先校验来源status=1与当前版本；符合时queued/running返回同job，succeeded返回已有结果，superseded/cancelled拒绝。来源已软删除或缺失时统一按不可用来源拒绝，不返回旧回复或重新入队。最多 3 次尝试，达到上限的 failed 返回 retryable=false，retry 拒绝。重复调度只允许一次 claim；迟到 attempt 不得提交到新 attempt。

retry不显式锁来源，先普通快照读取检查来源，再仅对job做上述原子CAS；这是修改来源事务锁序规则的明确例外，不能持有job锁后再获取来源锁。删除先提交时，原failed已变superseded，retry CAS影响0行；retry先提交时，job变queued，后续删除仍会把queued/running置superseded。两种先后都不能恢复来源，执行前与最终提交仍检查status/版本。CAS带预期attempt_count防止旧retry误操作已完成另一尝试的failed；CAS失败后回查最新状态，不盲目循环重置任务。

更新/关闭令旧未完成job为superseded；软删除按第3节置状态、版本递增并失效未完成任务，不删除job行。1a 不提供用户取消接口；cancelled 为后续扩展预留，不伪造已实现的取消功能。

仅XY_AI_WORKER_ENABLED=true的worker启动流程执行恢复；worker关闭时不调用recoverInterrupted、不扫描/领取任务。单实例启动时还须确认旧进程已停止，再将遗留running标为failed，错误 execution_interrupted；queued 保留并恢复扫描。无自动供应商重试，无“重启必定没付费”承诺。多实例 / 滚动发布前必须完成 1b 租约与持有者方案，禁止两个 worker 同时运行此恢复逻辑。

## 5. 公共 API

身份从现有 JWT 获取；不接收可信 userId。日记归属检查沿用现有业务提示，新增任务接口区分 401 / 403 / 404；使用现有 Result 包装，不能越过 AuthInterceptor。

| 方法 / 路径 | 请求 | data / 行为 |
| --- | --- | --- |
| POST /mood/add | 原 moodType / content / needAiReply | MoodSaveVO：id、contentVersion、aiReplyEnabled、aiReplyState、jobId |
| PUT /mood/update/{id} | 原修改 DTO | 同上；每次成功修改版本 +1；重新生成或关闭 |
| GET /mood/list、GET /mood/detail/{id} | 保持原查询参数 | 日记 VO 原字段 + contentVersion、aiReplyEnabled、aiReplyState、jobId、resultKind；状态查询不得调用模型 |
| GET /mood/ai-job/{id} | 无 | AiJobVO：id、targetId、targetVersion、status、attemptCount、resultKind、replyContent、errorCode、retryable |
| POST /mood/ai-job/{id}/retry | 无 | 返回当前 AiJobVO；仅来源status=1且当前版本的failed可入队，其余按第4节处理 |

先校验来源归属与status=1，再计算aiReplyState；来源status=0或不存在时拒绝读取任务/重试，不返回replyContent，不映射为unavailable，不创建新任务。aiReplyState包含任务状态，以及disabled / legacy / unavailable；没有 job 且历史非空回复为 legacy；没有 job 且 enabled=false 为 disabled，enabled=true 且无回复 / 无任务为 unavailable（历史或异常状态缺少任务，提示重新加载，不自动生成）。请求 content 必须非空，最大 500 个 UTF-16 code unit，与现有浏览器 maxlength 和 Java String.length 一致；心情类型只允许现有 UI 的合法枚举。客户端不能提交 version / jobId 决定服务端写入。

管理端继续现有分页，返回 `AdminAiReplyVO`：id、userId、targetType、targetId、modelName、status、errorCode、createTime；不返回 prompt、replyContent 或上游 errorMessage。keyword 搜索仅查这些允许字段，不允许用隐藏全文做猜测式检索。当前无详情 API，不新增一个暴露原文的接口；既有数据库 prompt 保留，不经普通页面透出。

## 6. Python 内部契约

新目录 `xinyu-ai/`，Python 3.12，FastAPI / Pydantic / httpx，单次非流式 httpx.AsyncClient 调用；不引用 agent-lab 的 SQLite、工具或记忆配置。依赖在实施时按可安装版本锁定，不提前声称已安装。

`POST /internal/v1/mood-replies`，`Authorization: Bearer <internal token>`，仅私网 / 回环可达。token 从 XY_AI_INTERNAL_TOKEN 读，缺失则服务启动失败，比较用 constant-time；禁止日志打印。Java 用户 JWT 不传供应商。

请求字段：`request_id`、`job_id`、`attempt`、`source_version`、`mood_type`、`content`、`budget_ms`。ID / 版本按字符串跨边界，attempt 为整数，budget_ms 固定不超过 40000。Python只回传关联字段，Java不信任回包宣称的用户身份。

200 响应：关联字段 + `reply_content`、`result_kind`（MODEL / SUPPORT）、`model_called`、`template_version`、`model_name`。正文去空白后非空、至多 2000 个 UTF-16 单位；MODEL 必须 model_called=true 且模型名非空；SUPPORT 必须 model_called=false 且模型名空。Java再校验一遍。

错误响应：`request_id`、`code`（invalid_request / unauthorized / provider_timeout / provider_error / invalid_model_response / overloaded），无原始正文。格式 / 关联字段不匹配均按 invalid_model_response 失败，不存默认安慰话。请求头 / body 大小上限、非法 UTF-16 代理输入需拒绝；Python用同样 UTF-16 计数标准，不能把 emoji 按一个字符放宽。

时间预算：Java→Python connect 2 秒、read 45 秒，整个任务尝试最多 50 秒；Python整个处理最多 40 秒（含风险判断、等待及供应商调用），供应商 connect 2 秒、read 35 秒且受剩余总时间约束。使用真实可取消的网络 deadline / 资源关闭，不能只让 Future 超时而继续占着唯一 worker。无自动重试；超时只保证本地结束等待，不保证供应商停止计算 / 计费。

供应商配置使用 XY_MODEL_BASE_URL、XY_MODEL_API_KEY、XY_MODEL_NAME、XY_MODEL_THINKING_MODE；健康检查不返回其值。模型配置沿用当前心情场景意图：max_tokens=300；thinking=disabled 作为供应商适配参数，供应商不支持时必须显式调整适配配置，不能默默丢掉参数。prompt 在 Python 模板集中管理，日记作为数据隔离；不复制到业务日志。

## 7. 最小风险回应

普通生成前执行有限规则分支；命中后跳过普通模型，返回 SUPPORT、具体的关心表达、联系可信任的人和寻求陪伴的提示；如有立即危险，提示联系当地急救或警方。不推测用户地区，不输出具体电话号码。它是有限规则能力，阶段 1b 再细化误判和分类，不声称覆盖所有危机。

按用户 2026-11-07 的简化要求，移除求助资源文件、地区／核验日期和 90 天有效期门禁。Python 服务不再读取 XY_HELP_RESOURCES_FILE；该变量缺失或保留无效路径不得影响服务启动和普通回应。内部认证及模型必需配置校验保留，接口字段和数据库设计不变。

验收断言：无资源配置也能从环境启动并正常生成 MODEL；命中规则时返回完整且合法的内置 SUPPORT，model_called=false、model_name=null、普通模型零调用。保留高风险／对照样例和正则耗时边界验证；不将有限样例通过等同于总体安全覆盖。未来地区联系方式按实际产品需求单独设计，不自动加入 LLM 定位或 IP 查询。

## 8. 页面、资源控制与验收

保存 toast 只说“心情已保存”；AI 显示排队 / 生成中 / 已完成 / 失败可重试，SUPPORT 不显示“模型已生成”。轮询每 2 秒、每次请求超时沿用 5 秒，上一请求结束再调下一次，不重叠。页面可见任务最多取当前页 + 打开详情，最多 6 个；终态、切换记录、隐藏页面、卸载、退出登录停止，恢复可见时回查。

列表 / 详情响应与轮询须同时匹配记录 ID、contentVersion 及本地请求代次，防止导航或编辑后迟到回包覆盖当前状态。轮询网络失败显示状态暂不可用，停止自动轮询并提供“刷新状态”；不能重发生成。最长自动观察 120 秒，之后停止并允许回查，不把任务改成失败。

本地运行只设执行并发 1、持久 queued 上限 100、单用户 queued/running 上限 3、单任务最多 3 次尝试。单实例准入使用进程内一把互斥锁包住容量查询与保存事务，提交后释放；它只保护短 DB 操作，绝不包住模型调用。容量不足仍保存日记，创建 status=failed、attempt_count=0、last_error_code=capacity_exceeded 的 job，提示“心情已保存，AI 暂忙，可稍后重试”；这是容量保护，不宣称付费总额或跨重启日额度。上线前按实际预算再补额度，1b 承接共享限流。

| 验收组 | 必须证明 |
| --- | --- |
| 保存与选择 | 无 AI 零任务；服务不可用仍保存；重复列表刷新无模型调用；500 / 501 UTF-16 边界 |
| 状态与恢复 | claim 竞争只有一个成功；失败重试一次入队；启动恢复；迟到 attempt 隔离 |
| 来源竞态 | update/delete/complete先FOR UPDATE再指定列条件更新，update不能复活来源；retry只靠job CAS与删除覆盖各未完成状态，测两种先后；deleted_at首次写入且重复删除不变；锁保持至提交；软删除status=0、版本+1、未完成job失效；删除与提交两种先后、重复删除、历史status=0但版本相同均不能回写；先删后返回不新增日志；先提交后删除的既有日志待清理；回复与终态原子提交 |
| 协议与失败 | 内部无 token / 错 token、40 / 50 秒总界限、空正文、错误 JSON、关联字段错、供应商错误均无伪成功 |
| 风险与隐私 | 无资源配置可启动并生成普通回应；SUPPORT 内置文案 / 零模型调用；管理端及结构化日志不含秘密与原文 |
| 前端 | 保存提示准确；刷新恢复；轮询销毁 / 失败 / 观察上限；旧请求回包不覆盖编辑；重试双击 |
| 回归 | 登录、心情CRUD、近7天趋势、后台分页；社区共享客户端2秒连接/15秒读等待初值按采样校准，发布/编辑单独20秒等待；慢响应超时保留帖子、AI失败无兜底公开回复、日志记失败 |

先跑预设供应商的真实 Java→Python 回环 HTTP 与隔离 MySQL 的迁移 / 竞态测试，再经用户授权做有限真实模型体验。单元模拟、回环契约、实际供应商质量、生产部署分别记录。

回退：先停接新 AI 任务 / 停 worker，保留已保存日记及新增字段 / 表；前后端按同版本回退，旧同步代码会重新启用旧问题，不作为无风险方案。迁移不含自动 DROP 回滚。所有代码与数据库操作待用户确认后实施，本轮文档检查不算产品验收。
