# 项目文档入口

阶段 1a（异步 AI 回应）与阶段 2（近期回顾与周报）已于 2026-11-18 收口并上线。以下是现行资料，按问题选读：

| 内容 | 入口 |
| --- | --- |
| 项目定位、功能、本机运行、接口概览 | [仓库说明](../../README.md) |
| 架构与模块 | [架构地图](../../ops/01-architecture-map.md) · [模块笔记](../../ops/04-module-notes.md) |
| 功能与接口约定 | [1a AI 回应](../../ops/features/ai-reply-service-v1a.md) · [阶段2回顾](../../ops/features/mood-review.md) · [举报与分页](../../ops/features/community-report.md) · [Redis 限流](../../ops/features/redis-rate-limit.md) |
| 数据库 | [完整初始化](../../sql/xinyu.sql) · [增量迁移](../../sql/migrations/) · [迁移核查单](../../deploy/MIGRATIONS.md) |
| 开发与测试、CI | [开发与测试](../../ops/03-dev-and-test.md) · [持续集成](../../ops/02-agent-workflow.md#持续集成) |
| 测评口径与结果 | [测评计划](../../ops/features/xinyu-evaluation-plan.md) · [本机性能测评](../../perf/README.md) |
| 部署上线 | [部署与运行说明](部署与运行说明.md) |
| 当前进度与下一步 | [接续状态](../../ops/features/xinyu-next-session.md) |

1a 升级前的 01～11 交付文档（同步 AI 版本）已移到[历史目录](../history/project-before-1a/README.md)，仅作演变参考；现行行为以上表资料和源码为准。
