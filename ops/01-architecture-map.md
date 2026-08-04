# 01 架构地图

## 总体链路

```text
Vue 页面
  -> Pinia store / src/api
  -> utils/request.js Axios 封装
  -> Spring Controller
  -> Service 业务逻辑
  -> Mapper / MyBatis-Plus
  -> MySQL
  -> Result / VO 返回前端
```

## 前端结构

- `xinyu-vue/src/views/`：页面级入口，如登录、心情、社区、后台。
- `xinyu-vue/src/components/`：通用组件、社区组件、后台组件。
- `xinyu-vue/src/api/`：按模块封装接口调用。
- `xinyu-vue/src/stores/`：Pinia 状态管理。
- `xinyu-vue/src/router/`：路由和页面入口。
- `xinyu-vue/src/utils/request.js`：Axios、token、响应处理。
- `xinyu-vue/src/styles/`：全局样式、响应式、设计 token。

## 后端结构

- `controller/`：HTTP 接口入口，只做参数接收、权限入口和结果返回。
- `service/`：业务规则、权限校验、AI 调用编排。
- `mapper/`：MyBatis-Plus 数据访问。
- `entity/`：数据库表映射。
- `dto/`：前端请求对象，避免直接暴露实体。
- `vo/`：返回给前端的视图对象。
- `common/`：统一返回、异常、常量等公共能力。
- `config/`、`interceptor/`、`util/`：配置、鉴权、工具类。

## 核心模块

- 用户与权限：登录注册、JWT、个人信息、管理员权限。
- 心情记录：情绪内容、类型、时间筛选、AI 陪伴回复。
- AI 回复：prompt、回复内容、业务类型、业务 id、状态、错误信息。
- 匿名社区：帖子、评论、话题、点赞、收藏。
- 后台管理：内容管理、用户管理、AI 记录管理、统计概览。

## 新模块接入位置

- 新前端页面：优先放入 `views/`，复杂区域拆到 `components/`。
- 新接口封装：放入 `src/api/模块名.js`。
- 新状态：确认跨页面复用后再放入 `stores/`。
- 新后端接口：按模块新增 Controller、Service、DTO、VO、Entity、Mapper。
- 新表结构：先写设计，再补 SQL 变更脚本，不直接改生产数据。
