# 心屿升级：新会话接续入口

更新：2026-11-07。工作目录：`/Users/jiangqiuyu/pre/heartisland/heart-island`。

## 当前状态

- 已完成整体设计与1a计划的多轮评审修订。当前主线1a单实例心情记录/异步回应→2近期分析与周报；1b Redis与运行增强可穿插，聊天后续交付。
- 设计评审后已按用户授权实施 Task 1：新增独立 `xinyu-ai/`，包含内部认证、严格协议、有限风险回应与单次模型适配；RED/GREEN 和真实回环启动／超时断连已验证。独立复核发现的风险正则回溯与同步分支预算检查问题已补回归并修复，最终验证见本地执行记录。
- Task 2—4 的业务代码尚未开始；没有 Java 接入、迁移执行、社区超时配置修改、真实模型耗时采样或部署。Task 1 测试不等于 1a 产品验收。
- 学习状态：Task 1 主要业务调用链已带读并经过相关预测／纠错；用户澄清测试也要快速带读，只是不展开太复杂，学习重点仍是主体代码。测试由 Codex 编写／执行，讲清代表性输入、替换层、断言及证据，不记录为测试体系掌握或独立实现。固定方法见[学习协作约定](../11-learning-collaboration.md)。
- 保留Java业务后端+Python AI服务；用户对象/任务/结果权威在Java/MySQL，Redis辅助。不要把agent-lab应用整体接进产品。
- 这些文件已在本工作目录，尚未提交/推送；`AI_WORKLOG/`被仓库gitignore，仅在当前本地目录可读。换会话请使用同一现有目录，不假定新checkout/worktree已经带上这些本地文件。

## 阅读顺序

1. 根目录[AGENTS.md](../../AGENTS.md)，再按它要求读取相关ops规则。
2. 本页确认进度，再读[1a功能契约v0.6](ai-reply-service-v1a.md)和[1a实施计划](../../AI_WORKLOG/plans/2026-11-07-ai-reply-1a-plan.md)的当前 Task，不重做已完成任务。
3. 涉及阶段或职责取舍时按需查[总方案v0.3](xinyu-upgrade-master-plan.md)，不要求每次顺读所有材料。早期草稿和原一页摘要已移到[历史索引](../../docs/history/README.md)，不用于当前实施。

## 接续第一步

先核验当前Git改动与源码，不覆盖未提交内容。Task 1 的代码在 `xinyu-ai/`，运行方式与验证范围见其 README；执行证据见 [1a执行记录](../../AI_WORKLOG/2026-11-07-ai-reply-1a-progress.md)。依赖已锁定，回归命令为 `uv run --directory xinyu-ai --locked pytest -q`（真实回环测试需要允许本地端口）。Task 1 测试按用户要求快速带读代表性链路即可，不重做实现或扩展测试课程。

已带读 `main.py` 的启动组装、认证、准入计数／finally、`provider.py` 的模型适配、`contracts.py` 的继承／严格模式／字段和跨字段校验，以及简化后的 safety 和 Settings 环境读取。用户已参与相关预测；具体卡点按需补充，不重复盘问已解释内容。

当前接续点：用户明确否定地区资源／定期核验的维护成本，并授权简化 safety。已移除资源文件、地区／日期配置及启动门禁，`create_app(settings, provider=None)` 不再接收日期函数；`build_support_reply(content)` 命中后直接生成内置具体 SUPPORT，不输出电话号码或猜测地区。普通 SYSTEM_PROMPT 已同步取消资源配置假设。有限规则本身未扩大覆盖。

实现基线：72 项离线／回环测试通过；旧 8 项资源启动门禁测试由 2 项“无配置／遗留无效路径仍能启动并处理 MODEL／SUPPORT”回归替代，因此从 78 变为 72。测试带读时另跑代表性 19 项通过，未调用真实模型或部署。测试重点讲代表性行为和证据；不得恢复旧资源门禁。

下一步 Task 2：保留用户写核心 SQL／状态判断、Codex 做脚手架与验证的分工。从[来源锁与版本更新导学](../../AI_WORKLOG/2026-11-07-ai-reply-1a-task2-learning.md)的删除／迟到结果行为预测开始，再写首批 Mapper SQL；尚未代写核心实现或宣称独立掌握。不要重新扩展 lab 前置课程，也不要把所有后续能力一起实现。

社区耗时采样属于Task3的校准步骤：最多5次真实模型合成输入，按计划记录耗时；目前2秒连接/15秒读等待、前端20秒仅为初值。当前会话没有执行采样。

## 最近修订必须保留

- 来源修改update、软删除delete、结果提交complete共用实际DB事务中的FOR UPDATE来源行锁与指定列条件UPDATE，不能整实体回写status/version而复活日记。
- `deleted_at DATETIME NULL`纳入1a迁移；首次删除写时间，重复删除不改；历史已删除行保留NULL表示时间未知。
- 删除递增版本、清空当前回复、失效未完成任务；查询/重试/生成/提交校验status与来源版本。
- retry不显式锁来源，靠带预期attempt_count的job CAS与删除对failed/queued/running的失效；不反向获取来源锁。
- worker关闭时不做恢复/扫描/领取；恢复还要求旧worker已停止。多实例前补1b租约。
- 社区保留同步入口；发布和同步编辑单独配置等待并实测校准；失败不写公开兜底，编辑失败不恢复旧内容对应回复。
- Task3与Task4之间不可部署；完成前后端联调后同批发布。
- 不自动提交、推送、部署；数据库/真实模型/浏览器/离线测试的验证证据分别记录。

## 学习与参考资料

产品方案、接口、数据和阶段计划在heart-island维护，正式开工以这里为准。研究与学习原稿留在agent-lab，不移动/删除源码：

- [优秀产品设计调研](/Users/jiangqiuyu/pre/agent-lab/agent-lab/notes/优秀产品设计调研-2026-11-06.md)：多产品设计取舍，主要是官方文档阅读，不是产品实测。
- [工程化职责与交接背景](/Users/jiangqiuyu/pre/agent-lab/agent-lab/notes/工程化职责与心屿交接.md)：早期职责导学，具体契约由本项目总方案和1a设计替代。
- [agent-lab正式路线](/Users/jiangqiuyu/pre/agent-lab/agent-lab/notes/agent-lab总体学习路线.md)：已学/已验范围与历史基线；不得写成心屿现有功能。

优秀产品的官方参考入口与证据层级见总方案第13节；进入具体机制时核验版本和源码，结合当前产品改动逐步讲解。
