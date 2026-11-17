# 首次完整上线运行手册

对应任务书 `AI_WORKLOG/plans/2026-11-17-deploy-plan.md`。这里只提供模板与逐步核查，不是自动部署脚本。A 段交付后停靠点 1；用户确认后才进入 B 段。服务器与云数据库的**每一步写操作**、提交及 push 均须按对应停靠点在聊天中明确同意，不合并成一次笼统授权。

## 上线原因 / 风险

本次把旧同步版本升级为单实例异步心情回复、Python 服务、回顾/周报、Redis 限流、社区举报和昵称选填，前后端必须同批。

**仓库为公开仓库，旧 JWT 固定密钥自初始提交起已在历史中，线上旧版本当前即可被伪造令牌。** 这不是等本轮上线才出现的风险；删除当前源码里的常量也不能删除公开历史。新版本必须使用独立随机 `JWT_SECRET`，缺失、少于32个UTF-8字节或旧固定密钥均拒绝启动，不允许固定回退。新密钥上线后所有用户需要重新登录；旧令牌（包括伪造令牌）不再通过签名校验。回退到旧后端会重新暴露此漏洞，不能把旧版本回退称为安全方案。

## 文件、环境与发布顺序

- `env/*.env.example` 是占位模板，不能原样启动；Java环境逐项对照 `application.yml`、`AiServiceClientConfig`、`AiJobRuntimeConfig`、`RestTemplateConfig`、`JwtUtil`；Python对照 `app/settings.py`。不使用旧说明中不存在的 JWT 配置项。
- Java17；Python3.12与 `uv.lock`；前端构建Node `^20.19.0 || >=22.12.0`。Python生产包只含 `app/`、`pyproject.toml`、`uv.lock`，不部署测试控制端点；提示词已在app模块内，不依赖测评目录。
- Java只监听回环8080、Python只监听回环8000且1个uvicorn worker；Redis只监听回环6379。所有真实路径和运行用户由B段现场确定，模板中的`<...>`不代表实际部署值。
- Redis64MiB为准备值，B段按余量确认；关闭RDB/AOF、allkeys-lru、密码认证。只保存短期计数，重启丢计数是已接受代价。额度保持20次心情/小时、100次/日、10次社区/小时、10次回顾/日、全站1000次/小时。
- 环境变量宽松绑定必须使用模板的 `XINYU_RATELIMIT_*`（如`XINYU_RATELIMIT_KEYPREFIX`），连字符不转换成额外层级；不能照抄旧说明的`XINYU_RATE_LIMIT_...`写法。本机预演通过Redis键前缀验证。

唯一顺序：停旧应用并确认退出 → 备份且恢复验证 → 逐份迁移 → Redis → 新Java（worker关）→ Python → 前端 → 基础冒烟 → 确认只有一个Java进程 → 重启Java开启唯一worker → 完整冒烟 → 观察30分钟。Java先接受v2模板，Python才能开始返回v2。

## A：本地准备与预演

在任务专属临时构建副本执行，保留用户和测评执行者文件/产物；不能在工作区运行会`--fix`的lint。先`lsof`确认28080/28000/28100/28081/28379空闲，不使用8080/5173/8000和测评18000/18080/18081/18100。仅使用终端已有的`AI_TEST_JDBC_URL`（回环、`xinyu_test_`前缀）、`AI_TEST_DB_USER`、`AI_TEST_DB_PASSWORD`，脚本不会打印它们。

1. `python3 -B deploy/check/local_schema.py --output deploy/check/schema-after.json`：新库与`2036e7a`旧库+5份迁移对比，重复初始化后再次比对；成功为12表无结构差异，finally清理自己创建的库。失败先检查错误码，不改业务库或增量文件。
2. 在临时源码副本分别运行 `mvn test`、`mvn -DskipTests package`（Java17），Python `python -m pytest -q`、前端 `node --test tests/*.test.js`，`VITE_API_BASE_URL=/api npm run build`。测试使用`src/test/resources/application.properties`合成JWT配置，绝不将该资源打入生产jar。
3. 若本机Mockito自挂载不可用，可在**测试命令**的`argLine`提供已解析的Mockito测试agent，不改生产配置。启用回环HTTP/隔离MySQL所需的本机测试权限，不修改测试预期来掩盖环境失败。
4. `JAVA_HOME`指向本机JDK17后，执行`python3 -B deploy/check/rehearse.py --build-root "$LOCAL_BUILD_ROOT" --output deploy/check/rehearsal.json`。`LOCAL_BUILD_ROOT`必须位于`/private/tmp`且已含打包结果、Python环境与前端dist。预演创建合成旧库、备份恢复验证、逐份迁移、启动Redis/Java/Python/前端静态代理替身、跑只读冒烟，确认worker关闭不领取，再停止旧Java、启动唯一worker走通一次合成生成并核对持久化。失败自动停止自身进程并清理自身测试库，日志只在临时构建目录中。

成功结果见`check/schema-before.json`、`check/schema-after.json`与`check/rehearsal.json`。macOS预演使用静态代理替身验证产物和history路径，**不证明真实Nginx/systemd模板已运行**；Linux运行核验必须留到B/D。A完成停靠点1，审阅SQL diff、迁移清单、模板和预演证据。

## B：服务器只读核查（须先经停靠点1确认）

用户在终端设置SSH连接参数、云库连接和必要凭据；不把地址、账号、密码、密钥写入本轮文件或聊天。禁止`set -x`、`env`/`printenv`全量输出、`ps e`、原样`cat`配置或把`systemctl show Environment`结果发回聊天。只记录资源数值、版本、脱敏结构和变量名。

逐项核查：OS/CPU/内存/磁盘、Java/Python/uv/Nginx/MySQL客户端与Redis是否存在；现有jar/前端/日志位置及启动方式（实际路径只在用户终端使用）；旧配置中变量**名称**；既有Nginx路由和证书到期日期（仅记录日期和是否覆盖目标站点）。SSH不安装软件，不上传模板，不建临时文件，不改已知主机配置或远端环境。

云库运行 `check/migrations/00-inventory.sql`，补齐所有现有表的行数和`SHOW CREATE TABLE`。MySQL版本、binlog格式、JSON、现有模型列128与迁移前置结构按[MIGRATIONS.md](MIGRATIONS.md)判断。status=0与后台操作证据只能只读对照，无证据则写“无法区分作者删除与旧管理员隐藏”，等用户决定。

**新增必查：线上旧后端进程是否显式设置`DB_PASSWORD`、是否为`application.yml`里的默认值。** 由用户在终端选择正确PID，再通过SSH stdin运行`check/runtime_env.py`（只传数字PID）；脚本只返回`db_password_configured`和`db_password_is_default`布尔值。读取失败或存在命令行/其他配置覆盖时不能声称实际数据源已核验，交用户在终端核实，报告仍只写是否设置、是否为默认值，不输出密码。当前公开开发默认值本轮保持不改。

根据实测资源/部署方式修订本手册和停写窗口；B完成停靠点2，交用户确认。没有现状证据不能原样覆盖Nginx、证书或服务单元。

## C：停写、备份和迁移（逐步授权）

1. **停止旧应用/关闭入口**：先报告准确的旧服务停止命令和维护窗口影响，经用户同意才执行。用正确的服务状态、PID和监听端口确认进程及在途同步生成均退出，其他写入方也已停止。失败不迁移、不启动新版本。
2. **完整备份**：先报告工具、全库结构+数据/触发器/例程/事件、服务器本地目标及用户指定的第二位置，分别取得对应写操作授权。凭据由终端注入，避免`-p明文`、命令行URL或输出配置。示例参数：`mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces "$DB_NAME" > "$BACKUP_FILE"`（连接参数由用户终端配置，不写进文件）。核实均为InnoDB且无并行DDL；记录开始/结束、字节数、SHA-256。磁盘不足或dump失败立即停，不覆盖唯一旧备份。
3. **验证备份**：取得临时恢复库创建/导入/清理的具体授权；恢复到专属空库，对照备份时逐表行数与结构、抽查关联记录和外键，不能只检查文件存在。第二位置校验SHA-256一致。验证不通过不迁移。备份恢复本身也是服务器/云库写操作，不纳入B段。
4. **停靠点3**：展示备份验证结果，再逐份请求迁移授权。使用迁移清单每份before→迁移→after，检查结构、计数、历史回填及原字段未变，记录执行结果。第2份已完整为128则跳过。**任一核查不符进入停靠点4**，DDL可能部分提交，不能自动继续/回滚/重跑。

## 推送与发布版本

本轮所有提交另待停靠点统一确认。push为停靠点5：列出`git log --reverse --oneline origin/main..HEAD`的完整待推送清单，核对最终发布提交与产物SHA-256，用户明确同意才push。不把工作区版本伪称为已提交版本；带未提交修复的产物仅用于本机预演。实际上传部署前，重新从最终确认提交构建/核验，不拿早期产物冒充最终提交。

## D：逐步部署（每一项独立确认）

上传、安装依赖、创建目录、写配置、启动/重启/启用服务、修改软链接或Nginx都属于服务器写操作；先给具体动作/目标/校验/失败处理，再请求相应授权。只执行用户同意的那一步。

1. **Redis**：据B段确认包版本与包自带服务，安装/配置分别授权。合并`redis/redis.conf.example`；密码由用户在终端设置，Java的REDIS_URL密码必须URI编码。确认`ss -ltnp`只有127.0.0.1:6379，认证PING成功且无认证请求被拒绝；核对maxmemory、allkeys-lru、RDB/AOF关闭。失败保持worker关闭；可临时本机计数，但最终验收需恢复Redis。
2. **发布Java，worker先关**：上传确认版本jar并核对SHA-256；将systemd模板中的用户/目录/环境文件/Java17路径替换为B段确认值。用户在服务器终端创建受限EnvironmentFile（父目录受限，文件0600），配置全部必需变量，**不在聊天或仓库出现实际值**。JWT按下段生成。设置`AI_WORKER_ENABLED=false`；daemon-reload、启动服务各自先获授权。检查服务active、`/hello`、匿名401、日志无启动错误、Java只监听回环预期端口；失败停在当前步，不启动Python或开放写入。
3. **Python**：上传仅运行时包、核对SHA-256；使用Python3.12执行`uv sync --frozen --no-dev`（需授权安装依赖），不从开发机复制venv。用户终端配置EnvironmentFile，内部令牌与Java一致。部署模板为1个uvicorn worker、127.0.0.1:8000，健康返回`{"ready":true}`。检查active、端口绑定及日志；失败保留Java worker关闭。
4. **前端与Nginx**：上传同批dist、校验版本与产物，保留旧目录供回退。把`nginx/site.conf.example`合入B段确认的HTTPS站点，保留证书和HTTP跳转。`nginx -t`通过后，再单独授权reload。验证`/review`、`/profile/reports`、`/admin/reports`刷新能到SPA；`/internal/`、`/api/internal/`及其子路径均404。API去掉`/api`前缀；Nginx30秒等待大于前端社区20秒，超时不得自动重发（帖子可能已保存）。失败不开放业务入口，回退Nginx也需授权。
5. **基础冒烟**：使用专用测试账号执行下节只读脚本和无AI写入；worker尚关，此时不要求AI完成。测试账号注册和每一类生产写入均先授权。失败不启用worker。
6. **开启唯一worker**：确认旧Java/所有旧worker都退出、全机仅一个Java后端，核对迁移和Python健康；报告修改`AI_WORKER_ENABLED=true`与重启唯一Java的具体动作并获同意。启动恢复会将遗留running标failed、保留queued继续消费，重启前后记录队列计数。启用后再核对进程、端口与任务完成，无自动供应商重试。失败停worker的操作同样先报并获授权。

### JWT在服务器终端生成

在本步写EnvironmentFile已获授权后，由用户在**服务器终端**设置`BACKEND_ENV_FILE`（实际值不发回聊天），对已准备且尚未含JWT_SECRET的文件执行：

```sh
umask 077
printf 'JWT_SECRET=%s\n' "$(openssl rand -base64 48)" >> "$BACKEND_ENV_FILE"
chmod 600 "$BACKEND_ENV_FILE"
```

命令不打印密钥，只把随机值写入服务器EnvironmentFile；不要重复追加造成多个定义，不使用开发/测试资源中的值。不要将该文件复制进仓库、报告或聊天。应用拒绝无效配置时错误为`invalid_jwt_secret`，不应输出密钥。上线后明确通知**所有用户需重新登录**。服务器上其他实际凭据由用户终端管理，不由本地交付文件承载。

## E：冒烟与观察

`check/smoke.py`只执行GET：Java/Python健康、匿名401、普通用户权限与读取、后台列表、Redis状态、可选SPA/history与内部路径封闭。配置名：`SMOKE_BACKEND_URL`、`SMOKE_PYTHON_URL`、`SMOKE_FRONTEND_URL`、`SMOKE_USER_TOKEN`、`SMOKE_ADMIN_TOKEN`；值仅用户终端提供，脚本不打印响应原文或令牌、不跟随重定向、不读取宿主HTTP代理。未提供用户/管理员令牌时结果会标记对应检查未做，不能声称完整验收。实际API返回`rateLimitBackend=redis`，前端显示“Redis”。

经用户逐项明确同意后，用测试账号：登录→保存关闭AI心情→保存开启AI心情并等待→查看回顾页→关闭AI发帖→打开举报入口→核对后台概览和所有列表；同时在浏览器验证页面、权限、刷新与轮询终态。**事先报本次真实模型调用总数为1次**（只生成一次普通合成心情回复，不触发回顾生成/社区AI）；额外重试也算新调用，必须重新说明且总数不超过3次。只读脚本不包含任何生成入口。

冒烟通过后连续观察30分钟，检查Java/Python/Nginx错误分类、后台失败数、队列状态和进程；诊断只记录分类/数量/时间，不抄用户正文、JWT、prompt、原始供应商错误或含凭据配置。不要把单次成功称为质量测评、性能保证或30分钟稳定性证据。测试数据保留/删除由用户决定，删除必须单独授权。

## 回退

先报告触发原因、准确版本、会丢失/停用的功能和是否涉及数据，再取得回退授权。顺序：停止接收新写入/新AI任务→停worker并确认退出→停新应用→前后端配套回退→按旧契约回查。新增列/表保留，queued/running及新报告不能假装已由旧版本处理；旧同步超时/公开回复旧问题及旧JWT漏洞会重现。Python/Redis停止或配置改动都另需授权。

通常不删表、不缩模型列。只有备份验证及核查证实数据损坏/误初始化等情况才提出恢复备份方案；恢复前再次停写，明确备份之后数据的损失边界、第二位置副本与恢复验证，用户明确同意后才实施。不能自动执行恢复或不安全的旧后端回退。
