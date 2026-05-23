# xinyu-vue

心屿项目前端，基于 Vue3 + Vite 开发，包含用户端、匿名社区和后台管理端页面。

## 技术栈

- Vue3
- Vite
- Pinia
- Vue Router
- axios

## 主要功能

- 登录、注册、退出登录
- 心情记录列表、详情、新增、编辑、删除
- AI 回复展示
- 最近 7 天心情趋势
- 匿名社区帖子、回复、点赞、收藏
- 帖子 AI 回复展示
- 后台概览
- 用户管理、帖子管理、回复管理、话题管理
- AI 回复记录管理

## 目录说明

```text
src
├─ api          后端接口封装
├─ components   公共组件、业务组件和布局组件
├─ router       路由配置
├─ stores       Pinia 状态管理
├─ styles       全局样式
├─ utils        请求工具和业务工具
└─ views        页面组件
```

后台公共组件：

```text
src/components/admin
├─ AdminTabs.vue
├─ AdminPageHeader.vue
└─ AdminFilterActions.vue
```

## 环境配置

开发环境接口地址在 `.env.development` 中配置：

```text
VITE_API_BASE_URL=http://localhost:8080
```

## 本地运行

安装依赖：

```sh
npm install
```

启动开发服务：

```sh
npm run dev
```

构建生产包：

```sh
npm run build
```

代码检查：

```sh
npm run lint
```

## 页面路由

| 路由 | 说明 |
| --- | --- |
| `/login` | 登录 |
| `/register` | 注册 |
| `/` | 心情首页 |
| `/community` | 社区帖子列表 |
| `/community/post/:id` | 帖子详情 |
| `/favorites` | 收藏列表 |
| `/admin` | 后台概览 |
| `/admin/users` | 用户管理 |
| `/admin/posts` | 帖子管理 |
| `/admin/replies` | 回复管理 |
| `/admin/topics` | 话题管理 |
| `/admin/ai-replies` | AI 记录管理 |

## 开发约定

- 页面优先调用 `stores` 中的方法，不直接散落请求逻辑。
- 接口统一放在 `src/api` 下，按业务域拆分。
- 登录 token 由请求拦截器统一携带。
- 后台页面筛选按钮统一使用 `AdminFilterActions`。
- 后台页头统一使用 `AdminPageHeader`。
