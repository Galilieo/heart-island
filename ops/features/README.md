# 心屿功能开发入口

继续开发先读[接续状态](xinyu-next-session.md)，再搜索当前主题的契约章节。下表是定位索引，不是全文必读清单；旧草稿与结束阶段流水不参与日常阅读。

## 按主题定位

| 文档 | 用途 |
| --- | --- |
| [接续状态](xinyu-next-session.md) | 做到哪一步、下一步怎么接、验证边界 |
| [升级总方案](xinyu-upgrade-master-plan.md) | 阶段顺序、Java／Python／MySQL 职责与长期边界；按需查阅 |
| [1a 功能契约](ai-reply-service-v1a.md) | 当前字段、内部／公共 API、状态、并发与失败规则 |
| [1a 实施计划](../../AI_WORKLOG/plans/2026-11-07-ai-reply-1a-plan.md) | Task 1—4 已实施；仅按当前问题核对对应 Task，不重新领取旧任务 |

当前进度统一看[接续状态](xinyu-next-session.md)，学习协作遵守[协作约定](../11-learning-collaboration.md)。

## 升级方向速览

近期主线是 **1a 单实例记录／异步回应 → 2 近期分析与周报**；1b Redis 与运行增强可穿插，多实例前补租约。聊天、流式、受控工具、可管理记忆与知识库随后按需交付。Java／MySQL 持有业务对象与任务权威，Python 处理 AI，Redis 提供辅助能力。

这里仅保留方向摘要，详细阶段与验收统一维护在总方案，避免多份文档分别更新。

## 需要时再查

- [阶段归档](../../AI_WORKLOG/archive/README.md)：已结束阶段接续与执行证据；[历史方案](../../docs/history/README.md)：早期草稿与旧路线。均只在追溯时读取，不直接执行。
- [参考方案与品牌素材](references/README.md)：用户提供的方案及当前唯一的小芽素材版本；待评估，未因归档自动进入实施。
- [新功能模板](feature-template.md)：只有开始一个需要独立契约的新功能时才使用，不按聊天轮次重复建方案。

维护规则：长篇交付设计在本轮升级接近完成后集中更新；日常进度更新接续状态，设计变更改总方案／当前契约，执行清单与验证写入原计划和 AI_WORKLOG；已被替代的草稿归档，不再增加“最新方案”“补充摘要”等并行入口。历史材料不得作为已实现或生产验收的证据。

- [社区举报与处理](community-report.md)：治理线 G1 举报、匿名脱敏（`mine` 替代 `userId`）、后台聚合裁决与状态契约；含社区列表分页。
- [Redis 共享限流](redis-rate-limit.md)：1b 时间窗口额度、原子检查、Redis 故障降级与 429 口径。
- [测评计划](xinyu-evaluation-plan.md)：L1～L4 指标、当前值与复跑口径；首轮报告见 `AI_WORKLOG/2026-11-17-evaluation-report.md`。
- [近期回顾与周报](mood-review.md)：阶段2统计口径、两表、六个接口、生成去重与限流、任务执行、过期与危机分支。
- [Redis 共享限流](redis-rate-limit.md)：付费 AI 调用按用户与全站的时间窗口额度，Lua 原子扣减，故障降级本机计数。
