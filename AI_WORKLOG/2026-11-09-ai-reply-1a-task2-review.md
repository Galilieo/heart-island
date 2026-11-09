# 心屿 1a · Task 2 最终实现设计与评审稿

日期：2026-11-09。状态：**Task 2 本地实现、验证及独立审查完成；完整阶段1a尚未完成，尚不能发布。**

本文是从当前源码整理的单份评审快照，用于评审已实现设计，不新增业务要求，也不代替原功能契约。数据库变更仍须用户逐项明确同意；本次整理不修改代码、SQL或数据库。

## 1. 评审对象与证据边界

- 项目：heart-island／心屿，Spring Boot 3.5.14、Java 17、MyBatis-Plus、MySQL。
- 提交父基线：`98707a2396bec47d5d6bfdffc0e8502aa49e726e`。本稿与实现纳入同一组本地提交用于审查；本地commit与远端push分别授权，不因本文在Git中就假定远端已同步。
- 原始独立审查冻结22个源码／测试／SQL文件；外部评审修订后，附录更新为26文件当前SHA，包含共享心情基准及Python契约／接口测试。原始与修订后审查范围分别记录，不把旧快照审查扩大为新变更也已经复核。
- 主agent最新全后端回归130项通过，其中30项真实隔离MySQL；独立reviewer审查22文件并另跑54项服务／DTO测试，通过且无可操作发现。独立评审不等于用户已同意提交、部署，也不等于用户已独立掌握实现。
- 当前没有Java→Python HTTP客户端、自动worker调度、自动启动恢复、前端轮询或真实模型闭环；这些属于Task3／4。
- 本轮Task2收口未修改表结构／索引／约束／迁移脚本，已有迁移仅在本机隔离测试库运行。历史上已单独获准完成云端model_name长度100→128，不能据此认为Task2任务表已上线。

## 2. 场景、产品选择与不变量

用户保存日记立即获得保存结果，AI回应作为后台任务处理。任务在数据库持久化，可刷新回查，失败后可由用户重试。生成中允许修改和删除：修改令旧未完成任务失效，按新版本及开关申请任务；删除随时可用。没有“生成中锁住编辑直到模型结束”的产品限制。

必须保持：

1. 日记与本版本任务同事务保存；队列满不能把已经接受的日记当成未保存。
2. 一个任务类型、一个业务对象、一个依据版本只有一个job；失败重试复用它。
3. 旧来源版本、旧attempt、旧request_id不得写当前回复或成功日志。
4. 删除先提交，旧结果不能写回；回应先提交，之后删除仍撤销当前回复可见性。
5. 任务状态成功、日记回复、兼容MODEL日志一起提交或一起回滚。
6. 任何模型网络等待都不能放在数据库事务或来源行锁内；本Task尚无网络调用。
7. 公开任务查询只允许本人，且返回前检查来源有效性；读取不创建或重新生成任务。

## 3. 数据对象职责、命名与扩展边界

| 对象 | 本次职责 | 不承担的职责 |
| --- | --- | --- |
| mood_record | 日记正文、当前可见回复、生成依据版本、回应意愿、软删除状态 | 不保存完整尝试历史 |
| ai_job | 排队／执行状态、尝试身份、业务关联、最后结果元数据和错误分类 | 不复制日记正文、prompt或完整上游响应；不保存回复正文 |
| ai_reply | 既有单次回应兼容调用记录；本提交路径只写有效MODEL成功记录 | 不是当前回复权威，不用旧日志恢复被删除／修改的回复，不充当未来全部聊天消息／工具记录 |

保留通用`ai_job`／`AiJob`命名。当前只有`MOOD_REPLY`适配逻辑；领取、恢复和失效SQL都限定此类型，不能因为表名通用就声称已支持其他任务。

- `task_type`必须稳定对应一种业务目标，例如当前`MOOD_REPLY → mood_record`；同一类型不能混用不同表的ID。
- `target_id`是该类型关联的业务对象ID；当前迁移注释写“来源日记ID”，描述的是当前适配。没有增加通用业务对象外键，关联有效性由服务检查。
- `target_version`当前等于`mood_record.content_version`。未来周报涉及多条日记，应由报告业务模型管理来源集合及其版本，不能直接拿一条日记版本代表整份报告。
- 当前唯一键不支持同一对象版本成功后再创建第二个任务。成功后主动重新生成、多候选、按新模板重新生成属于后续需要明确规则的需求，不能假定已支持，也不预先把template_version加进唯一键。
- 单实例是当前前提；没有租约、持有者、心跳或独立attempt表，当前只保留最后一次尝试身份及结果元数据。

## 4. 最终数据库设计与迁移状态

### 4.1 mood_record新增字段

| 字段 | 类型／初值 | 语义 |
| --- | --- | --- |
| content_version | BIGINT NOT NULL DEFAULT 1 | 三项生成依据实际变化／首次软删除加1；输入完全相同不递增；只写AI回复和重试不递增 |
| ai_reply_enabled | TINYINT NOT NULL DEFAULT 0 | 本版本是否需要AI回应；不同于请求DTO的needAiReply |
| deleted_at | DATETIME NULL | 首次软删除时写数据库当前时间；普通更新／AI提交不写，重复删除不重写；历史未知保持NULL |

日记仍以status=1有效、status=0软删除。ai_reply列仍保存当前回复。

### 4.2 ai_job字段与索引

下方是仓库中**现有迁移原文**，不是本评审新提出或已在线执行的DDL：

```sql
-- 心情异步回应增量迁移：在正确的目标库中人工执行一次，不由应用自动运行。
-- MySQL DDL 会隐式提交，执行前备份并安排停写窗口；不含破坏性初始化或自动 DROP 回退。
ALTER TABLE mood_record
    ADD COLUMN content_version BIGINT NOT NULL DEFAULT 1 COMMENT '生成依据版本',
    ADD COLUMN ai_reply_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '本版本是否需要AI回应',
    ADD COLUMN deleted_at DATETIME NULL DEFAULT NULL COMMENT '首次软删除时间，历史未知保留NULL';

-- 不为历史日记创建任务，不推算历史删除时间，只标记当前已有回复的有效日记。
UPDATE mood_record
SET ai_reply_enabled = 1, update_time = update_time
WHERE status = 1 AND ai_reply IS NOT NULL AND CHAR_LENGTH(TRIM(ai_reply)) > 0;

CREATE TABLE ai_job (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    user_id BIGINT NOT NULL COMMENT '所属用户ID',
    target_id BIGINT NOT NULL COMMENT '来源日记ID',
    task_type VARCHAR(32) NOT NULL COMMENT '任务类型，当前MOOD_REPLY',
    target_version BIGINT NOT NULL COMMENT '任务对应来源版本',
    status VARCHAR(24) NOT NULL COMMENT 'queued/running/succeeded/failed/cancelled/superseded',
    attempt_count INT NOT NULL DEFAULT 0 COMMENT '执行尝试次数',
    request_id VARCHAR(64) DEFAULT NULL COMMENT '本次尝试追踪标识',
    template_version VARCHAR(64) DEFAULT NULL COMMENT '模板版本',
    model_name VARCHAR(128) DEFAULT NULL COMMENT '模型名称',
    result_kind VARCHAR(24) DEFAULT NULL COMMENT 'MODEL或SUPPORT',
    model_called TINYINT DEFAULT NULL COMMENT '未知NULL/未调用0/已发起1',
    last_error_code VARCHAR(64) DEFAULT NULL COMMENT '错误分类，不存原文',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at DATETIME DEFAULT NULL,
    finished_at DATETIME DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_job_target_version (task_type, target_id, target_version),
    KEY idx_ai_job_status_id (status, id),
    KEY idx_ai_job_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI异步任务元数据';
```

关键含义：

- `attempt_count`初始0，仅领取时递增，范围由本次服务／SQL保证最多3；不是供应商调用次数或计费次数。数据库没有新增CHECK约束。
- `request_id`每次领取生成新的UUID，不提供在公开VO中。它与attempt共同标识一次执行，失败重试会清空，下一次领取重新生成。
- `result_kind`只有合法成功提交才写MODEL／SUPPORT；`model_called`三态为未知NULL、明确未调用false、明确已发起true。失败和恢复不能擅自填写false。
- 结束未完成任务为superseded时，本路径清理结果元数据；批量来源失效SQL仅改状态和时间，可保留旧错误分类，但公开视图不会把它当当前失败展示。
- 唯一键服务同一输入版本去重；(status,id)服务按最早ID扫描；(user_id,status)服务用户容量查询。
- `cancelled`只是已有迁移／契约中的预留值，没有手动取消接口，也没有任何当前业务路径把任务写成cancelled。

### 4.3 历史兼容与独立长度修正

- 不为历史日记补任务或补生成请求。
- 原status=1且已有SQL TRIM后非空回复的记录回填enabled=1；其余0。显式`update_time=update_time`，避免回填改写历史修改时间。
- 历史版本统一从1开始；历史已删除行的deleted_at=NULL表示时间未知，不伪造为迁移时间。
- 历史无任务但有非空回复展示legacy，不追认供应商成功。
- ai_reply.model_name从100放宽为128有独立迁移02，不依赖01；新库初始化定义同步128。02已曾获准在线执行，01仍未在线执行。禁止用带DROP的sql/xinyu.sql升级已有库。

```sql
-- 仅放宽 AI 调用记录的模型名长度，与内部回应契约的 128 字符上限一致。
-- 本迁移独立于 20261107_01：云端仅修复长度时无需执行其他任务表变更。
-- 执行前核对目标库与实际字段定义并备份；预期原长度为 100，已为 128 时跳过。
-- MySQL DDL 会隐式提交；不自动缩回 100，以免截断扩列后写入的长模型名。
ALTER TABLE ai_reply
    MODIFY COLUMN model_name VARCHAR(128) NULL DEFAULT NULL COMMENT '模型名称';
```

已有私密日志的保留期清理／匿名化尚未实现。软删除撤销业务可见性并清空当前回复，不代表历史日志或物理磁盘数据已被擦除。

## 5. 状态机与容量规则

```text
保存需要回应且有容量 → queued(attempt=0)
保存需要回应但容量满 → failed(capacity_exceeded, attempt=0)
queued --领取CAS／attempt+1／新request_id--> running
running --有效MODEL或SUPPORT提交--> succeeded
running --分类失败／无效回包／启动恢复--> failed
failed --本人重试，来源有效、attempt<3且容量允许--> queued
queued / running / failed --修改、关闭或删除来源--> superseded
running --生成前或提交时发现来源已失效--> superseded
```

succeeded在来源修改／删除后保留历史状态；通过来源当前版本及status校验阻止旧任务返回旧回复。superseded不会自动重排。cancelled不在当前可达状态图中。

| 限制 | 当前规则 |
| --- | --- |
| 全局待排队 | queued最多100；running不计入这100 |
| 单用户在途 | queued + running最多3 |
| 单任务尝试 | 最多3次领取；无自动供应商重试 |
| 执行部署前提 | 只允许一个启用worker的Java进程；单线程扫描执行由Task3接入 |

容量满时，add/update仍原子保存来源并创建failed任务，last_error_code=capacity_exceeded、attempt=0、finished_at已写；无需调用模型。关掉回应则不创建新任务。

容量判断跨所有任务类型计数；当前只有MOOD_REPLY使用此入队路径。未来新增类型时需要评审是否共用配额策略，不能默认当前策略适合所有任务。

## 6. 保存、修改与删除的真实事务

### 6.1 add

Controller使用JWT身份构造内部实体 → validateInput → admission.save → 插入status=1/version=1的日记 → 根据意愿及容量插入任务或无任务 → 事务提交 → 返回MoodSaveVO。

输入只接受当前13种心情；content为非空白且不超过500 UTF-16代码单元，拒绝孤立代理字符。Unicode空白判定涵盖Character.isWhitespace／isSpaceChar及0x85。needAiReply缺省true，显式null在当前服务也按true；只有明确false关闭回应。请求不能指定可信userId、版本或jobId。

### 6.2 update

validateInput → admission.save → SELECT日记FOR UPDATE(id,user_id) → 检查status=1 → 精确比较moodType/content/enabled；三项相同返回原状态，否则指定列条件UPDATE、版本在SQL内+1、清回复 → 旧queued/running/failed任务superseded → 按新版本及开关创建任务 → 一起提交。

不使用旧实体updateById整行覆盖；不写status或deleted_at，避免“修改把删除状态改回有效”。三项相同时保留原回复、版本、jobId、任务状态和update_time；failed仍经独立retry，unavailable不自动补任务。不存在／不属于本人／已删除返回原有业务失败。日记变更、旧任务失效、新任务插入任一出错，全部回滚。

### 6.3 delete

admission.transaction → SELECT本人日记FOR UPDATE → 检查status=1 → 条件软删除(status=0/version+1/enabled=0/ai_reply=NULL/deleted_at=DB当前时间) → 旧未完成任务superseded → 一起提交。

重复删除返回false，不递增版本／时间，不创建任务。删除不增加容量，因此不占进程准入锁。任务失效报错时来源软删除也回滚。

## 7. 领取、生成前检查与最终提交

### 7.1 claimNext

AI_WORKER_ENABLED=false立即返回null，连数据库扫描都不执行。启用时短事务选择最早MOOD_REPLY queued且attempt<3的行；用id、类型、queued状态、预期attempt_count做CAS。CAS成功后当前读该job并返回；竞争失败返回null，等待未来下一次扫描，不忙循环抢占。领取事务结束后才进入后续处理。

```sql
UPDATE ai_job SET status='running', attempt_count=attempt_count+1, request_id=#{requestId},
  started_at=CURRENT_TIMESTAMP, finished_at=NULL, last_error_code=NULL,
  result_kind=NULL, model_name=NULL, model_called=NULL, update_time=CURRENT_TIMESTAMP
WHERE id=#{id} AND task_type='MOOD_REPLY' AND status='queued'
  AND attempt_count=#{expectedAttempt} AND attempt_count<3
```

### 7.2 sourceForAttempt

先查job，要求类型MOOD_REPLY、status=running、attempt为1～3、attempt／request_id完全匹配；再查来源，要求id／user／content_version匹配、status=1且enabled=true。有效返回来源供Task3构造最少输入；无效只条件结束当前attempt为superseded，返回null。

这里不锁住来源等模型；它只能避免已失效来源开始生成，不能消除“预检后、网络开始前发生删除”的窗口。最终提交必须再检查；不承诺供应商停止计算或计费。

### 7.3 complete

实际源码顺序：

1. 事务外初筛job是否仍是这次running尝试；旧attempt直接false，不读来源、不写日志。
2. 开短事务，先按observed目标／用户锁来源。
3. 对job执行FOR UPDATE当前读；重新核对running、attempt、request_id及task_type/user/target/version关联。不能把初筛快照作为提交依据。
4. 来源无效时，仅结束仍属本次执行的job为superseded，false返回。
5. 校验回包；不合格将本次任务failed(invalid_model_response)，不写回复／日志，false返回。
6. 本次job终态CAS成功后，指定列写入日记回复。SQL必须仍匹配有效状态、归属、版本与enabled；影响行数非1则抛异常。
7. MODEL插入兼容成功日志，prompt=NULL；SUPPORT不插供应商成功日志。日志影响行数非1也抛异常。
8. 事务一起提交，返回true。源码虽先执行job成功UPDATE，其他事务不会看到已提交的部分成功；随后日记或日志失败会把这个UPDATE一起回滚。

```sql
UPDATE ai_job SET status=#{status}, last_error_code=#{errorCode},
  result_kind=#{resultKind}, model_called=#{modelCalled}, model_name=#{modelName},
  finished_at=CURRENT_TIMESTAMP, update_time=CURRENT_TIMESTAMP
WHERE id=#{job.id} AND task_type='MOOD_REPLY' AND user_id=#{job.userId}
  AND target_id=#{job.targetId} AND target_version=#{job.targetVersion}
  AND status='running' AND attempt_count=#{job.attemptCount} AND request_id=#{job.requestId}
```

```sql
UPDATE mood_record SET ai_reply=#{reply}, update_time=CURRENT_TIMESTAMP
WHERE id=#{id} AND user_id=#{userId} AND status=1
  AND content_version=#{expectedVersion} AND ai_reply_enabled=1
```

`complete()`返回值必须按下表理解，不能把所有false都当成相同原因：

| 情况 | 返回／异常 | 数据副作用 |
| --- | --- | --- |
| job不再是本次running尝试 | false | 不改其他尝试，不写来源／日志 |
| 当前来源已失效 | false | 仍属本次的job→superseded |
| 当前来源有效但回包非法 | false | job→failed，错误invalid_model_response |
| 有效提交成功 | true | job、来源、MODEL日志同时提交 |
| 数据库写入失败或条件异常 | 抛异常 | 本事务回滚；job可仍为running，Task3必须在原事务结束后以新事务fail(internal_error)收尾，并测试可重试；当前不假称已自动失败 |

### 7.4 MoodAiResponse校验

- request_id、job_id、attempt、source_version必须与当前执行精确匹配。ID／版本使用标准十进制字符串，预期值必须正数；attempt为1～3。
- template_version必须等于当前mood-reply-v1。
- 正文按与Python strip相应的Unicode空白口径去首尾空白，结果非空且不超过2000个Java UTF-16单元，拒绝孤立代理字符。
- MODEL必须model_called=true，模型名去Unicode首尾空白判断非空、原字符串Java length≤128且Unicode合法；写库仍使用原模型名。
- SUPPORT必须model_called=false、model_name=null；仅为空串不等于null。
- 未知result_kind、缺失result、错误关联或不合法字段归invalid_model_response。
- 这是数据对象语义校验；HTTP反序列化的字段类型／大小／超时保护仍需Task3客户端实现，不宣称单靠Java record已强制禁用Jackson类型转换。

## 8. 失败、重试与启动恢复

### 8.1 fail

先核验当前attempt，再使用同一个finishAttempt条件UPDATE结束为failed。错误白名单：invalid_request、unauthorized、provider_timeout、provider_error、invalid_model_response、overloaded、execution_interrupted、capacity_exceeded、internal_error；未知／null归internal_error。失败结果元数据清空，model_called保持未知NULL；不插MODEL成功日志，不把异常原文写入last_error_code。

### 8.2 retry

1. admission.save包住整个准入事务至提交。
2. 查job：不存在404、不属于当前JWT用户403、非当前MOOD_REPLY类型404。
3. 普通快照读取来源；缺失／删除／归属不匹配404，版本不匹配或enabled=false为409。此检查在所有幂等返回之前执行。
4. queued/running返回当前任务；succeeded返回已有结果；其他状态中仅failed且attempt<3可继续。
5. 容量满返回503，不改原任务；次数到上限或状态不可重试返回409。
6. CAS把原任务failed→queued，清request_id、运行／结束时间、最后错误与结果元数据；不增加attempt，不创建新任务，也不改来源版本。
7. 当前读job一次。CAS失败时仅在关联未变且当前为queued/running/succeeded时返回当前状态；否则409，不循环入队，更不能重置已完成另一轮的failed。

```sql
UPDATE ai_job SET status='queued', request_id=NULL, started_at=NULL, finished_at=NULL,
  last_error_code=NULL, result_kind=NULL, model_called=NULL, model_name=NULL,
  update_time=CURRENT_TIMESTAMP
WHERE id=#{job.id} AND task_type='MOOD_REPLY' AND user_id=#{job.userId}
  AND target_id=#{job.targetId} AND target_version=#{job.targetVersion}
  AND status='failed' AND attempt_count=#{job.attemptCount} AND attempt_count<3
```

### 8.3 recoverInterrupted

worker关闭返回0且不查询DB；启用时只将MOOD_REPLY的running→failed(execution_interrupted)，清结果元数据，保留attempt／request_id，queued不变。方法本身不能证明旧进程已停止；Task3启动编排须保证唯一worker和旧进程退出后才调用。当前尚未注册自动启动调用。

## 9. 锁、隔离级别与竞态推演

AiJobAdmission使用同一个TransactionTemplate。save拒绝已存在的外层实际事务，随后synchronized准入锁覆盖容量查询直到DB提交，避免锁释放时事务尚未提交造成超量；普通transaction不占准入锁。进程锁只适合单实例，不能替代来源行锁。

事务未显式改隔离级别，采用数据库默认隔离；本实现的“同一快照”读取及相关推演以MySQL REPEATABLE READ为前提。未来改变隔离级别需复核，不因使用了TransactionTemplate就假定任何数据库都有同样快照语义。

| 路径 | 锁顺序／事务 |
| --- | --- |
| add | 准入互斥锁 → 新来源／新任务写入 → 提交再放准入锁 |
| update | 准入互斥锁 → 来源FOR UPDATE → 任务失效／新任务 → 提交 |
| delete | 来源FOR UPDATE → 任务失效 → 提交 |
| complete | 来源FOR UPDATE → 任务FOR UPDATE当前读 → 条件写入 → 提交 |
| retry | 准入互斥锁 → 来源快照读取 → 任务CAS／当前读 → 提交；不获取来源行锁 |
| claim | 任务短CAS／当前读 → 提交；不在持有任务锁时去锁来源 |

关键交错：

| 场景 | 结果 |
| --- | --- |
| 删除先提交，旧回应后回来 | 来源仍软删除，当前回复NULL；未完成job已superseded；旧回应不写日志 |
| 回应先提交，删除后提交 | 有效历史成功日志可保留；删除清当前回复并隐藏来源，旧job查询拒绝 |
| status=0但异常版本恰好未变 | 来源status条件仍阻止回写；不能仅检查版本 |
| 编辑先提交，旧回应后回来 | 来源版本已变且旧未完成job失效；旧回应不覆盖新版本 |
| 第1次结果晚于第2次领取返回 | 版本可能相同，但attempt／request_id不符；不能改第2次或写旧成功日志 |
| retry已读来源，删除先提交 | 删除使failed→superseded；retry CAS=0，回查拒绝 |
| retry先CAS但未提交，删除随后开始 | 删除拿来源锁后等待job锁；retry不反向锁来源，能先提交；删除随后把queued失效 |
| complete后日志插入失败 | job成功、来源回复、日志全部回滚，不出现部分完成 |

两种删除／提交顺序的成功日志数量可以不同，因为它们反映删除前是否确实有一次有效提交；当前可见性最终都应为已删除、无当前回复。

## 10. 公共接口及返回示例

沿用Result与现有路径，Long／long字段通过Jackson转成字符串。请求身份来自token头及AuthInterceptor，不采纳请求参数中的userId。

| 接口 | 本次行为 |
| --- | --- |
| POST /mood/add | 原DTO moodType/content/needAiReply；data从原布尔改为MoodSaveVO |
| PUT /mood/update/{id} | 同上，每次成功修改递增来源版本 |
| DELETE /mood/delete/{id} | 原布尔包装，首次软删除并失效任务；重复删除原业务失败 |
| GET /mood/list | 保留moodType/startDate/endDate/pageNum/pageSize及分页包装；默认1页、每页5条，返回MoodRecordVO |
| GET /mood/detail/{id} | 原日记字段加回应状态／版本，不公开deletedAt |
| GET /mood/ai-job/{id} | 新增本人任务回查AiJobVO |
| POST /mood/ai-job/{id}/retry | 新增本人任务重试，queued/running/succeeded可幂等回查 |

保存成功示例（ID均为合成数据）：

```json
{"code":200,"message":"心情已保存","data":{"id":"9","contentVersion":"7","aiReplyEnabled":true,"aiReplyState":"queued","jobId":"10"}}
```

AiJobVO示例：

```json
{"code":200,"message":"查询成功","data":{"id":"10","targetId":"9","targetVersion":"7","status":"failed","attemptCount":1,"resultKind":null,"replyContent":null,"errorCode":"provider_timeout","retryable":true}}
```

MoodRecordVO字段：id、userId、moodType、content、aiReply、status、createTime、updateTime、contentVersion、aiReplyEnabled、aiReplyState、jobId、resultKind。AiJobVO不暴露request_id、模板／模型配置、prompt或上游原始错误。

新增任务接口错误采用HTTP401／403／404／409／503，并沿用Result包装。既有日记接口的部分校验／不存在错误仍使用HTTP200+Result.code=500，这是保留的旧行为，不把全部接口描述成统一REST错误语义。未登录被真实/mood/**拦截器拒绝为401。

## 11. 状态视图与隐私边界

列表／详情在同一事务快照中读取本人status=1来源与精确当前版本任务；getOwned同样组合任务与来源快照。当前页逐条查对应job，默认页5条；暂未批量JOIN优化，不能宣称零额外查询。

| 条件 | aiReplyState／回复 |
| --- | --- |
| 当前版本有job | 状态来自job；只有succeeded返回当前回复及resultKind |
| 没有job，有Unicode判定非空回复 | legacy；返回历史回复，resultKind=null |
| 没有job，无回复且enabled=false | disabled |
| 没有job，无回复且enabled=true | unavailable；仅表示缺少任务，不自动补生成 |
| 来源已删除／不存在 | 日记详情沿旧失败语义；任务接口404，不返回replyContent，不归类为unavailable |
| 旧版本任务／已关闭来源 | 任务接口409，不返回旧回复，即使旧job状态仍是succeeded |

任务retryable只表示当前来源合法且failed、attempt<3；容量是否允许要在真正retry时再检查，因此retryable=true也可能暂时返回503。

本单元不在job存正文，MODEL新日志prompt为NULL，错误分类不含原文；不承诺已完成全项目日志脱敏。旧AiReplyService／社区共享日志／管理端隐藏全文搜索等按Task3修复，历史日志清理另行规划。

## 12. 测试与独立审查证据

2026-11-09最初全量128项通过（28 IT）；根据外部评审新增2项无变化保存真实DB回归后，最新全量130项通过（30 IT）。Python全量114项通过，包含共享13值输入基准。

| 测试组 | 数量 | 替换／保留层 |
| --- | ---: | --- |
| AiJobStateTests | 32 | 真实保存服务；Mapper／事务管理器替身，覆盖保存、输入、开关、容量、删除和事务组织 |
| AiJobLifecycleTests | 15 | 真实任务服务；DB替身，覆盖失效／越权／次数／幂等与关闭worker |
| MoodAiResponseTests | 7 | 纯Java回包语义、关联字段、Unicode边界 |
| AiJobPersistenceIT | 30 | 真MySQL、MyBatis、Spring事务；少量Mapper包装只插入并发屏障，不替换SQL |
| AuthHttpTests | 43 | 真JWT／MVC／拦截器／JSON序列化；业务依赖替身 |
| CorsConfigTests | 1 | 跨域回归 |
| JacksonConfigTests | 1 | Spring启动配置下Long序列化 |
| XinyuBackendApplicationTests | 1 | 专用配置下contextLoads |
| 合计 | **130** | 0失败、0错误、0跳过 |

独立review_task2_final核对22文件及完整日志，未发现Critical／Important／Minor可操作问题；独立用JDK17+显式Mockito javaagent重跑前3组，共54项，全部通过。未独立重跑MySQL IT、HTTP或跨服务测试；不得把这54项再加进128宣称182个不同测试。

关键真实DB证据：rowLockSerializesDeleteAndCompleteInBothOrders、retrySnapshotBeforeDeleteCannotRequeueAfterDeletion、retryClaimedBeforeDeleteCommitsWithoutReverseSourceLock、concurrentClaimsOnlyOneAttemptWins、retryCasCannotTouchAnotherFailedAttemptAndConcurrentRetriesReuseOneJob、supportSkipsProviderLogAndLogFailureRollsBackWholeCompletion、globalCapacityFailureStillSavesAndCanRetryWhenQueueFrees、listViewsKeepPaginationAndExcludeDeletedOrForeignSources等。双连接通过performance_schema.data_lock_waits证明等待，不用sleep或纯mock冒充锁证明。

测试环境：本机/tmp独立MySQL8.4实例，监听33379，人工创建全新空xinyu_test_库。IT验证JDBC URL无额外query/userinfo/fragment、库名前缀、目标库为空；这些检查不能替代真实物理环境隔离。SpringBoot全量测试强制使用/tmp合成配置；无真实模型调用、无云端访问。临时MySQL已停止。

实际命令结构：

```sh
AI_TEST_JDBC_URL=jdbc:mysql://127.0.0.1:33379/xinyu_test_task2_commit_20261109 \
sh xinyu-backend/mvnw -o -f xinyu-backend/pom.xml \
  '-Dtest=*Tests,AiJobPersistenceIT' \
  '-Dspring.config.location=file:/tmp/heartisland-task2-test.yml' \
  '-DargLine=-javaagent:/Users/jiangqiuyu/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' test
```

复跑须新建空隔离库并更新配置，不能复用已迁移的非空库。命令引用的本机JDK／缓存路径不是跨机器通用安装说明。最新完整日志/tmp/heartisland-task2-commit-full.log，原128项日志/tmp/heartisland-task2-full.log，独立日志/tmp/heartisland-task2-independent-unit.log；临时文件可能被系统清理，长期结果记录在原AI_WORKLOG执行记录中。

## 13. 已做与尚未做的明确分界

| 项目 | 状态 |
| --- | --- |
| 保存／更新／关闭／软删、版本、入队与容量 | Task2已实现并验证 |
| 领取／生成前检查／条件提交／分类失败／同job重试 | Task2已实现并验证 |
| 本人回查／列表详情VO／恢复方法 | Task2已实现并验证 |
| Java→Python认证、HTTP解析、I/O总deadline | Task3未实现 |
| 单线程扫描、自动执行、启动恢复调用与停机编排 | Task3未实现；不能把恢复方法存在写成“重启自动恢复已跑通” |
| 社区共享客户端超时、假成功、日志隐私及管理端VO | Task3未完成 |
| 前端轮询、迟到回包、本地代次、状态刷新／重试UI | Task4未实现 |
| 多实例租约／逐attempt审计／Redis／聊天／周报 | 不在Task2范围 |
| Task2新结构线上迁移与应用部署 | 未执行；须用户授权并待完整1a验收 |

## 14. 希望评审者重点检查的问题

1. 当前job唯一键、target_version与任务类型映射是否准确表达当前业务边界，是否错误暗示已通用支持周报／多轮对话？
2. complete在来源锁之后是否真的重新当前读job，并核验完整attempt及关联身份？MySQL RR下有无过期快照遗漏？
3. delete／update／complete的来源→任务锁序和retry例外是否正确，有无死锁、旧结果回写或部分提交路径？
4. retry的所有幂等返回是否先检查当前来源？CAS失败后有无错误重置另一轮failed？容量与次数是否保持？
5. 删除／关闭／旧版本来源会否通过任务查询、日记VO或legacy／unavailable分类泄露／恢复旧回复？
6. 数据库异常、无效回包、SUPPORT和未知供应商调用状态是否被错误记成模型成功？
7. 测试是否真实覆盖关键竞争和回滚？哪些结论只是静态审查或合成测试，仍缺Task3／4的实际跨服务验收？

请区分必须修复的正确性／隐私问题、当前明确边界和可延后优化；不要把尚未承诺的多实例／主动重新生成当作当前已实现能力。发现问题时给出文件、方法、具体触发时序／输入及影响，数据库方案改动仅提出供用户审议，不直接执行。

## 15. 外部评审后的修订与现场核验

1. **心情枚举**：评审者看到远端Task1的6值契约；本地Java／Python早已是13值，但未提交导致不可见。提交会同时纳入Python契约及测试，并使用test-contracts/mood-types.json作为两端的共享输入基准。Java真实保存校验和Python实际ASGI请求分别覆盖同一13值；不以解析Java源文本冒充行为测试。真正Java→Python HTTP矩阵为Task3必做。
2. **提交异常**：Task2数据库异常仍抛出并回滚，未越界新增worker；Task3计划和当前契约已明确要求新事务fail(internal_error)及可重试回归。持续数据库故障时不承诺失败收尾一定成功。
3. **无变化保存**：新增两项真实DB回归先RED（预期版本1实际2），修复后保留queued/running/failed/succeeded及disabled/legacy/unavailable状态、不清回复、不新增任务、不修改update_time。新行为已纳入最新130项回归。
4. **失效UPDATE索引**：实际SQL原本已包含task_type='MOOD_REPLY'、target_id、user_id及status条件，无需再补类型条件。隔离MySQL8.4中构造100用户、每用户20目标、每目标5版本，共10000条代表性任务并ANALYZE；EXPLAIN FORMAT=JSON实际选择uk_ai_job_target_version，access_type=range，used_key_parts=[task_type,target_id]，rows_examined_per_scan=5。仅说明该数据分布下的计划，不保证任意分布／版本都选同一索引；未因此强制索引或修改schema。next-key间隙短时等待仍可能存在，不能从EXPLAIN推断完全没有范围锁。原始证据/tmp/heartisland-task2-explain.json。
5. **发布顺序**：已在当前契约写明先备份／迁移，再启动新版本，禁止新旧应用及worker同时处理业务；旧应用过渡写入无job但有回复可显示legacy，不等于支持混合部署。

无变化保存与共享测试基准的小delta已由原reviewer另行只读复核，无阻断项；核对最新130项后端／114项Python和EXPLAIN证据，本次未独立重跑测试。

最初独立22文件审查针对无变化保存修订前的快照；后续修订及最新源码SHA在下方重新列出，不能将旧快照一致性声称为新改动也已原样审查。提交记录将使评审者可以实际检查代码；本地commit并不意味着远端已推送。

## 附录：固定评审文件与SHA256

以下路径相对仓库根目录；外部评审若需要核实实现，应同时读取这些文件，不能仅凭本文判断源码正确。

```text
c992feeee89f044050c603257ed9fdc9332557224c8e67c304f6a5e273f53d77  xinyu-backend/src/main/java/com/xinyu/entity/AiJob.java
82f524798880c8501fd8f1039a622426174afe05812936d0b032a8d41a6fef5a  xinyu-backend/src/main/java/com/xinyu/entity/MoodRecord.java
42436f85dd2bcbcc896b1deb76f2d28fca20f3c4911a24dc53541b4a69f2b4a8  xinyu-backend/src/main/java/com/xinyu/mapper/AiJobMapper.java
d5cdd8ff526ea121eb1c3478e5cb64825a79a58843cb8e11f5e8fc0948f864dc  xinyu-backend/src/main/java/com/xinyu/mapper/MoodRecordMapper.java
ad3cdc5d5afdf0df9f127d484481fdb770f3f7425833a9539bc1dae609ce0d48  xinyu-backend/src/main/java/com/xinyu/service/AiJobAdmission.java
16652ce9fca774e0f43c3dc4d30c9b2eedf5bbbaa42452946fe2180a344e8e77  xinyu-backend/src/main/java/com/xinyu/service/AiJobService.java
41bcea03a690ae26e5b47ee4928fa50e579acebec2df10d3cbfddcc5b3ec32bc  xinyu-backend/src/main/java/com/xinyu/service/MoodRecordService.java
9be019d53b3a49c7ad9495c22392d9f6252c2b6217fe4cd6a1b8fa4658e943a6  xinyu-backend/src/main/java/com/xinyu/controller/AiJobController.java
bb81b94f90c2897ac25b4a09872ec1c4b4f3b7111e0e24d453315d73d94c3b95  xinyu-backend/src/main/java/com/xinyu/controller/MoodRecordController.java
974f1e84ea000fed2490b7fbfaf4b6dd40af71d76dbfcfcb98988c8e0a78d38b  xinyu-backend/src/main/java/com/xinyu/dto/MoodAiResponse.java
4340e68a5e39fdd4253d53072f647a149f87e25b421977569e243678079d96b7  xinyu-backend/src/main/java/com/xinyu/vo/AiJobVO.java
bb30ea18d4fad0493ec80d51f5765df876600ab0b9de8896ffbe6bcecb048ba1  xinyu-backend/src/main/java/com/xinyu/vo/MoodRecordVO.java
389f4d0ec8a3c454929071f68f1e7e9074ec201bdb49d91ef7f7e041da9e24b0  xinyu-backend/src/main/java/com/xinyu/vo/MoodSaveVO.java
5a102bd14d0dd4380bd92e5c743007e4e932b0592f706a41d0713e9ca529fcb0  xinyu-backend/src/test/java/com/xinyu/ai/AiJobStateTests.java
28a8e10db2c8c26797f596d72da5535f9cb4be9c6eb15c0083fdcfde674d6549  xinyu-backend/src/test/java/com/xinyu/ai/AiJobLifecycleTests.java
7f32d059489b21a4c79e7bbc8beb082d43e154193d3dda50124382eaa576abf3  xinyu-backend/src/test/java/com/xinyu/ai/AiJobPersistenceIT.java
5c1f5608608629b6558ec98ecfda92963fcfa9526e771190f216ad4992113fc2  xinyu-backend/src/test/java/com/xinyu/ai/MoodAiResponseTests.java
202fb67fc72a00d783f3d6d2c1fe4e521fe45e50225338ff8a04086c5aa966ea  xinyu-backend/src/test/java/com/xinyu/auth/AuthHttpTests.java
3c3af59f8dd31aa0fa5e79387e078c32a4e1c0254fc1c3826fc50e28e373a054  xinyu-backend/src/test/resources/ai/schema.sql
26c39ad624f31b85676de44316d7cc7e03e7325e8ac7b411f1d666c4fb6b3aff  sql/migrations/20261107_01_ai_reply_job.sql
39af8aa12e2ab0aaa2ed785a9ffc398083960c2d2d35296b2a174e14fb87fa4a  sql/migrations/20261107_02_ai_reply_model_name.sql
b62b55b48964ea84abb2f752e29b78c4e1638accc69f290db5406d029b60cd50  sql/xinyu.sql
d8c81265d7c2d81a66b7f71ffe22385038ab69332d3797fea787ef9e9f561476  test-contracts/mood-types.json
80b330d439990617e61e7b23d07dd3b243197598a0056939281c204dfbae48af  xinyu-ai/app/contracts.py
2d63a410d951005bc57ca88e25ff898d5624a971c3de0c6840e0cb96c1a7012c  xinyu-ai/app/provider.py
50449deae1a8cfac4b2bbf2afeb7b380e6cac6bfa1b05c46da5f06bc62a8433e  xinyu-ai/tests/test_contract.py
```
