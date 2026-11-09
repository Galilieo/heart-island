# 阶段 1a：单实例异步心情回应设计

2026-11-09 · 契约 v1.0设计修订（用户批准Task3 v2开工；3A实施中，后续worker／冷却尚未完成） · 对应[总方案 v0.3](xinyu-upgrade-master-plan.md)阶段 1a，已获用户授权实施。本文确定接口与验收；[实施计划](../../AI_WORKLOG/plans/2026-11-07-ai-reply-1a-plan.md)拆执行任务，实际进度见[接续状态](xinyu-next-session.md)。

## 1. 目标与范围

用户写完日记即收到保存结果，AI 在后台生成。页面能区分保存、排队、生成、失败及回应完成，刷新后可回查；失败可手动重试。

保留 Java / MySQL 的业务权威，新增独立 Python 服务。初始部署限定一个启用worker的Spring Boot进程；用户2026-11-09选定Task3同进程执行并发初值为3，Java执行槽位与Python内部生成请求门禁同步设为3，保留可配置并结合供应商额度与实测校准。定时扫描只补空闲执行槽位，数据库中queued是待办队列，不把全部待办塞进内存；3槽占满时第4条仍留queued，空闲后再领取。当前Java worker尚未实现，Python仍为单进程最多1个内部生成请求。

1a 包含内部认证、总超时、最小内置风险回应分支、日志隐私、条件提交。Redis 限流 / 租约 / 独立 attempt 表在 1b 按场景加入；聊天、SSE、周报、RAG 不在本阶段，周报仍是紧接着的阶段 2。

## 2. 当前代码与兼容面

设计前的历史问题基线：日记曾在保存／修改请求中同步调用AI，删除仅置status=0。Task2当前已改为日记与任务同事务保存，以及带版本、删除时间、旧任务失效的软删除；不重做已完成链路。尚待Task3修复的源码问题是社区旧解析器可能将默认文字当成功、RestTemplate未显式设超时、后台AI列表直接返回含prompt实体。前端现有统一等待5秒，Task4再适配新状态；未以此核验线上数据。

保留 `/mood/add`、`/mood/update/{id}` 路径与 `Result` 包装。本方案把两者 `data: true` 改为保存结果 VO；这是已声明的响应变化，Vue API / store / 页面必须同批修改。已有列表、详情的日记字段保持，增加状态字段；`Long` 沿用当前 Jackson 转字符串的约定。社区回应保留同步入口，同时修复共享解析器假成功与RestTemplate超时。待实测校准的初值为connect=2000ms、read=15000ms，无自动重试；前端仅communityApi.createPost/updatePost的Axios timeout=20000ms，其他请求保持5000ms。编辑也会同步调用attachAiReply，因此使用相同等待策略。

实施时先以当前社区prompt、max_tokens=200、实际供应商/模型做最多5次顺序合成样例采样，记录日期、模型/参数、每次耗时、失败类型、中位数与最大值，再确认/调整后端及前端等待值；少量样例不用于宣称稳定P95或通用SLA。采样记录放AI_WORKLOG，未采样不得把初值标为已验证。连接/读等待不是整条发布请求的总deadline；前端超时只说明未收到结果，提示“发布结果尚未确认，帖子可能已保存，请先刷新核对”，不自动重发、不声称保存失败，也不声称必定已保存。完整社区异步化不在1a。

社区失败展示决定：新帖生成失败时community_post.ai_reply保持NULL，attachAiReply不得把getDefaultReply等兜底文字写入；编辑先清除旧内容对应的回复，失败不恢复旧回复。仅成功且非空的生成结果才写入公开回复，失败日志的status=失败且reply_content=NULL。公开列表/详情沿用非空才显示AI卡片的规则；旧数据不在本次批量清洗。回归真实延迟HTTP，保留已插入帖子并记分类失败，不能只mock抛异常。Task3后端返回契约变化与Task4前端必须同批验收发布，Task3完成时不可单独部署。

后续社区异步化（2026-11-09用户要求记录）：社区AI回复后续也采用先保存、后台异步生成，社区执行并发初值3、可配置。该方向已记入总方案第12节，具体执行池／Python门禁及全局额度设计待核验；不改变本1a社区同步范围，不插入Task3／Task4，尚未实现。

## 3. 增量数据设计

迁移文件：`sql/migrations/20261107_01_ai_reply_job.sql`。仅增量，不运行 `sql/xinyu.sql` 初始化脚本，不在本轮执行迁移。

模型名长度修正单独使用 `sql/migrations/20261107_02_ai_reply_model_name.sql`：将既有 `ai_reply.model_name` 从 VARCHAR(100) 放宽为 VARCHAR(128)，对齐内部契约及任务元数据。该脚本可独立执行，不依赖任务表迁移；新库初始化定义同步为128。用户已同意本地修正与隔离验证，云端执行须另行明确授权，执行前核对实际表定义与备份；不自动缩列回退。

`mood_record` 新增：

| 字段 | 类型 / 初值 | 规则 |
| --- | --- | --- |
| content_version | BIGINT NOT NULL DEFAULT 1 | 内容、心情、回应开关有实际变化或首次软删除才 +1；三项均相同的保存、重复删除和只写 AI 结果不递增 |
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

产品取舍（用户确认）：生成中允许修改与删除；修改使旧任务失效，并在需要 AI 回应时按新版本创建任务；删除随时可用。此选择服务于日记操作体验，不是异步技术的必然要求；不采用生成中锁定编辑，以避免任务卡住时需要额外的解禁机制。这里的短数据库事务行锁只串行化写入，不在整个生成期间锁定编辑，也不等待上游结束才删除。

```text
保存事务：日记 + queued job → 提交 → 返回
worker：读取 queued → CAS queued→running / attempt+1 → 校验当前来源status=1 / 版本 / enabled → 调 Python
提交事务：锁定来源并校验status=1 / 版本 / enabled → 校验 job 为本次 running → 写回复与兼容日志 → succeeded
```

无变化保存：update锁住本人有效日记后，逐项精确比较moodType、content和enabled；三项均相同则返回当前状态及原jobId，不写来源、不递增版本、不清回复、不失效任务、不创建任务，也不修改update_time。failed仍需独立retry；unavailable不因无变化保存自动补生成。任一项实际变化仍按原更新事务处理。

修改来源的update/delete及提交结果的complete统一先锁日记，再操作job；只修改job的retry见下文CAS例外。update、delete、complete必须在事务生效的连接上先执行SELECT ... FOR UPDATE（按id/user_id锁来源），重新检查status/版本后，再执行只更新指定列的条件UPDATE；锁持有至事务提交或回滚。删除UPDATE的WHERE包含id、user_id、status=1和锁定行的content_version，SET中content_version=content_version+1并写deleted_at；complete使用同样归属/status/版本约束并检查enabled，且不改deleted_at。update只改mood_type/content/ai_reply_enabled/ai_reply及正常修改时间，SQL中content_version+1，WHERE带id/user_id/status=1/预期版本；不写status或deleted_at。随后在同一事务内失效旧未完成job，按开关和容量创建新版本job，失败整体回滚。不能沿用旧update()/delete()无锁selectById后updateById整实体回写；Java进程锁不能替代数据库行锁。claim只执行job的短CAS，不持锁等模型。外部网络调用始终在事务外；每次领取一条，Task3扫描按空闲并发槽位补充，扫描间隔初值1秒；总执行并发受配置上限限制，不建立无限内存任务队列。

提交还须匹配user_id、target_id、target_version、attempt_count、request_id；最终日记UPDATE的WHERE同时带status=1与content_version=预期版本，enabled=true也必须成立。来源软删除、缺失或版本不匹配时丢弃结果，只把仍属本次执行的未完成job标为superseded；不得写ai_reply日志或succeeded。不要假设软删除会移除job。若删除发生在生成前校验之后，外部调用仍可能执行；删除保证结果不可提交，不承诺供应商停止计算/计费。回复、兼容日志和succeeded在同一事务中；提交失败不显示完成。先提交后删除时，删除清空当前回复并撤销可见性；先删除后提交时，status与版本两道条件均阻止回写。

失败只把本次 running 条件更新为 failed。手动重试仅接受同用户、来源status=1、当前版本、enabled=true的failed，且须通过同样的容量准入；CAS条件含job/user/target_version、status=failed、预期attempt_count及attempt_count<3，再failed→queued，清理最后结果 / 错误与运行时间，不改来源版本。所有幂等返回前先校验来源status=1与当前版本；符合时queued/running返回同job，succeeded返回已有结果，superseded/cancelled拒绝。来源已软删除或缺失时统一按不可用来源拒绝，不返回旧回复或重新入队。上述attempt_count<3与最多3次是当前Task2代码事实，Task3的3B按第10节替换为逐轮冷却、到期可继续；实现前不宣称已放宽。重复调度只允许一次 claim；迟到 attempt 不得提交到新 attempt。

retry不显式锁来源，先普通快照读取检查来源，再仅对job做上述原子CAS；这是修改来源事务锁序规则的明确例外，不能持有job锁后再获取来源锁。删除先提交时，原failed已变superseded，retry CAS影响0行；retry先提交时，job变queued，后续删除仍会把queued/running置superseded。两种先后都不能恢复来源，执行前与最终提交仍检查status/版本。CAS带预期attempt_count防止旧retry误操作已完成另一尝试的failed；CAS失败后回查最新状态，不盲目循环重置任务。

更新/关闭令旧未完成job为superseded；软删除按第3节置状态、版本递增并失效未完成任务，不删除job行。1a 不提供用户取消接口；cancelled 为后续扩展预留，不伪造已实现的取消功能。

Task3必做失败收尾：worker必须在complete事务已回滚／结束后捕获其异常，在新的短事务里调用带原jobId/attempt/request_id的fail(internal_error)，不得在rollback-only原事务中写失败。补“提交日志失败→回复／终态回滚→新事务标failed→可手动重试”的worker＋真实DB回归，以及来源已删除／尝试已变化时失败收尾不得覆盖新状态的用例。若数据库持续不可用导致fail也失败，要记录分类故障并保留恢复依据，不伪称已转failed、不自动重调模型。当前只有Task2方法，不能将Task3收尾写成已实现。

仅AI_WORKER_ENABLED=true的worker启动流程执行恢复；worker关闭时不调用recoverInterrupted、不扫描/领取任务。单实例启动时还须确认旧进程已停止，再将遗留running标为failed，错误 execution_interrupted；queued 保留并恢复扫描。无自动供应商重试，无“重启必定没付费”承诺。多实例 / 滚动发布前必须完成 1b 租约与持有者方案，禁止两个 worker 同时运行此恢复逻辑。

## 5. 公共 API

身份从现有 JWT 获取；不接收可信 userId。日记归属检查沿用现有业务提示，新增任务接口区分 401 / 403 / 404；使用现有 Result 包装，不能越过 AuthInterceptor。

| 方法 / 路径 | 请求 | data / 行为 |
| --- | --- | --- |
| POST /mood/add | 原 moodType / content / needAiReply | MoodSaveVO：id、contentVersion、aiReplyEnabled、aiReplyState、jobId |
| PUT /mood/update/{id} | 原修改 DTO | 同上；每次成功修改版本 +1；重新生成或关闭 |
| GET /mood/list、GET /mood/detail/{id} | 保持原查询参数 | 日记 VO 原字段 + contentVersion、aiReplyEnabled、aiReplyState、jobId、resultKind；状态查询不得调用模型 |
| GET /mood/ai-job/{id} | 无 | AiJobVO：id、targetId、targetVersion、status、attemptCount、resultKind、replyContent、errorCode、retryable；3B新增retryAfterSeconds（当前尚无） |
| POST /mood/ai-job/{id}/retry | 无 | 返回当前 AiJobVO；仅来源status=1且当前版本的failed可入队，其余按第4节处理 |

先校验来源归属与status=1，再计算aiReplyState；来源status=0或不存在时拒绝读取任务/重试，不返回replyContent，不映射为unavailable，不创建新任务。aiReplyState包含任务状态，以及disabled / legacy / unavailable；没有 job 且历史非空回复为 legacy；没有 job 且 enabled=false 为 disabled，enabled=true 且无回复 / 无任务为 unavailable（历史或异常状态缺少任务，提示重新加载，不自动生成）。请求 content 必须非空，最大 500 个 UTF-16 code unit，与现有浏览器 maxlength 和 Java String.length 一致；心情类型单选，三端统一为13项：正向开心、满足、期待、感动、平静；负向焦虑、难过、生气、委屈、孤独、迷茫、疲惫；其他。前端用无标题间距分为5项与8项两组，其他并入末组；按钮暂用纯文字且等宽，不用重复图标或正向／负向标签，不生成情绪评分；接口继续传一个moodType字符串，历史六项兼容。情绪强度和多选留待后续单独设计，不在1a增加字段。客户端不能提交 version / jobId 决定服务端写入。

管理端继续现有分页，返回 `AdminAiReplyVO`：id、userId、targetType、targetId、modelName、status、errorCode、createTime；不返回 prompt、replyContent 或上游 errorMessage。keyword 搜索仅查这些允许字段，不允许用隐藏全文做猜测式检索。当前无详情 API，不新增一个暴露原文的接口；既有数据库 prompt 保留，不经普通页面透出。

## 6. Python 内部契约

新目录 `xinyu-ai/`，Python 3.12，FastAPI / Pydantic / httpx，单次非流式 httpx.AsyncClient 调用；不引用 agent-lab 的 SQLite、工具或记忆配置。依赖在实施时按可安装版本锁定，不提前声称已安装。

`POST /internal/v1/mood-replies`，`Authorization: Bearer <internal token>`，仅私网 / 回环可达。token 从 AI_INTERNAL_TOKEN 读，缺失则服务启动失败，比较用 constant-time；禁止日志打印。Java 用户 JWT 不传供应商。

请求字段：`request_id`、`job_id`、`attempt`、`source_version`、`mood_type`、`content`、`budget_ms`。ID / 版本按字符串跨边界，attempt 为整数，budget_ms 固定不超过 40000。Python只回传关联字段，Java不信任回包宣称的用户身份。

200 响应：关联字段 + `reply_content`、`result_kind`（MODEL / SUPPORT）、`model_called`、`template_version`、`model_name`。正文去空白后非空、至多 2000 个 UTF-16 单位；MODEL 必须 model_called=true 且模型名非空；SUPPORT 必须 model_called=false 且模型名空。Java再校验一遍。

3A客户端实施状态（2026-11-09）：采用Boot管理的Apache HttpClient 5.5.2，不改Boot或Java版本。成功与错误响应正文最多16KiB，状态行及响应头累计最多8KiB，在解析期间拒绝超界；禁止重定向、自动重试及自动内容解压。严格检查重复键、尾随JSON、必需／未知字段和实际JSON类型，再校验关联与语义。worker关闭时不创建客户端、不要求内部地址／凭据。新增generateAsync返回CompletableFuture，DNS等待仅登记续接；同一客户端逻辑请求及实际HTTP执行线程均受concurrency限制（默认3），交接队列上限同为concurrency，超容量安全overloaded。同步generate保留为明确阻塞的兼容入口，3C worker须使用异步入口；同步回调保持轻量，数据库收尾必须使用显式执行器。关闭取消未完成请求、DNS调度及HTTP资源，worker生命周期本身仍3D待实施。

DNS恢复实施状态（2026-11-09）：固定服务主机首次请求按需解析，A／B／C共享一项查询并登记各自续接；成功后当前仍有效的请求使用剩余预算发送。失败后当前等待者分别provider_error，缓存失败默认10秒（AI_DNS_FAILURE_CACHE_SECONDS，配置范围1～3600秒）；缓存期内直接失败，到期新请求触发重查。物理查询单线程、最多一项；解析等待上限沿用connect预算（默认2秒），逻辑超时后若原生调用仍卡住就保留进行中标记，不因缓存过期不断创建线程；迟到地址丢弃，实际查询退出后才可发起新查询。DNS等待计入原总deadline，单请求取消／超时不取消共享查询；过期或关闭后的请求不再发送HTTP。成功地址缓存到客户端关闭，本轮不含动态IP／成功TTL刷新，不宣称强制终止不可中断的原生DNS。已完成180项Java普通／合成Python联调验证；独立评审状态和证据见执行稿11.7／11.8及本地progress。

错误响应：`request_id`、`code`（invalid_request / unauthorized / provider_timeout / provider_error / invalid_model_response / overloaded），无原始正文。格式 / 关联字段不匹配均按 invalid_model_response 失败，不存默认安慰话。请求头 / body 大小上限、非法 UTF-16 代理输入需拒绝；Python用同样 UTF-16 计数标准，不能把 emoji 按一个字符放宽。

时间预算（Task3 v2已确认）：领取成功后的来源预检、构造请求、HTTP与解析／语义校验共同受生成阶段50秒单调时钟绝对截止约束；Java→Python connect 2秒、read 45秒且受剩余时间限制。Python整个处理最多40秒（含风险判断、等待及供应商调用），budget_ms=min(40000,剩余时间减本地余量)，供应商connect 2秒／read 35秒也受剩余预算约束。complete与独立失败收尾各配置5秒事务超时，与生成阶段分开；实际JDBC连接、statement、锁、网络及commit／rollback的有界效果须3C实测，不把配置值称为严格墙上时间保证。claim与来源预检也不得无限占槽。真实取消网络／关闭资源，执行线程实际退出后才还槽；不靠Future返回假装I/O结束。无自动重试，不保证供应商停止计算或计费。当前3A先验证HTTP；worker、数据库超时和失败收尾尚未实施。

供应商配置使用 OPENAI_BASE_URL、OPENAI_API_KEY、OPENAI_MODEL、DEEPSEEK_THINKING_MODE；健康检查不返回其值。可选 OPENAI_MAX_TOKENS 控制输出预算，默认4096，本服务允许1～8192的整数（不是供应商上限）；thinking样例仍为disabled，同时允许enabled。思考模式也消耗输出预算，调大不保证在40秒总预算或64 KiB上游正文限制内完成；截断仍判失败，不自动重试，不回传思考内容。该初值未经真实模型校准。thinking作为供应商适配参数，供应商不支持时必须显式调整适配配置，不能默默丢掉参数。prompt 在 Python 模板集中管理，日记作为数据隔离；不复制到业务日志。

## 7. 最小风险回应

普通生成前执行有限规则分支；命中后跳过普通模型，返回 SUPPORT、具体的关心表达、联系可信任的人和寻求陪伴的提示；如有立即危险，提示联系当地急救或警方。不推测用户地区，不输出具体电话号码。它是有限规则能力，阶段 1b 再细化误判和分类，不声称覆盖所有危机。

2026-11-09用户确认后续方向：1a继续保留正则与固定文案；1b改为命中后触发专门危机prompt，模型失败／超时／无效结果时再固定回退，内容安全API／模型分类器仅为候选增强。实施及验收列入[总方案第7.2节](xinyu-upgrade-master-plan.md#72-阶段1bredis运行互斥与风险细化)，不是本1a新增要求。模型尝试后的回退涉及当前SUPPORT零调用约束，须在1b同步评审元数据与日志，不能直接标model_called=false；当前代码与本节1a验收不变。

按用户 2026-11-07 的简化要求，移除求助资源文件、地区／核验日期和 90 天有效期门禁。Python 服务不再读取 XY_HELP_RESOURCES_FILE；该变量缺失或保留无效路径不得影响服务启动和普通回应。内部认证及模型必需配置校验保留，接口字段和数据库设计不变。

验收断言：无资源配置也能从环境启动并正常生成 MODEL；命中规则时返回完整且合法的内置 SUPPORT，model_called=false、model_name=null、普通模型零调用。保留高风险／对照样例和正则耗时边界验证；口语正例覆盖我想死、我想去死、不想活了、活不下去了和句首／分句边界的想自杀，反例包含明确否定、第三人称转述及累死了／笑死我了／想死你了；不将有限样例通过等同于总体安全覆盖。未来地区联系方式按实际产品需求单独设计，不自动加入 LLM 定位或 IP 查询。

## 8. 页面、资源控制与验收

保存 toast 只说“心情已保存”；AI 显示排队 / 生成中 / 已完成 / 失败可重试，SUPPORT 不显示“模型已生成”。轮询每 2 秒、每次请求超时沿用 5 秒，上一请求结束再调下一次，不重叠。页面可见任务最多取当前页 + 打开详情，最多 6 个；终态、切换记录、隐藏页面、卸载、退出登录停止，恢复可见时回查。

列表 / 详情响应与轮询须同时匹配记录 ID、contentVersion 及本地请求代次，防止导航或编辑后迟到回包覆盖当前状态。轮询网络失败显示状态暂不可用，停止自动轮询并提供“刷新状态”；不能重发生成。最长自动观察 120 秒，之后停止并允许回查，不把任务改成失败。

当前Task2只提供任务方法，尚无worker调度；Task3计划实现单进程有界执行并发并同步Python门禁，用户选定两端并发初值均为3，尚待实现与实测。现有持久queued上限100、单用户queued/running上限3保留；全局执行3槽与单用户在途3名额是独立规则。单任务当前最多3次尝试，Task3按第10节处理冷却后可继续的修订。单实例准入使用进程内一把互斥锁包住容量查询与保存事务，提交后释放；它只保护短 DB 操作，绝不包住模型调用。容量不足仍保存日记，创建 status=failed、attempt_count=0、last_error_code=capacity_exceeded 的 job，提示“心情已保存，AI 暂忙，可稍后重试”；这是容量保护，不宣称付费总额或跨重启日额度。上线前按实际预算再补额度，1b 承接共享限流。

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


## 9. 发布顺序与旧应用兼容

完整1a验收并获准发布后，必须先执行增量迁移，再启动依赖新列的新应用，不能把带DROP的初始化脚本当作升级。推荐停写窗口：停止旧应用／worker并确认退出 → 备份及验证 → 核对并执行所需增量迁移 → 启动配套前后端，新worker先关闭 → 基础接口／权限／页面回查验证 → 确认仅一个worker后启用扫描与恢复 → 完整验收后开放写入。

若迁移后短暂仍由旧应用写入，新日记的enabled可为默认0且带旧同步回复，当前无job＋非空回复会显示legacy；这只是迁移兼容，不是允许新旧应用长期混跑。新旧版本应用不得同时处理这套业务，新旧worker不得同时运行。当前01尚未上线，02模型名独立扩列已单独完成；不能因02已执行就认为所有迁移已完成。

Task2提交用于评审，不代表获准部署；Task3／4尚未完成，仍需配套验收。Python与Java的13种心情回归共用test-contracts/mood-types.json输入基准；它是测试数据，不是运行时配置。Task3必须覆盖同一组13值的真实Java→Python回环HTTP矩阵，避免只靠单端测试宣称跨服务已验证。


## 10. RETRY-001：逐轮冷却后继续手动重试（3B待实施）

状态：用户2026-11-09批准Task3 v2执行；当前Task2及3A仍按attempt≤3，3B才同步放宽Java SQL／校验和Python契约。不将设计确认写成已实现。

每累计3次执行失败进入一轮冷却：第3次180秒、第6次360秒、第9次720秒、第12次1440秒、第15次2880秒，第18／21／24……次均3600秒。其他失败次数无新增冷却。复用原job，attempt_count累计不清零，不设业务总次数上限；沿用当前INT／Integer的物理类型，不增加溢出专项或字段迁移。系统中断保留已领取attempt，也可能触发冷却；容量拒绝且attempt=0不消耗次数。到期只允许手动retry，不自动queued或调用模型；成功任务不提供重新生成入口。

Java纯函数根据预期attempt_count计算冷却时长，数据库用同一预期次数和CURRENT_TIMESTAMP执行完整requeue CAS。finished_at为NULL时按用户v2放行并记录分类，不写猜测时间；GET的retryAfterSeconds在此情况下必须显式返回0，不能让GREATEST传播NULL。时间计算保持数据库整秒口径；有亚秒精度时正剩余向上取整，资格判断不得提前归零。

AiJobVO的3B目标字段只增加retryAfterSeconds，不增加retryAvailableAt或serverTime。非failed／无需冷却／异常NULL时间／已到期均为0；retryable表示有效来源、failed且剩余0，不保证容量在请求时仍可用。冷却中retry先校验归属与来源，再返回HTTP429及正整数Retry-After，普通Result错误体不携带任务VO；前端收到后GET回查。现有ResponseStatusException处理最小透传响应头，不新增专用错误结构。容量不足仍503，来源软删404，失效版本等沿原409规则。

前端Task4：POST在途、queued／running禁用重试，冷却按服务端剩余秒数展示；结束后只恢复手动入口，不自动生成。刷新不能绕过后端CAS。跨job用户请求限频留1b，不把单job冷却、三执行槽位或每用户三个在途名额冒充时间限流。

验收：纯函数覆盖0／1／2／3／4／6／9／12／15／18／30，真实DB用调整finished_at验证到期边界、NULL、并发retry只入队一次、第4／7次正常领取并提交、旧attempt不能覆盖新attempt、冷却中删除来源返回404、服务重建后资格一致；MVC验证429与Retry-After。两处用户手写：cooldownSeconds和冷却CAS；Codex提供脚手架、RED测试及评审，不提前代写。

参考仍为BullMQ固定提交b18b6ddd3d4e6fe2d8a14d09fb733aea159a5de4的同job重新入队与独立尝试计数思路，不照搬其Redis后端，也不以此证明冷却数值或产品效果。具体调用链及取舍见既有执行记录。
