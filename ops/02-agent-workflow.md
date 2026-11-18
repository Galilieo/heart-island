# 02 Agent 工作流程

授权与隐私边界统一见 [AGENTS.md](../AGENTS.md#工作边界以本节为准)；学习、测试与独立评审统一见 [协作约定](11-learning-collaboration.md)。本文只维护任务步骤。

## 默认步骤

1. 确定本轮目标与任务类型，核对 Git 改动；已有工作从接续入口定位，陌生模块查架构地图。
2. 先搜索标题／符号，再读取相关契约、源码、SQL、配置及测试；按需要扩展调用链，不默认扫描历史材料。
3. 新功能按 [功能开发规则](06-feature-development.md)形成方案；bugfix 先复现／定位，说明根因、最小修复点与回归方式。业务修改按 AGENTS 的确认边界执行。
4. 按独立功能小步实现、验证；源码与测试适用 [可读性规范](12-code-readability.md)，学习任务执行协作约定。
5. 用中文先报结果，再给具体文件、验证与限制；标明待确认项。需要沉淀时更新已有契约／执行记录及接续状态。

## 持续集成

- **第一层**（`.github/workflows/ci.yml`，每次 push／PR）：前端格式检查、只检查不修改的 lint、单元测试与构建；Python `pytest`；Java `mvnw test`（`*Test`／`*Tests`，不含 `*IT`）。
- **第二层**（`.github/workflows/integration.yml`，push 到 main 或手动）：MySQL 8.0 与 Redis 6.0 一次性容器，按 `.github/scripts/run-backend-its.sh` 为每个 `*IT` 类新建空库逐类运行；某类全部跳过也算失败。超时评估 `MoodReviewTimeoutBenchmarkIT` 不在 CI 范围。
- 本机复现第二层：设好 `AI_TEST_DB_USER`／`AI_TEST_DB_PASSWORD`（必要时 `AI_TEST_REDIS_URL`）、在 `xinyu-ai` 执行 `uv sync --frozen` 后运行同一脚本；脚本只建删 `xinyu_test_ci_` 前缀库。
- CI 不放真实模型密钥、不部署；推送后两层未通过时先修复或说明原因，不把红灯当作已验收。CI 结果不替代本轮要求的浏览器或生产验收。

## 读取与维护

不以“先读文档”为由整份输出长材料；同一会话未变化的内容复用已有上下文。测试成功看摘要，失败定位相关完整错误及上下文，避免截掉根因。确认、带读、评审等规则只在权威文档修改，本文不另维护副本。
