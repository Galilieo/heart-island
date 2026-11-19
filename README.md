# 心屿 AI 情绪记录与匿名互助社区系统

[![CI](https://github.com/Galilieo/heart-island/actions/workflows/ci.yml/badge.svg)](https://github.com/Galilieo/heart-island/actions/workflows/ci.yml)
[![Integration](https://github.com/Galilieo/heart-island/actions/workflows/integration.yml/badge.svg)](https://github.com/Galilieo/heart-island/actions/workflows/integration.yml)

心屿围绕“情绪记录、AI 陪伴回应、近期回顾、匿名互助社区、后台内容治理”构建。记录先保存、AI 回应在后台异步生成；统计由程序计算，AI 只负责文字解释；社区支持匿名发帖、举报与后台裁决。

当前已上线的能力对应升级总方案的阶段 1a、阶段 2 与阶段 1b 的 Redis 限流部分；阶段 3 起（多轮对话、陪伴聊天、记忆与知识库）尚未开始，见[升级总方案](ops/features/xinyu-upgrade-master-plan.md)。

## 架构

```text
浏览器（Vue 3）
   │  /api
   ▼
Java 后端（Spring Boot，JDK 17）──► MySQL 8：用户、记录、任务表 ai_job、回顾、社区、举报
   │  鉴权／业务／限流／任务领取       Redis：付费 AI 调用的时间窗口计数（不可用时降级为本机计数）
   │
   ├─ 心情回应、近期回顾：单 worker 领取 ai_job ──HTTP（内部令牌）──► Python AI 服务（FastAPI）──► 模型供应商
   │                                                               提示词、危机规则、输出校验
   └─ 社区帖子回应：发帖请求内同步调用模型（未经 Python 服务）
```

- Java 持有用户身份、来源版本、任务状态与结果持久化；Python 只做内部认证、输入校验、危机规则分支和一次模型生成。
- 任务表做持久队列：领取用 `FOR UPDATE SKIP LOCKED` 加条件更新，结果按来源版本提交，修改或删除后的迟到结果不会写回。

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot 3、MyBatis-Plus、MySQL 8、Redis、JWT、BCrypt、Apache HttpClient 5 |
| AI 服务 | Python 3.12、FastAPI、httpx、uv；DeepSeek（OpenAI 兼容接口） |
| 前端 | Vue 3、Vite、Pinia、Vue Router、axios；自建组件库，无第三方 UI 库 |
| 工程 | GitHub Actions 两层 CI、Prettier／oxlint／ESLint、pytest、JUnit 5 |
| 部署 | Linux、Nginx、systemd（运行手册见 `deploy/`） |

## 已完成功能

### 心情记录与 AI 回应

- 记录增删改查、分页筛选、近 7 天趋势；单条正文上限 1000 字。
- 每条记录可单独开关 AI 回应。保存立即返回，AI 回应进入任务队列，前端轮询状态（排队、生成中、成功、失败、已关闭）。
- 失败如实标记，不自动重试付费；用户可手动重试，多次失败后进入冷却。修改或删除记录后，旧任务的迟到结果不会覆盖新内容。
- 危机规则命中时不调用模型，直接返回固定的支持文案。
- 服务重启时，执行中的任务标记为中断，排队中的任务继续处理。

### 近期回顾与周报

- 按自然周（周一起，`Asia/Shanghai`）与近 30 天统计记录次数、记录天数与情绪类型分布；首页另有近 7 天趋势。
- 按需生成 AI 文字回顾与周报，信件中的引用指向具体记录；可查看历史，来源记录变化后提示需更新，显式重新生成时旧版本保留到新版本成功。
- 统计数字由程序渲染，模型不能改写统计字段。

### 匿名社区

- 话题、帖子与回复的发布、修改、删除；最新／最热排序；分页列表（每页默认 10 条）。
- 点赞、收藏、收藏列表；帖子可选 AI 回应。
- 社区接口不返回作者 ID，只返回 `mine` 标记本人内容。
- 举报帖子或回复，查看“我的举报”；被管理员隐藏的内容仅作者可见并显示原因。

### 后台管理（全站 ADMIN）

- 概览：用户、帖子与回复（含隐藏数）、AI 调用成功与失败、限流后端与当日拒绝次数；举报页显示待处理数。
- 用户、帖子、回复、话题管理；举报按对象聚合处理（隐藏或驳回），并发裁决加锁。
- AI 日志：可跨用户查看与搜索已保存的 prompt、回复和错误详情，用于排障；隐私页已告知。

### 限流与稳定性

- Redis 时间窗口额度：每人每小时／每日心情回应、每小时社区回应、每日回顾，以及全站每小时上限；超额返回 429 与 `Retry-After`。
- Redis 故障时自动降级为本机计数并定期重试恢复；单人在途任务数与全站排队容量另有上限。

### 用户与隐私

- 注册、登录、JWT 鉴权（签名密钥必须由环境变量提供）、BCrypt 密码、个人资料（昵称选填）、修改密码。
- 隐私政策页：说明 AI 外发范围、管理员排障权限、匿名与删除保留规则。

## 项目结构

```text
heart-island
├─ xinyu-backend     Spring Boot 后端（controller / service / mapper / entity / dto / vo / ratelimit）
├─ xinyu-ai          Python AI 服务（app/ 运行代码，tests/ 测试，evals/ 离线测评集与脚本）
├─ xinyu-vue         Vue 3 前端（api / stores / views / components / composables / utils，tests/ 单元测试）
├─ sql               完整初始化 xinyu.sql 与 migrations/ 增量迁移
├─ deploy            上线运行手册、迁移核查单、环境变量与 systemd／Nginx／Redis 模板
├─ perf              本机受控性能与正确性测评脚本及结果
├─ test-contracts    Java 与 Python 共用的契约数据（如心情类型）
├─ ops               Agent 工作规则与 features/ 功能契约、总方案、接续状态
├─ docs              项目资料入口与历史归档
└─ .github           两层 CI 工作流与集成测试脚本
```

## 本机运行

需要：JDK 17、Maven（或 `xinyu-backend/mvnw`）、Node 20.19+／22.12+、Python 3.12 与 uv、MySQL 8、Redis（可选）。

### 1. 数据库

```sql
CREATE DATABASE xinyu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

然后导入 `sql/xinyu.sql`。**该脚本会先 DROP 再建表，只能用于空库或可丢弃的本地库**；已有数据的库请按 `sql/migrations/` 与 [迁移核查单](deploy/MIGRATIONS.md) 逐份升级。

### 2. Python AI 服务

```bash
cd xinyu-ai
uv sync --locked
```

服务只从进程环境读取配置（变量说明见 [`xinyu-ai/README.md`](xinyu-ai/README.md)，模板见 `deploy/env/python.env.example`）：`AI_INTERNAL_TOKEN`、`OPENAI_BASE_URL`、`OPENAI_API_KEY`、`OPENAI_MODEL`、`DEEPSEEK_THINKING_MODE`。设置后启动：

```bash
uv run --locked uvicorn app.main:app_from_env --factory --host 127.0.0.1 --port 8000 --no-access-log
```

只监听回环地址，不要暴露到公网。`GET /health` 返回 `{"ready":true}` 表示配置校验通过。

### 3. Java 后端

至少设置以下环境变量（完整列表与说明见 `deploy/env/backend.env.example`）：

| 变量 | 说明 |
| --- | --- |
| `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` | 数据库连接 |
| `JWT_SECRET` | 必填，至少 32 字节的随机值；缺失或过短时拒绝启动 |
| `AI_WORKER_ENABLED` | `true` 才会领取并执行心情／回顾任务；`false` 时记录照常保存，任务停在排队 |
| `AI_SERVICE_BASE_URL`、`AI_INTERNAL_TOKEN` | 所有 Java 实例必填；令牌与 Python 服务一致 |
| `AI_COMMUNITY_CONCURRENCY` | 社区同步生成独立名额，Java 与 Python 默认各 3 |
| `REDIS_URL` | 可选；为空时限流使用本机内存计数 |

```bash
cd xinyu-backend
sh mvnw spring-boot:run
```

默认地址 `http://localhost:8080`，健康检查 `GET /hello`。

### 4. 前端

```bash
cd xinyu-vue
npm install
echo 'VITE_API_BASE_URL=http://localhost:8080' > .env.development.local
npm run dev
```

默认地址 `http://localhost:5173`。

## 测试与 CI

| 范围 | 命令 |
| --- | --- |
| 前端 | `npm run format:check`、`npm run check:lint`、`npm test`、`npm run build`（在 `xinyu-vue/`） |
| Python | `uv run --locked pytest -q`（在 `xinyu-ai/`） |
| Java 单元与契约 | `sh mvnw test`（在 `xinyu-backend/`，运行 `*Test`／`*Tests`） |
| Java 集成测试 | `.github/scripts/run-backend-its.sh`：每个 `*IT` 类新建一个空的 `xinyu_test_ci_` 库逐类运行，需要本机 MySQL、`xinyu-ai` 已 `uv sync`，设置 `AI_TEST_REDIS_URL` 时包含 Redis 限流测试 |

GitHub Actions 分两层：每次 push／PR 运行三端快速检查；推送到 `main` 时额外用 MySQL 8.0 与 Redis 6.0 容器运行全部集成测试。CI 不使用真实模型密钥，也不自动部署。说明见 [ops/02 持续集成](ops/02-agent-workflow.md#持续集成)。

`perf/` 下是本机受控的性能与正确性测评（合成上游与隔离库），结果不代表线上性能，见 [`perf/README.md`](perf/README.md)。

## 接口概览

除注明“公开”外均需请求头 `token`；后台接口要求 ADMIN。

| 模块 | 方法与路径 | 说明 |
| --- | --- | --- |
| 用户 | `POST /user/register`、`POST /user/login` | 注册、登录（公开） |
| 用户 | `POST /user/update-profile`、`POST /user/update-password`、`GET /user/profile-stats` | 资料、密码、个人页统计 |
| 心情 | `POST /mood/add`、`PUT /mood/update/{id}`、`DELETE /mood/delete/{id}` | 保存、修改、删除；开启 AI 时返回任务信息 |
| 心情 | `GET /mood/list`、`GET /mood/detail/{id}`、`GET /mood/trend/recent7` | 分页、详情（含 AI 状态）、近 7 天趋势 |
| AI 任务 | `GET /mood/ai-job/{id}`、`POST /mood/ai-job/{id}/retry` | 查询任务状态、手动重试 |
| 回顾 | `GET /mood/analysis?range=30d`、`POST /mood/analysis/generate` | 近 30 天统计与回顾、生成 |
| 周报 | `GET /mood/report/week`、`POST /mood/report/week/generate`、`POST /mood/report/week/regenerate`、`GET /mood/report/week/history` | 周统计与周报、生成、重新生成、历史 |
| 社区 | `GET /community/post/list`、`GET /community/post/detail`、`GET /community/reply/list` | 公开读取；`mine=true` 需登录 |
| 社区 | `POST /community/post/add`、`/update`、`/delete` | 帖子发布、修改、删除 |
| 社区 | `POST /community/post/like`、`/unlike`、`/favorite`、`/unfavorite`；`GET /community/post/favorites` | 互动与收藏列表 |
| 社区 | `POST /community/reply/add`、`/update`、`/delete` | 回复 |
| 举报 | `POST /community/report`、`GET /community/report/mine`、`GET /community/report/reported-targets` | 举报、我的举报、已举报对象 |
| 话题 | `GET /topic/list` | 话题列表 |
| 后台 | `GET /admin/overview` | 概览 |
| 后台 | `GET /admin/user/page`、`PUT /admin/user/{id}/status` | 用户管理 |
| 后台 | `GET /admin/post/page`、`GET /admin/post/{id}`、`PUT /admin/post/{id}/status`、`DELETE /admin/post/{id}` | 帖子管理；`/admin/reply/...` 同构 |
| 后台 | `GET /admin/report/page`、`GET /admin/report/detail`、`POST /admin/report/handle`、`GET /admin/report/pending-count` | 举报处理 |
| 后台 | `GET /admin/topic/list`、`POST /admin/topic`、`PUT /admin/topic/{id}`、`PUT /admin/topic/{id}/status` | 话题管理 |
| 后台 | `GET /admin/ai-replies/page` | AI 日志 |

字段、状态与错误码以功能契约为准：[1a AI 回应](ops/features/ai-reply-service-v1a.md)、[阶段2回顾](ops/features/mood-review.md)、[社区举报与分页](ops/features/community-report.md)、[Redis 限流](ops/features/redis-rate-limit.md)。Python 内部接口见 [`xinyu-ai/README.md`](xinyu-ai/README.md)。

## 文档入口

| 你要做什么 | 入口 |
| --- | --- |
| 继续开发 | [功能首页](ops/features/README.md) → [接续状态](ops/features/xinyu-next-session.md) |
| 查阶段规划与边界 | [升级总方案](ops/features/xinyu-upgrade-master-plan.md) |
| 查测评口径与结果 | [测评计划](ops/features/xinyu-evaluation-plan.md) |
| 部署上线 | [上线运行手册](deploy/README.md)、[迁移核查单](deploy/MIGRATIONS.md) |
| 查 Agent 工作规则 | [AGENTS.md](AGENTS.md) 与 `ops/` |
| 查项目资料与历史方案 | [docs/README.md](docs/README.md)、[历史归档](docs/history/README.md) |

`AI_WORKLOG/` 为本地工作记录，大部分未纳入 Git。

## 后续规划

按[升级总方案](ops/features/xinyu-upgrade-master-plan.md)推进，以下均**尚未实现**：

- 阶段 1b 余项：危机回应增强（命中后结合原文生成支持性回应，失败退回固定文案；已决定在阶段 3 前或线上出现误判时排期）、启用多实例前的任务租约。
- 阶段 3：围绕记录继续聊（多轮对话与历史）。
- 阶段 4：独立陪伴聊天、流式输出与受控工具调用。
- 阶段 5：用户可管理的记忆与有来源的知识库。
- 阶段 6：自动周报、提醒与成本面板等。
