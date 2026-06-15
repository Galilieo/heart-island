# 心屿前端重构与视觉升级设计

**日期**: 2026-05-19  
**范围**: `xinyu-vue/` 整个前端项目  
**目标**: 在保留现有功能的前提下，建立全局设计系统，统一柔暖手绘视觉风格；引入 api 层、Pinia store、composable，瘦身所有 view 文件。

---

## 1. 背景与现状

技术栈：Vue 3 + Vite + Vue Router + Pinia（已装未用）+ Element Plus（已装基本未用）+ Axios。

主要问题：
- 5 个 view 文件总计约 3220 行，最大 `Home.vue` 1067 行，业务逻辑、UI、状态、HTTP 全混在一起。
- 每个 view 自己写一套 CSS，颜色、间距、圆角、阴影各处不一致，没有全局 token。
- 每个按钮重复写 `loading` / `try/catch` / `message` / `messageTimer` 模板，约几十处。
- `request` 直接散落在 view 中调用，URL 与 view 强耦合。
- `localStorage.getItem('loginUser')` 在多个文件中直接读写，登录状态跨页面同步靠刷新页面。
- Element Plus 在 deps 中但实际未使用（router 中误导入一行 `tree-select-option.mjs`）。
- Pinia 在 deps 中但 `stores/` 目录为空。

---

## 2. 视觉方向

**风格**：奶油纸感 + 薄荷青主调 + 杏粉点缀；圆润手绘感；暖色阴影替代冷灰阴影。

### 2.1 颜色 Token

```
--bg-base       #fdfaf3   主背景（奶油纸感）
--bg-soft       #f6efe1   稍深奶油，分区/输入框背景
--bg-card       #ffffff   卡片白
--ink-1         #2f2a24   主文字（暖黑）
--ink-2         #6b6357   次要文字
--ink-3         #a39c8e   placeholder / 辅助
--line          #ece4d4   描边（米色）
--brand         #6fa9a4   薄荷青
--brand-deep    #4d8580   悬停/激活
--brand-soft    #e4f0ee   品牌背景态
--accent        #d98c7a   杏粉
--warn          #d4a24a
--danger        #c66a6a
--joy           #f3b95f   心情：开心
--calm          #9bc1bc   心情：平静
--sad           #8fa5c4   心情：难过
--angry         #d48a7a   心情：生气
--tired         #b3a89a   心情：疲惫
```

颜色具体值在实施过程中可微调，框架不变。

### 2.2 字体

```
--font-sans:    "MiSans","Source Han Sans SC","PingFang SC",
                "Microsoft YaHei", system-ui, sans-serif
--font-display: "Smiley Sans","ZCOOL KuaiLe","MiSans", sans-serif
字号阶梯: 12 / 13 / 14 / 16 / 18 / 22 / 28 / 36
```

### 2.3 间距 / 圆角 / 阴影 / 动效

```
--space-1..8:  4 / 8 / 12 / 16 / 20 / 24 / 32 / 48
--radius-1..4: 6 / 12 / 18 / 24
--shadow-sm:   0 2px 6px rgba(120,98,60,.06)
--shadow-md:   0 12px 28px rgba(120,98,60,.10)
--shadow-lg:   0 22px 50px rgba(120,98,60,.14)
--shadow-inset: inset 0 0 0 1px var(--line)
--ease-bounce: cubic-bezier(.34,1.56,.64,1)
--ease-soft:   cubic-bezier(.4,.0,.2,1)
--t-fast/base/slow: 140ms / 220ms / 360ms
```

### 2.4 手绘感细节

- 卡片轻微旋转 0 ~ -0.5deg。
- 分隔线 dashed 或双层波浪 SVG。
- 按钮按下 `translateY(1px)` + 阴影内缩。
- emoji + 自家 SVG 涂鸦点缀（云、星、叶、波浪、植物、杯子）。
- 背景叠加细微噪点纹理。

---

## 3. 目录结构

```
src/
├── api/                  按域拆 HTTP
│   ├── user.js
│   ├── mood.js
│   ├── community.js
│   ├── topic.js
│   └── index.js
├── stores/               Pinia
│   ├── user.js
│   ├── mood.js
│   ├── community.js
│   └── ui.js
├── composables/
│   ├── usePagination.js
│   ├── useToast.js
│   ├── useConfirm.js
│   └── useAsyncAction.js
├── styles/
│   ├── tokens.css
│   ├── base.css
│   ├── utilities.css
│   └── index.css
├── components/
│   ├── ui/
│   │   ├── BaseButton.vue
│   │   ├── BaseCard.vue
│   │   ├── BaseInput.vue
│   │   ├── BaseTextarea.vue
│   │   ├── BaseSelect.vue
│   │   ├── BaseTag.vue
│   │   ├── BaseDialog.vue
│   │   ├── BasePagination.vue
│   │   ├── EmptyState.vue
│   │   ├── SkeletonCard.vue
│   │   ├── Toast.vue
│   │   └── Doodle.vue
│   ├── layout/
│   │   ├── AppShell.vue
│   │   ├── AppHeader.vue
│   │   └── AppFooter.vue
│   ├── mood/
│   │   ├── MoodCard.vue
│   │   ├── MoodDetailDialog.vue
│   │   ├── MoodForm.vue
│   │   ├── MoodFilterBar.vue        (新)
│   │   └── MoodTrendPanel.vue       (新)
│   └── community/
│       ├── PostCard.vue
│       ├── PostPublishForm.vue
│       ├── TopicFilter.vue
│       ├── PostReplyList.vue        (新)
│       └── PostReplyForm.vue        (新)
├── views/
│   ├── Login.vue
│   ├── Register.vue
│   ├── Home.vue
│   ├── PostList.vue
│   └── PostDetail.vue
├── assets/
│   ├── logo-xinyu.png
│   └── doodles/                     (新，SVG)
├── router/index.js
├── utils/request.js
└── main.js
```

预期 view 文件行数：

| 文件 | 现状 | 目标 |
|---|---|---|
| Login.vue | 264 | ~120 |
| Register.vue | 267 | ~120 |
| Home.vue | 1067 | ~250 |
| PostList.vue | 823 | ~200 |
| PostDetail.vue | 801 | ~200 |

---

## 4. 基础 UI 组件契约

### BaseButton
- props: `variant` = `primary | soft | ghost | danger | plain`，`size` = `sm | md | lg`，`loading`，`disabled`，`block`
- slot: `default`、`icon`
- 事件: `@click`
- 视觉: 圆角 12，按下 `translateY(1px)` + 阴影内缩，`loading` 显示小圆点

### BaseCard
- props: `padding` = `sm | md | lg`，`tilt`（默认 0），`hoverable`，`as`
- slot: `default | header | footer`
- 视觉: 米色描边 + 暖阴影；`hoverable` 时阴影 +1 层、轻微抬起

### BaseInput / BaseTextarea / BaseSelect
- props: `v-model`，`placeholder`，`label`，`error`，`hint`
- slot: `prefix`
- 视觉: 奶油底，聚焦时薄荷青描边 + 软光晕

### BaseTag
- props: `tone` = `brand | joy | calm | sad | angry | tired | warn | danger | neutral`，`size`，`closable`
- 用于心情标签、话题标签、状态徽章

### BaseDialog
- props: `modelValue`，`title`，`width` = `sm | md | lg`，`closable`，`persistent`
- slot: `default | footer`
- 视觉: 卡片样弹窗 + 遮罩 + 进入动画

### BasePagination
- props: `pageNum`，`pageSize`，`total`，`pages`
- 事件: `@change(pageNum)`

### EmptyState
- props: `title`，`hint`，`doodle` = `cloud | leaf | star | cup`

### SkeletonCard
- props: `rows`（默认 3），`avatar`（默认 false）

### Toast
- 由 `useToast` 调度，全局单例，挂在 AppShell 顶层
- api: `toast.success(msg) / error(msg) / info(msg)`

### Doodle
- props: `name` = `cloud | star | leaf | wave | cup | plant`，`size`，`color`
- 实现: 内联 SVG

### 心情配色映射

| 心情 | tone | token |
|---|---|---|
| 开心 | joy | `--joy` |
| 平静 | calm | `--calm` |
| 难过 | sad | `--sad` |
| 生气 | angry | `--angry` |
| 疲惫 | tired | `--tired` |

---

## 5. 数据层

### 5.1 `api/`

只负责 HTTP，使用现有 `utils/request.js`，返回 `response.data`。

```
user.js
  login({username, password})
  register(payload)
  getProfile()
  logout()

mood.js
  list({moodType, startDate, endDate, pageNum, pageSize})
  detail(id)
  create({moodType, content})
  update(id, {moodType, content})
  remove(id)
  trend()
  aiReply(id)

community.js
  postList({topicId, keyword, pageNum, pageSize})
  postDetail(id)
  createPost(payload)
  updatePost(id, payload)
  removePost(id)
  like(id) / unlike(id)
  favorite(id) / unfavorite(id)
  replyList(postId, {pageNum, pageSize})
  createReply(payload)
  removeReply(id)

topic.js
  list()
```

实际 URL 与字段以 `xinyu-backend` 控制器为准，迁移时按现有 view 中调用的接口对齐。

### 5.2 `stores/` Pinia

```
user
  state: loginUser, token
  actions: login / register / logout / fetchProfile
  getters: isLoggedIn
  持久化: token + loginUser 写 localStorage（集中处理）

mood
  state: list, total, pages,
         filters{moodType, startDate, endDate},
         pagination{pageNum, pageSize},
         trend, selected
  actions: fetchList / fetchTrend / add / update / remove
           setFilter / setPage / openDetail / closeDetail / aiReply

community
  state: posts, total, pages,
         filters{topicId, keyword},
         pagination,
         currentPost, replies, replyPagination,
         topics
  actions: fetchPosts / fetchPost / fetchReplies / publishPost /
           updatePost / removePost / toggleLike / toggleFavorite /
           publishReply / removeReply / fetchTopics

ui
  state: toast queue, confirm dialog state, global loading
  actions: pushToast / dismiss / confirm({title, content})
```

### 5.3 composables

```
usePagination(fetcher, {pageSize=10})
  → {pageNum, pageSize, total, pages, refresh, goto, next, prev}

useToast()
  → toast.success(msg) / error(msg) / info(msg)

useConfirm()
  → async confirm({title, content}) → boolean

useAsyncAction(fn, {successMsg, errorMsg})
  → {run, loading}
  自动 toast.success / toast.error(err.userMessage)
```

### 5.4 调用规约

- view **不再直接 import `request`**；统一通过 store action → api。
- `request.js` 拦截器保留；登录失效时不再硬跳转 `window.location.href`，改为派发到 user store 由 router 处理。
- `localStorage` 的 `token`、`loginUser` 读写只在 user store 中。

---

## 6. 页面重构

### Login.vue
- 布局: 左侧 Doodle 插画 + slogan，右侧居中 BaseCard 表单。
- 调 `userStore.login()`，成功跳 `/home`。

### Register.vue
- 与 Login 同布局，复用表单组件。
- 注册成功后引导登录或自动登录跳 `/home`。

### Home.vue
- 顶部 AppHeader（logo + 导航 [心情 / 社区] + 用户菜单）。
- 两栏 grid：
  - 左：`<MoodForm @submit="moodStore.add"/>`，上方一张「今日心情」小卡。
  - 右：`<MoodFilterBar v-model:filters/>` + `<MoodTrendPanel :trend/>` + 列表 `<MoodCard v-for/>` + `<BasePagination/>`。
- view 只剩组装与事件绑定。

### PostList.vue
- 顶部 AppHeader。
- 左侧 TopicFilter + 关键词搜索；中间 PostPublishForm + PostCard 列表 + BasePagination。

### PostDetail.vue
- 单栏居中。
- `<PostCard variant="detail"/>` + `<PostReplyForm @submit/>` + `<PostReplyList :replies>` 含 `<BasePagination/>`。

### 共同细节
- 所有页面套 AppShell（背景 + 噪点 + footer）。
- 列表加载用 SkeletonCard；空列表用 EmptyState + doodle。
- 所有 confirm 改 `useConfirm`；所有 message 改 `useToast`。
- view 不再出现 `request` / `localStorage` 调用。

---

## 7. 清理项

- 从 `package.json` 移除 `element-plus`。
- 删除 `router/index.js` 第 8 行误导入 `tree-select-option.mjs`。
- `main.js` 注册 Pinia，引入 `styles/index.css`。
- 删除 view 中遗留的 message/messageTimer 相关 ref。

---

## 8. 实施顺序

1. 装 Pinia 入口 + 建 `styles/`、`api/`、`stores/`、`composables/` 骨架（不动 view）。
2. 写 ui 基础组件（Base*、Toast、Doodle、SkeletonCard、EmptyState）。
3. 写 layout（AppShell / AppHeader / AppFooter）。
4. Login 打通：验证整套设计系统 + 数据层。
5. Register（同模板）。
6. Home：抽 MoodFilterBar / MoodTrendPanel，瘦身。
7. PostList。
8. PostDetail：抽 PostReplyList / PostReplyForm。
9. 清理：删 Element Plus、删 router 误导入、跑一遍 lint。
10. 自测每个页面（登录、注册、心情 CRUD、AI 回复、帖子 CRUD、点赞收藏、回复、分页、筛选）。

---

## 9. 不做的事（YAGNI）

- 不引入 TypeScript / UnoCSS / Tailwind。
- 不做暗色主题（视觉方向已定为奶油亮色）。
- 不做 i18n。
- 不做单元测试框架（项目目前没有测试基建，本轮不引入）。
- 不重写 `utils/request.js` 拦截器，只调整失败跳转。
- 不重做后端接口；如发现字段不一致，记录后请求后端配合，不在本轮硬改。
