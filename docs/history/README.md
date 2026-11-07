# history 历史资料导航

本目录保存历史设计规格、实施计划和开发过程资料，用来理解项目演进。这里的内容不保证仍然代表当前实现。

## 当前内容

```text
history/
├─ feature-plans/  已被替代的功能草稿、旧路线与评审摘要
└─ superpowers/
   ├─ plans/   历史实施计划
   └─ specs/   历史设计规格
```

## 文件说明

### 功能规划归档（2026-11-07）

这批材料从 `ops/` 移入，保留归档前正文，仅修正路径引用。它们可能仍含“待评审”“尚未实施”等当时表述，不作为当前状态。当前开发以[功能入口](../../ops/features/README.md)、[总方案](../../ops/features/xinyu-upgrade-master-plan.md)与[1a 契约](../../ops/features/ai-reply-service-v1a.md)为准。

| 文件 | 归档原因 |
| --- | --- |
| [AI 多轮对话 v1](feature-plans/ai-chat-session-v1.md) | 早期草稿；职责与阶段已由总方案覆盖，后续开工再细化 |
| [轻量长期记忆 v1](feature-plans/mood-memory-v1.md) | 同上，属于后续阶段 |
| [情绪周报 v1](feature-plans/mood-report-v1.md) | 早期接口／流程草稿，阶段 2 开工前须按总方案重新核对 |
| [RAG 知识库 v1](feature-plans/rag-knowledge-base-v1.md) | 早期草稿，尚未实施 |
| [工具调用 v1](feature-plans/tool-calling-v1.md) | 早期草稿，尚未实施 |
| [AI 回复可选化 v1](feature-plans/ai-reply-optional-v1.md) | 旧功能方案；当前源码已有开关，异步升级以 1a 契约为准 |
| [原升级一页摘要](feature-plans/xinyu-upgrade-summary.md) | 评审摘要已并入功能入口，不再单独维护当前状态 |
| [1a 前的旧升级路线](feature-plans/agent-rag-roadmap-before-1a.md) | 旧主题顺序已被总方案的分期替代 |

### 更早的设计与计划

| 文件 | 类型 | 用途 |
| --- | --- | --- |
| `superpowers/plans/2026-05-22-admin-foundation.md` | 历史计划 | 后台基础能力开发计划 |
| `superpowers/plans/2026-05-23-post-ai-reply.md` | 历史计划 | 社区帖子 AI 回复相关计划 |
| `superpowers/specs/2026-05-19-frontend-redesign-design.md` | 历史规格 | 前端重设计方案 |
| `superpowers/specs/2026-05-23-post-ai-reply-design.md` | 历史规格 | 帖子 AI 回复设计方案 |

## 使用规则

- 查“为什么当时这么做”时可以看这里。
- 查“现在是否已经实现”时，必须回到源码、SQL 和 `docs/project/` 核对。
- 不建议直接改旧历史文件；如果需要补充，优先新增说明或在本 README 标注。
- 历史文件里可能有旧路径、旧命令或已调整的计划，不要直接复制执行。
