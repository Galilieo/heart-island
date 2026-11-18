# 01 架构地图

更新：2026-11-18。字段、状态与接口细节以功能契约为准，本页只给定位。

## 总体链路

```text
Vue 页面 -> Pinia store / src/api -> utils/request.js（axios，带 token）
  -> Spring Controller（AuthInterceptor 鉴权）-> Service -> Mapper / MyBatis-Plus -> MySQL
  -> Result / VO 返回前端（long 一律序列化为十进制字符串）
```

AI 相关的三条路径：

```text
心情回应、近期回顾：
  保存记录／点击生成 -> 写 ai_job（queued）-> 单 Java worker 三槽领取（FOR UPDATE SKIP LOCKED + 条件更新）
  -> AiServiceClient（HTTP + 内部令牌）-> Python xinyu-ai -> 模型供应商
  -> 按尝试身份与来源版本条件提交；修改／删除后的迟到结果被拒绝
  前端 ai-job-polling.js 轮询 /mood/ai-job/{id}

社区帖子回应：
  发帖／编辑请求内同步调用模型（AiReplyService 直连供应商，不经 Python），生成代次保护防止旧结果回写

付费调用额度：
  RateLimiter（Redis 时间窗口；不可用时 FallbackRateLimiter 降级为本机计数）在准入事务外预检
```

## 后端结构（`xinyu-backend/src/main/java/com/xinyu/`）

- `controller/`：HTTP 入口；`admin*` 为后台，`AiJobController` 为任务查询与重试。
- `service/`：业务规则。AI 任务相关集中在 `AiJobService`、`AiJobWorker`、`AiJobAdmission`、`AiServiceClient`；回顾在 `MoodReview*`；社区在 `Community*`。
- `ratelimit/`：限流组件、Redis Lua 脚本与降级。
- `mapper/`、`entity/`、`dto/`、`vo/`、`common/`、`exception/`。
- `config/`、`interceptor/`、`utils/`：装配（worker 由 `AI_WORKER_ENABLED` 控制）、鉴权、JWT、Jackson。

## AI 服务（`xinyu-ai/`）

- `app/main.py`：内部端点 `/internal/v1/mood-replies`、`/internal/v1/mood-reviews`、`/health`；认证、请求体上限、总超时与并发门禁。
- `app/provider.py`、`app/review_prompt.py`：提示词与供应商调用；`app/safety.py`：危机规则；`app/contracts.py`、`app/reviews.py`：协议与输出校验。
- `evals/`：离线合成测评集与脚本；`tests/`：pytest 与供 Java 集成测试拉起的合成服务。

## 前端结构（`xinyu-vue/src/`）

- `views/`：页面入口（首页含心情记录、回顾、社区、收藏与我的举报、个人、隐私、后台）；`components/`：按 `mood/`、`review/`、`community/`、`admin/`、`ui/`、`layout/` 分组。
- `api/`、`stores/`（`admin/` 按域拆分）、`router/`、`styles/`。
- `composables/`：`useMoodPolling`、`useReviewPolling`、`useAsyncAction`、`usePagination` 等；`utils/ai-job-polling.js` 为串行轮询器。

## 数据（MySQL）

用户 `user`；心情 `mood_record`（含 `content_version`、`ai_reply_enabled`）；AI 日志 `ai_reply`；任务 `ai_job`（`MOOD_REPLY`／`MOOD_REVIEW`）；回顾 `mood_review`、`mood_review_source`；社区 `topic`、`community_post`、`community_reply`、`post_like`、`favorite`；举报 `content_report`。

## 新模块接入位置

- 新页面放 `views/`，复杂区域拆到 `components/<模块>/`；接口封装放 `src/api/模块名.js`；跨页面复用再进 `stores/`。
- 新后端接口按模块新增 Controller、Service、DTO、VO、Entity、Mapper；需要模型调用的长任务优先复用 `ai_job` 与 worker（新任务类型，单独 SQL），不要在请求线程里等模型。
- 新表与字段先写设计并取得同意，再补 `sql/migrations/` 增量脚本并同步 `sql/xinyu.sql`；不直接改生产数据。
