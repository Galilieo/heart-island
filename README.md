# 心屿 AI 情绪记录与匿名互助社区系统

心屿是一个基于 Spring Boot、Vue3、MySQL 和 JWT 的前后端分离 Web 应用，围绕个人情绪记录、AI 情绪陪伴和匿名社区表达场景进行设计。

当前版本已经完成用户注册、登录认证、token 携带、心情记录新增、列表查询、详情查看、记录修改、删除、分页筛选、AI 情绪陪伴回复、AI 调用记录保存、最近 7 天心情趋势，以及匿名社区的发帖、回复、点赞、收藏、话题分类与排序等核心能力，形成了“登录 -> 记录心情 -> 生成 AI 回复 -> 查看与管理记录 -> 查看近期趋势 -> 进入匿名社区互动”的用户端完整闭环。后续版本将继续扩展后台管理和更完整的情绪统计等模块。

## 项目定位

心屿的核心目标是帮助用户完成以下事情：

- 记录自己的情绪状态和当天想法。
- 回看历史心情记录，观察情绪变化。
- 通过 AI 获得温和、支持性的情绪回应。
- 在匿名社区中表达情绪、获得互动。
- 通过统计图表了解一段时间内的情绪趋势。

系统不定位为医疗诊断工具，也不替代专业心理咨询服务。它更适合作为情绪记录、AI 陪伴反馈和匿名表达的综合型应用。

## 技术栈

### 后端

- Java 17
- Spring Boot
- MyBatis-Plus
- MySQL
- JWT
- BCrypt
- RestTemplate
- Maven

### 前端

- Vue 3
- Vue Router
- Pinia
- axios
- Vite
- localStorage

### 开发与测试工具

- IntelliJ IDEA
- Apifox
- Edge / Chrome DevTools
- Git
- npm
- DeepSeek API

## 已完成功能

### 用户与认证

- 用户注册
- 用户登录
- 密码加密存储
- JWT token 生成
- 登录状态保存
- 退出登录
- 前端请求自动携带 token
- 后端通过 token 解析当前用户
- 修改昵称
- 修改密码（校验原密码）
- 个人资料页

### 心情记录

- 新增心情记录
- 查询当前用户心情记录
- 查看心情记录详情
- 修改心情记录
- 删除心情记录
- 分页查询心情记录
- 按日期和心情类型筛选记录
- 最近 7 天心情趋势概览
- 新增记录时生成 AI 陪伴回复
- 修改记录时重新生成 AI 陪伴回复
- 前端心情记录卡片展示
- 前端展示 AI 回复和详情弹窗
- 空状态、加载状态、删除确认、编辑关闭确认和基础错误提示
- 登录路由守卫
- 登录页、注册页和首页统一用户端视觉风格
- 登录和注册使用页面内提示与提交 loading 状态

### AI 情绪陪伴

- 接入 DeepSeek API
- 通过环境变量读取 API Key
- 根据心情类型和记录内容生成温和回复
- AI 调用失败时返回默认陪伴文案
- 回复内容保存到心情记录中，便于列表和详情展示
- 完整 AI 调用记录保存到 `ai_reply` 表
- 记录 `model_name`、`prompt`、`reply_content`、`target_type`、`target_id`、`status` 和 `error_message`

### 匿名社区

- 发布匿名帖子（话题、情绪、内容）
- 帖子列表查询，支持按话题筛选
- 帖子排序：最新、最热
- 「全部 / 我的」帖子范围切换
- 帖子详情查看
- 修改和删除自己的帖子
- 发表、修改、删除回复
- 点赞与取消点赞
- 收藏与取消收藏
- 个人收藏页查看收藏的帖子
- 话题分类与种子数据
- 前端社区列表页、详情页、收藏页统一视觉风格

### 工程规范

- 前后端分离架构
- Controller / Service / Mapper 分层
- 统一返回结果 `Result`
- 全局异常处理
- CORS 跨域配置
- axios 请求封装
- 前端 Pinia 状态管理、api 层与 composable 抽象
- 基础接口测试

## 规划功能

后续版本计划继续完善：

- 管理员后台
- 用户管理和内容审核
- AI 回复记录后台查看
- 更完整的情绪趋势统计（如最近 30 天）
- ECharts 图表展示
- Redis 缓存
- 部署上线配置

## 系统架构

```text
浏览器
  -> Vue 前端服务
  -> axios / request.js
  -> Spring Boot 后端服务
  -> DeepSeek API
  -> MyBatis-Plus
  -> MySQL 数据库
```

核心数据流：

```text
用户操作页面
-> 前端调用后端接口
-> request.js 自动携带 token
-> 后端解析 token 获取当前用户
-> Service 处理业务逻辑
-> 需要 AI 回复时调用 DeepSeek API
-> Mapper 操作数据库
-> 后端返回统一 Result
-> 前端渲染结果
```

## 项目结构

```text
heart_island
├─ xinyu-backend        Spring Boot 后端项目
│  ├─ src
│  ├─ pom.xml
│  └─ ...
│
├─ xinyu-vue            Vue 前端项目
│  ├─ src
│  ├─ package.json
│  ├─ vite.config.js
│  └─ ...
│
├─ docs                 项目设计与说明文档
├─ sql                  数据库 SQL 文件
├─ assets               项目图片资源
├─ README.md            项目说明
└─ .gitignore
```

## 后端结构

```text
xinyu-backend
├─ common       通用类，例如统一返回结果 Result
├─ config       配置类，例如跨域配置
├─ controller   接收前端请求，提供后端接口
├─ entity       实体类，对应数据库表
├─ exception    全局异常处理
├─ mapper       数据库操作接口
├─ service      业务逻辑层
├─ utils        工具类，例如 JWT 工具
└─ resources    配置文件
```

## 前端结构

```text
xinyu-vue
├─ src
│  ├─ views         页面，例如登录页、首页、社区页、收藏页
│  ├─ router        路由配置与登录守卫
│  ├─ api           按业务域划分的接口请求
│  ├─ stores        Pinia 状态管理（user / mood / community）
│  ├─ composables   通用逻辑封装（toast / confirm / 异步动作 / 分页）
│  ├─ components    可复用组件（ui 基础组件、layout、业务组件）
│  ├─ styles        全局样式与设计 token
│  ├─ utils         工具文件，例如 request.js
│  ├─ App.vue       根组件
│  └─ main.js       入口文件
└─ package.json
```

## 快速启动

### 1. 初始化数据库

创建数据库：

```sql
CREATE DATABASE xinyu DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

数据库表结构可以参考 `sql` 目录或文档：

```text
docs/04-数据库设计说明书.md
docs/09-部署与运行说明.md
```

### 2. 启动后端

使用 IntelliJ IDEA 打开后端项目：

```text
D:\heart_island\xinyu-backend
```

确认 `src/main/resources/application.properties` 中数据库配置正确：

```properties
deepseek.api-key=${DEEPSEEK_API_KEY}
deepseek.base-url=https://api.deepseek.com
deepseek.model=deepseek-v4-flash

spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/xinyu?serverTimezone=Asia/Shanghai&useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:123456}
```

启动后端前需要配置 `DEEPSEEK_API_KEY` 环境变量。数据库连接也可以通过 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 覆盖默认值。

运行启动类：

```text
XinyuBackendApplication
```

后端默认地址：

```text
http://localhost:8080
```

也可以在后端目录使用命令启动：

```bash
mvn spring-boot:run
```

### 3. 启动前端

进入前端目录：

```bash
cd xinyu-vue
npm install
npm run dev
```

前端默认地址：

```text
http://localhost:5173
```

前端接口地址通过 `xinyu-vue/.env.development` 中的 `VITE_API_BASE_URL` 配置，开发环境默认指向 `http://localhost:8080`。

## 核心接口

| 功能 | 请求方式 | 接口地址 | 说明 |
| --- | --- | --- | --- |
| 用户注册 | POST | `/user/register` | 新用户注册 |
| 用户登录 | POST | `/user/login` | 登录成功后返回用户信息和 token |
| 修改昵称 | POST | `/user/update-profile` | 修改当前用户昵称 |
| 修改密码 | POST | `/user/update-password` | 校验原密码后修改密码 |
| 查询心情记录 | GET | `/mood/list` | 分页查询当前登录用户的心情记录，支持条件筛选，并返回 AI 回复 |
| 新增心情记录 | POST | `/mood/add` | 新增当前登录用户的心情记录，并生成 AI 回复 |
| 查看心情详情 | GET | `/mood/detail/{id}` | 查看当前用户的指定心情记录和 AI 回复 |
| 修改心情记录 | PUT | `/mood/update/{id}` | 修改当前用户的指定心情记录，并重新生成 AI 回复 |
| 删除心情记录 | DELETE | `/mood/delete/{id}` | 删除当前用户的指定心情记录 |
| 最近 7 天趋势 | GET | `/mood/trend/recent7` | 查询当前用户最近 7 天心情概览和心情类型分布 |
| 话题列表 | GET | `/topic/list` | 查询启用中的社区话题 |
| 帖子列表 | GET | `/community/post/list` | 查询社区帖子，支持话题筛选、最新/最热排序、我的范围 |
| 收藏帖子列表 | GET | `/community/post/favorites` | 查询当前用户收藏的帖子 |
| 帖子详情 | GET | `/community/post/detail` | 查询指定帖子详情 |
| 发布帖子 | POST | `/community/post/add` | 发布匿名帖子 |
| 修改帖子 | POST | `/community/post/update` | 修改自己发布的帖子 |
| 删除帖子 | POST | `/community/post/delete` | 删除自己发布的帖子（逻辑删除） |
| 点赞 / 取消点赞 | POST | `/community/post/like`、`/community/post/unlike` | 对帖子点赞或取消 |
| 收藏 / 取消收藏 | POST | `/community/post/favorite`、`/community/post/unfavorite` | 对帖子收藏或取消 |
| 回复列表 | GET | `/community/reply/list` | 查询指定帖子的回复 |
| 发表 / 修改 / 删除回复 | POST | `/community/reply/add`、`/community/reply/update`、`/community/reply/delete` | 回复的增改删 |

更完整的接口规划见：

```text
docs/05-接口设计说明书.md
```

## 统一返回格式

后端接口统一使用 `Result` 返回：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {}
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| code | 业务状态码 |
| message | 响应提示信息 |
| data | 业务数据 |

## 登录认证流程

```text
用户输入用户名和密码
-> 前端调用 /user/login
-> 后端查询用户并校验密码
-> 登录成功后生成 JWT token
-> 后端返回 token 和不含 password 的用户信息
-> 前端保存 token 和 loginUser 到 localStorage
-> 后续请求由 request.js 自动携带 token
-> 后端从 token 中解析 userId
```

## 心情记录流程

```text
用户填写心情类型和内容
-> 前端调用 /mood/add
-> 请求头自动携带 token
-> 后端解析 token 获取当前 userId
-> 后端调用 AI 服务生成陪伴回复
-> 后端保存心情记录和最新 AI 回复
-> 后端保存完整 AI 调用记录到 ai_reply 表
-> 前端刷新心情记录列表并展示 AI 回复
```

## 数据安全设计

- 密码使用 BCrypt 加密后保存。
- 登录接口不向前端返回 password。
- 后端不信任前端传入的 `userId`。
- 需要登录的接口通过 token 判断当前用户。
- 前端统一通过 request.js 携带 token。
- 私密心情记录默认只属于当前登录用户。

## 项目文档

项目文档位于 `docs` 目录：

| 文档 | 说明 |
| --- | --- |
| `01-项目背景与可行性分析.md` | 项目背景、建设目标、可行性分析 |
| `02-需求分析说明书.md` | 用户角色、用户故事、功能需求 |
| `03-系统概要设计说明书.md` | 系统架构、模块划分、技术选型 |
| `04-数据库设计说明书.md` | 数据表、字段、关系和索引设计 |
| `05-接口设计说明书.md` | 接口规范、请求参数、返回结果 |
| `06-详细设计说明书.md` | 业务流程和核心模块详细设计 |
| `07-前端界面与交互设计说明.md` | 页面结构、交互流程、状态设计 |
| `08-测试说明书.md` | 测试范围、测试用例、验收标准 |
| `09-部署与运行说明.md` | 本地运行、联调、部署方案 |
| `10-项目总结与后续规划.md` | 阶段总结、问题边界、迭代计划 |
| `11-分阶段开发计划.md` | 后续开发阶段、任务拆分、完成标准 |

## 当前版本说明

当前版本重点完成基础业务闭环：

```text
注册
-> 登录
-> 保存 token
-> 新增心情记录
-> 生成 AI 陪伴回复
-> 查看自己的心情记录
-> 分页和条件筛选
-> 查看心情详情
-> 修改心情记录
-> 重新生成 AI 回复
-> 查看最近 7 天心情趋势
-> 删除心情记录
-> 退出登录
```

匿名社区模块已经完成，包含发帖、回复、点赞、收藏、话题分类与排序。后台管理和更完整的统计分析属于后续迭代模块，相关设计已经在 `docs` 目录中进行规划。

## 后续迭代路线

```text
第一阶段：完善心情记录详情、修改、分页、筛选（已完成）
第二阶段：接入 AI 回复能力和用户端体验完善（已完成基础版本）
第三阶段：建设匿名社区模块（已完成）
第四阶段：建设后台管理模块
第五阶段：完善情绪统计图表
第六阶段：完善部署、缓存和工程配置
```

## 运行验证

登录成功后，可以在浏览器 DevTools 中验证：

```text
Application -> LocalStorage -> http://localhost:5173
```

应能看到：

```text
loginUser
token
```

刷新心情记录时，可以在：

```text
Network -> Fetch/XHR -> /mood/list -> Request Headers
```

看到请求头中携带 `token`，说明前端请求拦截器已经生效。

新增或修改心情记录后，可以在列表卡片或详情弹窗中看到 `心屿回复`。如果 AI 服务暂时不可用，后端会返回默认陪伴文案，保证记录流程不中断。

新增或修改心情记录后，也可以在数据库中查看 `ai_reply` 表，确认 AI 调用记录是否保存成功。首页的最近 7 天趋势模块会调用 `/mood/trend/recent7`，用于展示近期记录数、主要心情、最近一次心情和心情类型分布。
