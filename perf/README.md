# perf/ —— 本机受控性能与正确性测评（2026-11-17 首轮）

任务书：`AI_WORKLOG/plans/2026-11-17-evaluation-plan.md`；口径规则：`ops/features/xinyu-evaluation-plan.md` 第 10 节。
所有脚本只用 Python 标准库 + xinyu-ai 已有依赖，统一从仓库根目录以
`uv run --directory xinyu-ai --locked python ../perf/<script>.py` 执行（cwd 会被切到 xinyu-ai，
脚本内已按绝对路径处理）。

## 端口与组件

| 端口 | 组件 |
| --- | --- |
| 18100 | 合成上游 `fake_upstream/app.py`（OpenAI 兼容 `/chat/completions`） |
| 18000 | xinyu-ai Python 服务（uvicorn，指向 18100） |
| 18080 | 新版 Java（HEAD 构建，经 18000 调上游，worker 3 槽） |
| 18081 | 旧版 Java（`2036e7af`，直连 18100，同步链路） |

合成上游控制：`GET /_state`、`POST /_control {"mode":...}`、`POST /_release`；
逐请求故障用内容标记 `#FAULT:<mode>#`（空正文 empty_content / 纯空白 blank_content /
截断 truncated_json / 非法 invalid_json / http_500 / http_429 / 读超时 read_timeout /
挂起 hold / 慢速正文 slow_body）。日志：`perf/results/fake_upstream.log`（JSONL）。

## 复跑顺序

```bash
# 0) 旧版 worktree（A 段，一次即可）
git worktree add --detach /tmp/xinyu-eval-old-2036e7af 2036e7af11be3c8b099679fd7ca04afd7f7b0b2f
(cd /tmp/xinyu-eval-old-2036e7af/xinyu-backend && sh ./mvnw -q -DskipTests package)
(cd xinyu-backend && sh ./mvnw -q -DskipTests package)

# 1) B1 保存延迟对照（先建库并启动服务，见 services.py；脚本内含建库）
uv run --directory xinyu-ai --locked python - <<'PY'
import sys; sys.path.insert(0, 'perf')   # 注意 uv 会把 cwd 切到 xinyu-ai，用绝对路径更稳
PY

# 实际执行每个分项前，用 perf/services.py 的 start_* 函数把四个服务指向对应隔离库
uv run --directory xinyu-ai --locked python ../perf/run_b1.py
uv run --directory xinyu-ai --locked python ../perf/run_b2.py
uv run --directory xinyu-ai --locked python ../perf/run_b3.py
uv run --directory xinyu-ai --locked python ../perf/run_b4_app.py
uv run --directory xinyu-ai --locked python ../perf/run_b5.py
# 分页优化复测只跑社区两个端点，沿用 B5 档位、种子、20 次延迟和 3 次 SQL 探测。
uv run --directory xinyu-ai --locked python ../perf/run_b5.py --community-paging
uv run --directory xinyu-ai --locked python ../perf/run_c1.py
uv run --directory xinyu-ai --locked python ../perf/run_c2.py
uv run --directory xinyu-ai --locked python ../perf/run_d_metrics.py   # 离线，不需要服务
```

B4 组件微基准（需要 xinyu-backend 已编译 + 依赖 classpath）：

```bash
cd xinyu-backend
sh ./mvnw -q dependency:build-classpath -Dmdep.outputFile=/tmp/xinyu-eval-cp.txt
redis-server --port 6390 --daemonize yes --save '' --appendonly no --pidfile /tmp/redis-eval-6390.pid
javac -cp "target/classes:$(cat /tmp/xinyu-eval-cp.txt)" ../perf/bench/RateLimitBench.java -d ../perf/bench/out
java -cp "../perf/bench/out:target/classes:$(cat /tmp/xinyu-eval-cp.txt)" \
     RateLimitBench target/classes/ratelimit/check-and-increment.lua
```

## 数据与合成规则

- `gen_data.py`：固定种子 20261117，生成心情历史与社区帖子/点赞/收藏的 INSERT 文件；
  规则见文件 docstring 与 `perf/env-baseline.md`。
- 隔离库一律 `xinyu_test_eval_*`，建库内容取 `git show <commit>:sql/...`（不受并行上线改动影响），
  并自动剥离 `CREATE DATABASE`/`USE` 行（防误写业务库——2026-11-17 事故教训，见报告“发现”）。
- 结果产物在 `perf/results/<分项>/`（jsonl + summary.json + run-meta.json），不提交。

## 分项 → 产物

| 分项 | 脚本 | 结果目录 |
| --- | --- | --- |
| B1 保存延迟对照 | run_b1.py | results/b1/ |
| B2 假成功率矩阵 | run_b2.py | results/b2/ |
| B3 三槽吞吐与排队 | run_b3.py | results/b3/ |
| B4 限流开销（组件/应用） | bench/RateLimitBench.java + run_b4_app.py | results/b4/ |
| B5 SQL 计量 | run_b5.py | results/b5/（含 explain/） |
| 社区列表分页复测 | run_b5.py --community-paging | results/b5-paging/ |
| C1 并发写入 | run_c1.py | results/c1/ |
| C2 重启恢复 | run_c2.py | results/c2/ |
| D 效果指标离线计算 | run_d_metrics.py | results/d/ |

分位数口径：最近邻秩（nearest-rank）；失败/超时一律单列，不剔分母。
