# history 历史资料导航

本目录保存历史设计规格、实施计划和开发过程资料，用来理解项目演进。这里的内容不保证仍然代表当前实现。

## 当前内容

```text
history/
└─ superpowers/
   ├─ plans/   历史实施计划
   └─ specs/   历史设计规格
```

## 文件说明

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
