# 00 项目总览

## 基本信息

- 项目名：heart-island / 心屿。
- 定位：AI 情绪记录与匿名互助社区系统。
- 技术栈：Spring Boot + Vue3 + MySQL + MyBatis-Plus + JWT + AI API。
- 主要目录：`xinyu-backend/`、`xinyu-vue/`、`sql/`、`docs/`、`ops/`。

## 已有能力

- 用户注册、登录、JWT 鉴权、个人资料。
- 心情记录新增、列表、详情、修改、删除、趋势统计。
- 心情记录和社区帖子触发 AI 回复，并保留 AI 调用记录。
- 匿名社区：帖子、评论、点赞、收藏、话题。
- 后台管理：用户、帖子、评论、话题、AI 回复记录、概览统计。

## 当前权威资料

- 数据库结构：优先看 `sql/xinyu.sql`。
- 后端行为：优先看 `xinyu-backend/src/main/java/com/xinyu/`。
- 前端行为：优先看 `xinyu-vue/src/`。
- 交付型说明：看 `docs/project/`。

## 后续升级方向

- AI 多轮对话。
- 轻量长期记忆。
- 情绪周报。
- RAG 知识库。
- AI 工具调用。
- SSE 流式输出。
- 内容治理。
- 部署与监控。

## Agent 使用原则

工作边界与资料归属统一见 [AGENTS.md](../AGENTS.md)，新功能方案见 [ops/06](06-feature-development.md)。本页只提供项目背景，不作为每次任务必读项。
