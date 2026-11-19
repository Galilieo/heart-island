# 首次完整上线运行手册

对应任务书 `AI_WORKLOG/plans/2026-11-17-deploy-plan.md`。这里只提供模板与逐步核查，不是自动部署脚本。A 段交付后停靠点 1；用户确认后才进入 B 段。服务器与云数据库的写操作按停靠点在聊天中明确确认：文件准备、核查及不启停服务的单元加载可在用户允许后连续完成；停写、数据库写入、启动/切换服务、生产凭据变更、提交及 push 等关键动作仍在对应停靠点单独确认，不合并成一次笼统授权。

## 社区同步回复迁移的发布顺序

本次增量发布按 **迁移 → Python → Java 与前端同批**。

1. 备份并确认 `community_post.ai_result_kind` 不存在。按 [迁移核查单](MIGRATIONS.md) 的第 6 项验证迁移前后行数和原回复计数。
2. 切换 Python 包，确认 `/health` 返回 200，新版本已注册社区内部端点。沿用 Java 与 Python 共享的内部令牌，两端按模板设定社区名额。
3. 同批切换 Java 和前端，所有 Java 实例都需配置内部地址和令牌。核对 Java 健康、公开帖子里的 `resultKind` 和管理端筛选，并确认旧 Java 退出后再启用唯一 worker。Python 未就绪时暂停 Java 切换。

下文保留 2026-11-17 首次完整上线的背景与流程，其中 Java 先于 Python 的顺序对应当时的心情模板切换。

## 上线原因 / 风险

本次把旧同步版本升级为单实例异步心情回复、Python 服务、回顾/周报、Redis 限流、社区举报和昵称选填，前后端必须同批。

**仓库为公开仓库，旧 JWT 固定密钥自初始提交起已在历史中，线上旧版本当前即可被伪造令牌。** 这不是等本轮上线才出现的风险；删除当前源码里的常量也不能删除公开历史。新版本必须使用独立随机 `JWT_SECRET`，缺失、少于32个UTF-8字节或旧固定密钥均拒绝启动，不允许固定回退。新密钥上线后所有用户需要重新登录；旧令牌（包括伪造令牌）不再通过签名校验。回退到旧后端会重新暴露此漏洞，不能把旧版本回退称为安全方案。

## 文件、环境与发布顺序

- `env/*.env.example` 是占位模板，不能原样启动；Java环境逐项对照 `application.yml`、`AiServiceClientConfig`、`AiJobRuntimeConfig`、`JwtUtil`；Python对照 `app/settings.py`。不使用旧说明中不存在的 JWT 配置项。
- Java17；Python3.12与 `uv.lock`；前端构建Node `^20.19.0 || >=22.12.0`。Python生产包只含 `app/`、`pyproject.toml`、`uv.lock`，不部署测试控制端点；提示词已在app模块内，不依赖测评目录。
- Java只监听回环8080、Python只监听回环8000且1个uvicorn worker；Redis只监听回环6379。所有真实路径和运行用户由B段现场确定，模板中的`<...>`不代表实际部署值。
- Redis64MiB为准备值，B段按余量确认；关闭RDB/AOF、allkeys-lru、密码认证。只保存短期计数，重启丢计数是已接受代价。额度保持20次心情/小时、100次/日、10次社区/小时、10次回顾/日、全站1000次/小时。
- 环境变量宽松绑定必须使用模板的 `XINYU_RATELIMIT_*`（如`XINYU_RATELIMIT_KEYPREFIX`），连字符不转换成额外层级；不能照抄旧说明的`XINYU_RATE_LIMIT_...`写法。本机预演通过Redis键前缀验证。

2026-11-17 首次完整上线顺序：**停写前完成运行时、依赖、Redis和发布材料准备（旧应用继续运行）** → 到用户确定的时间停止旧应用 → 备份与恢复验证 → 逐份迁移（结构已在生产完成的跳过）→ 新Java（worker关）→ Python → 前端/Nginx → 基础冒烟 → 确认唯一Java后开启worker → 完整冒烟 → 恢复服务 → 观察30分钟。Java先接受v2模板，Python才能开始返回v2；前后端同批。

## A：本地准备与预演

在任务专属临时构建副本执行，保留用户和测评执行者文件/产物；不能在工作区运行会`--fix`的lint。先`lsof`确认28080/28000/28100/28081/28379空闲，不使用8080/5173/8000和测评18000/18080/18081/18100。仅使用终端已有的`AI_TEST_JDBC_URL`（回环、`xinyu_test_`前缀）、`AI_TEST_DB_USER`、`AI_TEST_DB_PASSWORD`，脚本不会打印它们。

1. `python3 -B deploy/check/local_schema.py --output deploy/check/schema-after.json`：新库与`2036e7a` 旧库依次应用现有迁移后对比，重复初始化后再次比对；成功为12表无结构差异，finally清理自己创建的库。失败先检查错误码，不改业务库或增量文件。
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

### B段后的通用注意事项

现场数值、实际路径与计数只写入本地忽略的执行记录，不写进本手册。

- **资源与混跑**：小内存单机多站共用时，先停旧Java再启新Java，禁止新旧混跑；初始JVM堆按余量设定（见环境模板`JAVA_TOOL_OPTIONS`），该值是资源预算，不是容量验证。新服务只绑定回环；旧进程若监听通配接口，切换后复核。
- **运行时**：不替换系统Python；uv与独立Python3.12装到专用运行时前缀，依赖同步显式指定该解释器。
- **配置隔离**：旧部署若带外部`config/application.yml`，新Java使用独立发布目录作为WorkingDirectory，只放确认的jar，不复制旧外部配置，避免覆盖EnvironmentFile；旧文件保留作回退依据，含凭据的配置不进报告或仓库。新EnvironmentFile显式提供DB_PASSWORD、JWT_SECRET等全部必需变量。
- **历史状态**：旧`status=0`无法区分作者删除与管理员隐藏时保留原值，修正属于另行范围。
- **Nginx/HTTPS**：以唯一匹配的心屿server块为编辑边界，不整体覆盖站点文件，保留证书、HTTP跳转与其他站点；按模板补内部路径拒绝及30秒代理等待。
- **停止旧进程**：停前核对PID、jar哈希和监听归属，正常SIGTERM，最多观察45秒；未退出则报告并等待授权，不自动SIGKILL，不猜测或停止不存在的systemd单元。

MySQL8.0客户端不支持8.4的`--no-login-paths`。服务器mysql/mysqldump使用`--no-defaults`、终端/内存中的显式连接参数，并将`MYSQL_TEST_LOGIN_FILE=/dev/null`（及mysql的`MYSQL_HISTFILE=/dev/null`），不读取既有登录配置、不持久化SQL历史。密码不放命令行，不打印原始客户端错误。[MySQL官方环境变量说明](https://dev.mysql.com/doc/refman/8.0/en/environment-variables.html)支持覆盖登录文件位置。

运行时固定uv0.12.5及CPython3.12.14官方归档（发布元数据与预期SHA-256见`runtime-manifest.json`），按P段核对摘要后解压到独立版本目录，再以该解释器执行`uv sync --frozen --no-dev`。不运行修改shell配置的自动安装脚本。依据[uv安装说明](https://docs.astral.sh/uv/getting-started/installation/)及[Python安装指南](https://docs.astral.sh/uv/guides/install-python/)。Redis安装包可能自动启动服务，安装方案必须提前说明这一副作用，取得包含该动作的明确授权或采用经审阅的抑制自动启动方案；不能在B段安装或启动。

停写窗口建议30～45分钟，只包含停旧应用、备份与恢复验证、迁移、新服务/前端/Nginx切换和冒烟；恢复服务后再观察30分钟，观察时间不计入停写。下载、安装、依赖同步和材料上传均提前完成，下载失败立即停止报告，旧应用继续运行。

## P：停写前准备（不影响旧应用）

本节只做不影响旧应用的准备。每项先给具体范围、校验与失败处理再取得授权；用户允许基础准备连续执行时也逐项记录。该许可不覆盖停写、数据库写入或新服务启动等关键动作。

1. **uv与独立Python3.12**：只写专用运行时前缀。下载→按`runtime-manifest.json`核对大小与SHA-256→检查归档路径→解压到版本专用目录→验证版本及ssl/sqlite3/ctypes导入。归档检查按链接所在目录解析并跟随归档内链接图，拒绝真实越界/循环和链接目录写入；CPython归档含大量合法相对符号链接，不能按“含`..`即拒绝”误判。任何下载/校验失败立即停，不自动换源、重试、清理已有目录或放宽检查。
2. **发布目录**：按发布提交新建`<RELEASE_ROOT>/<commit>/`及backend、python、frontend、packages子目录；不覆盖既有目录，不改当前WorkingDirectory、Nginx根或活动软链接。
3. **上传发布包**：先以`.part`落盘，实算大小与SHA-256均与干净提交清单一致才改名；展开后逐文件核验。Python只展开`app/`、`pyproject.toml`、`uv.lock`。
4. **Python依赖**：`uv sync --frozen --no-dev --no-python-downloads --no-build --python <独立解释器>`，核对生产包版本与`uv.lock`一致、导入通过、离线`uv pip check`通过；uv缓存与临时目录放在运行时前缀。
5. **Redis**：包版本与依赖先只读预检；安装时抑制自启的临时规则用后撤销。配置限制权限，密码由用户终端隐藏输入经SSH stdin写入服务器，不落本机。启动后核对仅回环监听、匿名NOAUTH/认证PONG、64MiB/allkeys-lru、RDB/AOF关闭；开机自启另行确认。
6. **环境文件**：受限目录0700、文件0600；JWT密钥与内部令牌在服务器生成（Java/Python内部令牌一致），不输出、不出服务器。Java初始`AI_WORKER_ENABLED=false`。
7. **systemd单元**：WorkingDirectory固定为本次发布目录，EnvironmentFile指向受限文件；`systemd-analyze verify`通过后，daemon-reload单独确认。运行账号由现场确定；迁移完成前不启用新服务开机自启。
8. **迁移与备份材料**：上传待执行迁移和只读核查SQL并逐一核验；确认mysql/mysqldump版本、磁盘余量与备份/恢复权限；准备服务器本地0700备份目录及用户指定的第二位置。

目录/上传/依赖同步按实际依赖顺序安排，Redis可在其独立授权后进行；这不合并任何授权。所有准备核验完再确认停写开始时间。

## C：停写、备份和迁移（逐步授权）

1. **停止旧应用/关闭入口**：先报告准确的旧服务停止命令和维护窗口影响，经用户同意才执行。用正确的服务状态、PID和监听端口确认进程及在途同步生成均退出，其他写入方也已停止。失败不迁移、不启动新版本。
2. **完整备份**：先报告工具、全库结构+数据/触发器/例程/事件、服务器本地目标及用户指定的第二位置，分别取得对应写操作授权。凭据由终端注入，避免`-p明文`、命令行URL或输出配置。示例参数：`mysqldump --single-transaction --routines --triggers --events --set-gtid-purged=OFF --no-tablespaces --skip-comments "$DB_NAME" > "$BACKUP_FILE"`（连接参数由用户终端配置，不写进文件）。核实均为InnoDB且无并行DDL；记录开始/结束、字节数、SHA-256。磁盘不足或dump失败立即停，不覆盖唯一旧备份。
3. **验证备份**：取得临时恢复库创建/导入/清理的具体授权；恢复到专属空库，对照备份时逐表行数与结构、抽查关联记录和外键，不能只检查文件存在。第二位置固定为用户本机仓库外的独立目录，权限0700，不能位于iCloud同步目录。核对解析后的真实路径（含符号链接），拒绝仓库内或iCloud路径，不能把目录值写进报告或聊天。传输后两端SHA-256必须一致，备份文件采用0600。验证不通过不迁移。备份恢复本身也是服务器/云库写操作，不纳入B段。
4. **停靠点3**：展示备份验证结果，再逐份请求迁移授权。使用迁移清单每份before→迁移→after，检查结构、计数、历史回填及原字段未变，记录执行结果。第2份已完整为128则跳过。**任一核查不符进入停靠点4**，DDL可能部分提交，不能自动继续/回滚/重跑。

## 推送与发布版本

本轮所有提交另待停靠点统一确认。push为停靠点5：列出`git log --reverse --oneline origin/main..HEAD`的完整待推送清单，核对最终发布提交与产物SHA-256，用户明确同意才push。不把工作区版本伪称为已提交版本；带未提交修复的产物仅用于本机预演。实际上传部署前，重新从最终确认提交构建/核验，不拿早期产物冒充最终提交。

推送以明确确认的发布提交为源，不推当前HEAD上尚未确认的提交；推送前核对远端当前提交并重算发布包大小/SHA-256，推送后只读核对远端。

## D：首次完整上线部署步骤（2026-11-17）

本节记录首次完整上线顺序。社区增量发布使用文首的 Python 先行步骤。

上传、安装依赖、创建目录、写配置、启动/重启/启用服务、修改软链接或Nginx都属于服务器写操作；先给具体动作/目标/校验/失败处理，再请求相应授权。只执行用户同意的那一步。

1. **Redis就绪复核**：P段已按具体授权完成安装/配置；此处复核回环监听、认证、64MiB/allkeys-lru及持久化关闭。若P段未获启动授权，此处另行请求启动。失败保持worker关闭，不在停写窗口临时补装或下载软件。
2. **启动Java，worker先关**：使用P段已上传并校验的确认版本jar，再复核SHA-256；将systemd模板中的用户/目录/环境文件/Java17路径替换为B段确认值。用户在服务器终端创建受限EnvironmentFile（父目录受限，文件0600），配置全部必需变量，**不在聊天或仓库出现实际值**。JWT按下段生成。设置`AI_WORKER_ENABLED=false`；daemon-reload、启动服务各自先获授权。检查服务active、`/hello`、匿名401、日志无启动错误、Java只监听回环预期端口；失败停在当前步，不启动Python或开放写入。
3. **启动Python**：使用P段已上传、摘要已核对、已按锁文件同步依赖的Python运行目录，复核独立解释器与venv；不在停写窗口下载或同步依赖，不从开发机复制venv。用户终端配置EnvironmentFile，内部令牌与Java一致。部署模板为1个uvicorn worker、127.0.0.1:8000，健康返回`{"ready":true}`。检查active、端口绑定及日志；失败保留Java worker关闭。
4. **前端与Nginx**：切换到P段已上传并核验的同批dist，复核版本与摘要，保留旧目录供回退。把`nginx/site.conf.example`合入B段确认的HTTPS站点，保留证书和HTTP跳转。`nginx -t`通过后，再单独授权reload。验证`/review`、`/profile/reports`、`/admin/reports`刷新能到SPA；`/internal/`、`/api/internal/`及其子路径均404。API去掉`/api`前缀；Nginx30秒等待大于前端社区20秒，超时不得自动重发（帖子可能已保存）。失败不开放业务入口，回退Nginx也需授权。
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

冒烟专用账号若使用只在服务器内存中的随机凭据，事后无法再登录复用；冒烟结束时就决定保留或清理，清理属于生产写入，单独授权。接口与静态history检查不能代替已认证浏览器UI验收。

`check/smoke.py`只执行GET：Java/Python健康、匿名401、普通用户权限与读取、后台列表、Redis状态、可选SPA/history与内部路径封闭。配置名：`SMOKE_BACKEND_URL`、`SMOKE_PYTHON_URL`、`SMOKE_FRONTEND_URL`、`SMOKE_USER_TOKEN`、`SMOKE_ADMIN_TOKEN`；值仅用户终端提供，脚本不打印响应原文或令牌、不跟随重定向、不读取宿主HTTP代理。未提供用户/管理员令牌时结果会标记对应检查未做，不能声称完整验收。实际API返回`rateLimitBackend=redis`，前端显示“Redis”。

经用户逐项明确同意后，用测试账号：登录→保存关闭AI心情→保存开启AI心情并等待→查看回顾页→关闭AI发帖→打开举报入口→核对后台概览和所有列表；同时在浏览器验证页面、权限、刷新与轮询终态。**事先报本次真实模型调用总数为1次**（只生成一次普通合成心情回复，不触发回顾生成/社区AI）；额外重试也算新调用，必须重新说明且总数不超过3次。只读脚本不包含任何生成入口。

停写窗口内冒烟通过并经授权恢复服务后，再连续观察30分钟（不计入30～45分钟停写窗口），检查Java/Python/Nginx错误分类、后台失败数、队列状态和进程；诊断只记录分类/数量/时间，不抄用户正文、JWT、prompt、原始供应商错误或含凭据配置。不要把单次成功称为质量测评、性能保证或30分钟稳定性证据。测试数据保留/删除由用户决定，删除必须单独授权。

## 回退

先报告触发原因、准确版本、会丢失/停用的功能和是否涉及数据，再取得回退授权。顺序：停止接收新写入/新AI任务→停worker并确认退出→停新应用→前后端配套回退→按旧契约回查。新增列/表保留，queued/running及新报告不能假装已由旧版本处理；旧同步超时/公开回复旧问题及旧JWT漏洞会重现。Python/Redis停止或配置改动都另需授权。

通常不删表、不缩模型列。只有备份验证及核查证实数据损坏/误初始化等情况才提出恢复备份方案；恢复前再次停写，明确备份之后数据的损失边界、第二位置副本与恢复验证，用户明确同意后才实施。不能自动执行恢复或不安全的旧后端回退。
