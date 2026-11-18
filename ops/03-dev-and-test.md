# 03 开发与测试

更新：2026-11-18。本机启动步骤与环境变量见根 [README](../README.md#本机运行)；CI 见 [ops/02 持续集成](02-agent-workflow.md#持续集成)。

## 本地依赖

- 后端：JDK 17、Maven（或 `xinyu-backend/mvnw`）、MySQL 8；Redis 可选（未配置时限流用本机计数）。
- AI 服务：Python 3.12、uv。
- 前端：Node 20.19+／22.12+（CI 用 22）、npm。
- 数据库：空库用 `sql/xinyu.sql` 初始化（会 DROP 表）；已有数据的库按 `sql/migrations/` 逐份升级。

## 常用命令

| 范围 | 目录 | 命令 |
| --- | --- | --- |
| 后端单元与契约 | `xinyu-backend` | `sh mvnw test`（`*Test`／`*Tests`） |
| 后端集成测试 | 仓库根 | `.github/scripts/run-backend-its.sh`（每个 `*IT` 类新建空的 `xinyu_test_ci_` 库；需本机 MySQL、`xinyu-ai` 已 `uv sync`，设 `AI_TEST_REDIS_URL` 时含 Redis 限流测试） |
| 单个集成测试 | `xinyu-backend` | `AI_TEST_JDBC_URL=jdbc:mysql://127.0.0.1:3306/<空的 xinyu_test_ 库> sh mvnw -Dtest=<类名> test` |
| AI 服务 | `xinyu-ai` | `uv sync --locked`；`uv run --locked pytest -q` |
| 前端 | `xinyu-vue` | `npm run format:check`、`npm run check:lint`、`npm test`、`npm run build` |

- `npm run lint` 与 `npm run format` 会**直接修改文件**，只在有意修复时使用；自查与 CI 用 `check:lint`、`format:check`。
- 集成测试只连 `xinyu_test_` 前缀的空库；`xinyu_test_community_report` 是本机固定库，跑完复原为空库。
- 测试默认用合成上游，不调用真实模型；真实模型调用须有明确授权。

## 手动自测主链路

- 注册、登录，刷新后登录态仍在；禁用账号的请求返回 403。
- 新增开启 AI 的心情：保存立即返回，状态从排队到成功；关闭 AI 的记录不建任务；`AI_WORKER_ENABLED=false` 时任务停在排队。
- 修改或删除记录后，旧任务结果不覆盖新内容；失败可重试，冷却期内提示等待。
- 回顾页：周统计与近 30 天、生成信、来源修改后显示需更新。
- 社区：分页、点赞收藏、发帖与回复、举报；后台裁决后作者看到隐藏原因。
- 后台：概览、用户、帖子、回复、话题、举报、AI 日志。
- 移动端 375px 宽度下可操作。

## 注意事项

- 不在文档或代码中写真实密码、密钥、服务器地址。
- 不直接执行清库、删表、批量删除文件等命令。
- 测试结论写明“已执行什么命令、结果数量”和“没有执行什么”。
