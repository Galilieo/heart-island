# 心情回复合成测评（1a）

本目录是[测评规划](../../ops/features/xinyu-evaluation-plan.md)在「1a 心情回应」上的首次落地，
口径与执行步骤见[执行手册](../../AI_WORKLOG/plans/2026-11-11-ai-reply-prompt-v2-eval-plan.md)。

## 数据声明

- `mood_reply_set.jsonl` 中的全部正文为**执行者原创的合成数据**，口吻模仿真实随手记录，
  不含任何真实用户日记、数据库内容或日志正文。
- 结果产物（`AI_WORKLOG/evals/…/results.jsonl`、`summary.md`、`run-meta.json`）只包含
  合成输入、模型输出与非敏感运行信息，**不提交**，不写入密钥。

## 文件

| 文件 | 用途 |
| --- | --- |
| `mood_reply_set.jsonl` | 100 条合成测评集（normal 39 / short 8 / mismatch 8 / injection 8 / crisis_pos 15 / crisis_boundary 15 / crisis_other 7；繁体 13 条） |
| `prompts/mood-reply-v2.txt` | v2 候选提示词（阶段 2 直接使用，修改另存 v2a/v2b，不覆盖） |
| `checks.py` | 规则检查器，纯函数；定义与阈值来自手册 3.3，阶段 1 前冻结 |
| `run_eval.py` | 运行脚本：先过正则层（不调模型），未命中的逐条调用供应商 |
| `blind_sheet.py` | 从两份 results.jsonl 生成分层配对盲评表 blind.md 与 blind-key.json |
| `../tests/test_eval_checks.py` | 检查器单元测试 |

字段口径：`expect.should_support` 为该样本是否应得到安全回应（SUPPORT）；
`expect.positive_mood` 按**正文情感**标注（mismatch 类以内容为准，注入类一律 false），
供 `advice_on_positive` 等门槛使用。

## 运行方式

```bash
# 单元测试（含检查器测试）
uv run --directory xinyu-ai --locked pytest -q

# 离线验收（不访问网络、不调模型）
uv run --directory xinyu-ai --locked python evals/run_eval.py \
    --label dryrun --set evals/mood_reply_set.jsonl --dry-run

# 真实运行（需先在进程环境注入 Settings.from_env() 要求的全部变量；密钥不落盘）
uv run --directory xinyu-ai --locked python evals/run_eval.py \
    --label v1 --set evals/mood_reply_set.jsonl --limit 100
uv run --directory xinyu-ai --locked python evals/run_eval.py \
    --label v2 --set evals/mood_reply_set.jsonl --prompt-file evals/prompts/mood-reply-v2.txt --limit 100

# 盲评表（阶段 3；blind-key.json 在用户完成前不得展示）
uv run --directory xinyu-ai --locked python evals/blind_sheet.py \
    --a <v1目录>/results.jsonl --b <v2目录>/results.jsonl --out <目录> --seed 11
```

脚本在替换候选提示词前会逐字比对「# 安全」小节，不一致即拒绝运行（手册第 4 节）。
模型调用授权上限 400 次（用户 2026-11-11 授权，仅限本手册的合成测评），
单遍不超过 100 次；超出须停下重新请示。
