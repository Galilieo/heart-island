# A 段：环境与基线冻结记录（2026-11-17）

> 由 `perf/` 脚本自动采集与人工核对混合生成；本文件是报告第 A 节的证据源。
> 重新采集：`uv run --directory xinyu-ai --locked python ../perf/collect_env.py`

## 被测版本

| 侧 | 版本 | 来源 |
| --- | --- | --- |
| 新版 Java | `eafc7cc156d02c7e27ef00466305be160e98a393`（main，任务书开工时 HEAD） | 本仓 `xinyu-backend/target/xinyu-backend-0.0.1-SNAPSHOT.jar`（-DskipTests package） |
| 旧版 Java（同步链路） | `2036e7af11be3c8b099679fd7ca04afd7f7b0b2f`（2c5d9fd 的父提交，测评计划 11.3 锁定） | `git worktree` 检出于 `/tmp/xinyu-eval-old-2036e7af`，同参数构建 |
| Python 服务 | 与新版同仓同提交（xinyu-ai，uv.lock 锁定依赖） | `uv run --directory xinyu-ai --locked` |

建库 SQL 一律取 `git show <commit>:sql/...`（不受并行上线未提交改动影响）；
新版=基线 `sql/xinyu.sql` + 按名序迁移（20261111_01、20261113_01 已并入基线，自动跳过）；
旧版=旧提交自带 `sql/xinyu.sql`。灌库前剥离 `CREATE DATABASE`/`USE` 行（见 perf/lib.py 事故防护注释）。

## 本机环境

- 机型/芯片：Apple M5（arm64），10 核，24 GB 内存
- 系统：macOS 27.2（Build 26B123）
- JDK：OpenJDK 21.0.12.1 (Temurin-21.0.12.1+1)
- Python：3.13.15（uv 管理，xinyu-ai 锁定依赖：fastapi/pydantic/httpx/uvicorn）
- MySQL：8.4.11（Homebrew，本机 127.0.0.1:3306）
- Redis：Homebrew redis-server（B4 段临时起在 127.0.0.1:6390，独立库号 0，前缀 xinyu:test:）

## 端口（任务书固定）

| 端口 | 用途 |
| --- | --- |
| 18080 | 新版 Java |
| 18081 | 旧版 Java |
| 18000 | Python 生成服务（uvicorn） |
| 18100 | 合成上游（perf/fake_upstream/app.py） |

## 关键配置（两侧对齐口径）

| 配置 | 新版 | 旧版 |
| --- | --- | --- |
| 上游地址 | Python `OPENAI_BASE_URL=http://127.0.0.1:18100` → 合成上游 | `DEEPSEEK_BASE_URL=http://127.0.0.1:18100` 直连合成上游 |
| 执行并发 | Java `AI_WORKER_CONCURRENCY=3`（3 槽）+ Python `AI_SERVICE_CONCURRENCY=3`（门禁 3） | 无并发上限（每请求一个 Tomcat 线程同步调用） |
| 任务扫描 | 每 1s（AiJobRuntimeConfig.SCAN_INTERVAL） | 无 |
| 上游超时 | Python 连接 2s/读 35s/总 ≤40s；Java 客户端连接 2s/读 45s/总 50s | RestTemplate 无超时（默认无限等待）——如实记录 |
| 限流 | 默认 mood 20/h、100/d；global 1000/h（B3 单独调高并在该段报告注明） | 无限流 |
| 排队上限 | 全局 queued 100 / 每用户 queued+running 3（AiJobService 常量） | 无 |
| 合成上游延迟 | 默认 1300ms 固定（参照 2026-11-11 真实社区采样中位 1285ms），抖动默认 0 | 同 |
| 数据库连接池 | Hikari（connection-timeout 2000ms、validation 1000ms、JDBC connectTimeout 2000/socketTimeout 6000；池大小默认 10） | Hikari 默认（无上述收紧） |

## 隔离库

- 全部库名以 `xinyu_test_eval_` 开头；连接配置来自 `~/.zshenv`（AI_TEST_*）。
- 每个测评项独立建库，用完删除；业务库 `xinyu` 不在本任务任何写路径上（事故修复后以 12 表守卫核对）。

## 合成数据规则（固定随机种子=20261117）

- 用户：`eval_<item>_u<k>`（k=01…），密码 `Passw0rd!123`，昵称=用户名；全部经注册接口创建。
- 心情内容：模板池（平静/疲惫/焦虑等 13 类各若干句，非危机文本）+ 序号后缀，保证唯一、可追溯、复跑一致；长度落在「一般长度」档（>15 字）。
- SQL 计量用历史数据：按种子生成的 (mood_type, content, create_time) 分布直接 INSERT，规则见 `perf/gen_data.py` docstring。
