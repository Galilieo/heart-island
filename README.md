# 心屿 AI 情绪记录与匿名互助社区系统

心屿是一个基于 Spring Boot + Vue3 的前后端分离项目，围绕“情绪记录、AI 陪伴回复、匿名互助社区、后台内容治理”构建完整 Web 应用闭环。

项目当前已经完成用户注册登录、JWT 认证、心情记录 CRUD、AI 回复生成与记录、匿名社区发帖回复、点赞收藏、话题管理、后台用户/帖子/回复/话题管理、AI 回复记录管理和后台概览统计等核心能力。整体功能已经从普通 CRUD 扩展为一个更接近真实实习项目的综合平台。

## 项目定位

本项目不是单纯练习页面或单表增删改查，而是面向个人简历和实习展示的 Java Web 项目。它重点体现：

- 前后端分离开发能力
- Spring Boot 分层架构设计能力
- MyBatis-Plus 数据访问能力
- JWT 登录认证与角色权限控制
- Vue3 + Pinia + Vue Router 状态与页面组织能力
- AI 能力接入、失败兜底和调用日志留痕
- 社区内容治理与后台管理能力
- 文档、测试、部署和阶段规划意识

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端 | Spring Boot、MyBatis-Plus、MySQL、JWT、BCrypt、Knife4j |
| 前端 | Vue3、Vite、Pinia、Vue Router、axios |
| AI | DeepSeek API |
| 数据库 | MySQL |
| 部署规划 | Linux、Nginx、JDK、Maven、Node.js |

## 已完成功能

### 用户与权限

- 用户注册、登录、退出登录
- JWT token 保存与请求自动携带
- 密码 BCrypt 加密存储
- 登录后保存用户基础信息
- 昵称和密码修改
- 普通用户与管理员角色区分
- 管理员接口权限校验

### 心情记录

- 新增心情记录
- 心情记录分页列表
- 心情类型、关键词、时间筛选
- 查看心情详情
- 修改心情记录
- 删除心情记录
- 最近 7 天情绪趋势统计
- 新增或修改记录后自动生成 AI 回复

### AI 回复

- 心情记录 AI 陪伴回复
- 社区帖子 AI 回复
- AI 调用成功记录
- AI 调用失败记录
- 失败时返回兜底文案，不阻断主业务
- `ai_reply` 表保存 prompt、回复内容、业务类型、业务 id、状态和错误信息
- 后台 AI 回复记录分页、筛选和详情查看
- 后台概览统计 AI 调用总数、成功次数、失败次数和失败率

### 匿名社区

- 话题列表
- 按话题查看帖子
- 发布帖子
- 修改本人帖子
- 删除本人帖子
- 帖子详情
- 最新/热门排序
- 回复帖子
- 修改本人回复
- 删除本人回复
- 点赞/取消点赞
- 收藏/取消收藏
- 收藏列表
- 帖子详情展示 AI 心屿回复

### 后台管理

- 后台概览页
- 用户管理：分页、搜索、状态筛选、启用/禁用
- 帖子管理：分页、内容搜索、帖子 id、用户 id、状态筛选、隐藏/恢复
- 回复管理：分页、内容搜索、回复 id、帖子 id、用户 id、状态筛选、隐藏/恢复
- 话题管理：新增、编辑、启用/禁用、排序
- AI 记录管理：按用户、业务类型、状态、关键词筛选，查看详情
- 后台公共组件拆分：`AdminTabs`、`AdminPageHeader`、`AdminFilterActions`

## 项目结构

```text
heart_island
├─ docs
│  ├─ 01-项目背景与可行性分析.md
│  ├─ 02-需求分析说明书.md
│  ├─ 03-系统概要设计说明书.md
│  ├─ 04-数据库设计说明书.md
│  ├─ 05-接口设计说明书.md
│  ├─ 06-详细设计说明书.md
│  ├─ 07-前端界面与交互设计说明.md
│  ├─ 08-测试说明书.md
│  ├─ 09-部署与运行说明.md
│  ├─ 10-项目总结与后续规划.md
│  └─ 11-分阶段开发计划.md
├─ sql
│  └─ xinyu.sql
├─ xinyu-backend
│  └─ src/main/java/com/xinyu
│     ├─ controller
│     ├─ service
│     ├─ mapper
│     ├─ entity
│     ├─ dto
│     ├─ vo
│     └─ common
└─ xinyu-vue
   └─ src
      ├─ api
      ├─ stores
      ├─ router
      ├─ views
      ├─ components
      └─ utils
```

## 快速启动

### 1. 初始化数据库

在 MySQL 中创建数据库并导入脚本：

```sql
CREATE DATABASE xinyu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

然后导入：

```text
sql/xinyu.sql
```

### 2. 启动后端

进入后端目录：

```bash
cd xinyu-backend
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8080
```

### 3. 启动前端

进入前端目录：

```bash
cd xinyu-vue
npm install
npm run dev
```

默认前端地址：

```text
http://localhost:5173
```

前端接口地址通过 `xinyu-vue/.env.development` 中的 `VITE_API_BASE_URL` 配置。

## 核心接口概览

| 模块 | 方法 | 地址 | 说明 |
| --- | --- | --- | --- |
| 用户 | POST | `/user/register` | 注册 |
| 用户 | POST | `/user/login` | 登录 |
| 用户 | POST | `/user/update-profile` | 修改昵称 |
| 用户 | POST | `/user/update-password` | 修改密码 |
| 心情 | GET | `/mood/list` | 心情记录分页 |
| 心情 | POST | `/mood/add` | 新增心情并生成 AI 回复 |
| 心情 | GET | `/mood/detail/{id}` | 心情详情 |
| 心情 | PUT | `/mood/update/{id}` | 修改心情并重新生成 AI 回复 |
| 心情 | DELETE | `/mood/delete/{id}` | 删除心情 |
| 心情 | GET | `/mood/trend/recent7` | 最近 7 天趋势 |
| 社区 | GET | `/community/post/list` | 帖子列表 |
| 社区 | POST | `/community/post/add` | 发布帖子并生成 AI 回复 |
| 社区 | GET | `/community/post/detail?id=` | 帖子详情 |
| 社区 | POST | `/community/post/update` | 修改帖子并重新生成 AI 回复 |
| 社区 | POST | `/community/reply/add` | 发布回复 |
| 社区 | POST | `/community/post/like`、`/community/post/unlike` | 点赞/取消点赞 |
| 社区 | POST | `/community/post/favorite`、`/community/post/unfavorite` | 收藏/取消收藏 |
| 后台 | GET | `/admin/overview` | 后台概览统计 |
| 后台 | GET | `/admin/user/page` | 用户管理分页 |
| 后台 | PUT | `/admin/user/{id}/status` | 启用/禁用用户 |
| 后台 | GET | `/admin/post/page` | 帖子管理分页 |
| 后台 | PUT | `/admin/post/{id}/status` | 隐藏/恢复帖子 |
| 后台 | GET | `/admin/reply/page` | 回复管理分页 |
| 后台 | PUT | `/admin/reply/{id}/status` | 隐藏/恢复回复 |
| 后台 | GET | `/admin/topic/list` | 话题管理列表 |
| 后台 | POST | `/admin/topic` | 新增话题 |
| 后台 | PUT | `/admin/topic/{id}` | 编辑话题 |
| 后台 | GET | `/admin/ai-replies/page` | AI 回复记录分页 |

更完整的接口说明见 [docs/05-接口设计说明书.md](docs/05-接口设计说明书.md)。

## 运行验证

后端测试：

```bash
cd xinyu-backend
mvn test
```

前端构建：

```bash
cd xinyu-vue
npm run build
```

建议手动验证以下主流程：

1. 注册并登录普通用户。
2. 新增心情记录，确认列表和详情展示 AI 回复。
3. 发布社区帖子，确认帖子详情展示 AI 回复。
4. 点赞、收藏、回复帖子。
5. 使用管理员账号进入后台。
6. 查看概览统计、用户管理、帖子管理、回复管理、话题管理和 AI 记录管理。

## 文档目录

| 文档 | 说明 |
| --- | --- |
| `docs/01-项目背景与可行性分析.md` | 项目背景、价值和可行性 |
| `docs/02-需求分析说明书.md` | 角色、用户故事和功能需求 |
| `docs/03-系统概要设计说明书.md` | 架构、模块划分和整体设计 |
| `docs/04-数据库设计说明书.md` | 数据表、字段和关系说明 |
| `docs/05-接口设计说明书.md` | REST 接口、参数和返回说明 |
| `docs/06-详细设计说明书.md` | 关键业务流程和实现细节 |
| `docs/07-前端界面与交互设计说明.md` | 页面结构和交互设计 |
| `docs/08-测试说明书.md` | 测试范围、测试用例和验收标准 |
| `docs/09-部署与运行说明.md` | 本地和服务器部署说明 |
| `docs/10-项目总结与后续规划.md` | 当前成果、亮点和后续方向 |
| `docs/11-分阶段开发计划.md` | 已完成阶段和后续开发计划 |

## 简历表述建议

可以在简历中概括为：

> 基于 Spring Boot + Vue3 开发“心屿”AI 情绪记录与匿名互助社区系统，实现 JWT 登录认证、BCrypt 密码加密、心情记录 CRUD、AI 陪伴回复、匿名社区发帖回复、点赞收藏、后台内容治理、AI 调用日志管理和平台统计等功能。后端采用 Controller-Service-Mapper 分层和 MyBatis-Plus 完成业务开发，前端使用 Vue3、Pinia、Vue Router 和 axios 构建用户端与管理端页面，并对后台列表筛选、分页和公共组件进行了复用拆分。

## 后续规划

项目当前主流程已经完整，后续可以继续增强：

- 用户端 30 天情绪趋势和 ECharts 可视化
- 举报、敏感词和内容审核流程
- AI 回复异步化、重试和限流
- Redis 缓存热门帖子、话题和统计数据
- 服务器部署、Nginx 反向代理和线上日志
- 单元测试、接口测试和端到端测试补充
