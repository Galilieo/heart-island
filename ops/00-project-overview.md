# 00 项目总览

更新：2026-11-18（阶段 1a 与阶段 2 收口后）。

## 基本信息

- 项目名：heart-island / 心屿。
- 定位：AI 情绪记录与匿名互助社区系统。
- 技术栈：Spring Boot 3（JDK 17）+ MyBatis-Plus + MySQL 8 + Redis + JWT；Python 3.12 FastAPI AI 服务；Vue 3 + Vite + Pinia。
- 主要目录：`xinyu-backend/`、`xinyu-ai/`、`xinyu-vue/`、`sql/`、`deploy/`、`perf/`、`test-contracts/`、`ops/`、`docs/`、`.github/`。

## 已有能力（已上线）

- 用户注册、登录、JWT 鉴权（签名密钥必须由环境变量提供）、个人资料（昵称选填）。
- 心情记录增删改查与筛选；每条可单独开关 AI 回应。保存立即返回，AI 回应走 `ai_job` 任务队列异步生成，前端轮询状态，失败可手动重试并有冷却；危机规则命中时返回固定支持文案、不调用模型。
- 近期回顾与周报：Java 统计，按需生成小芽的信，可回看历史，来源变化后过期。
- 匿名社区：帖子、回复、话题、点赞、收藏、分页列表；举报与后台聚合裁决；社区接口不返回作者 ID。
- 后台：用户、帖子、回复、话题、举报、AI 日志（全站 ADMIN 可查看已存 prompt／回复／错误）、概览统计。
- Redis 时间窗口限流（故障时降级为本机计数）。

具体字段与行为见 `ops/features/` 下的功能契约。

## 当前权威资料

- 数据库结构：`sql/xinyu.sql`（完整初始化）与 `sql/migrations/`（增量）。
- 后端行为：`xinyu-backend/src/main/java/com/xinyu/`；AI 服务：`xinyu-ai/app/`；前端：`xinyu-vue/src/`。
- 功能契约与阶段边界：`ops/features/`；当前进度：`ops/features/xinyu-next-session.md`。
- 部署：`deploy/README.md`、`deploy/MIGRATIONS.md`。

## 后续升级方向

按 `ops/features/xinyu-upgrade-master-plan.md` 分阶段推进，均未实现：1b 危机回应增强与多实例租约、阶段 3 围绕记录继续聊、阶段 4 独立陪伴与流式／受控工具、阶段 5 可管理记忆与知识库、阶段 6 自动化与成本面板。

## Agent 使用原则

工作边界与资料归属统一见 [AGENTS.md](../AGENTS.md)，新功能方案见 [ops/06](06-feature-development.md)。本页只提供项目背景，不作为每次任务必读项。
