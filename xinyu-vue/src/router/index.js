// 路由表 + 全局守卫。
// 守卫规则：
//   - 带 requiresAuth 的页面，未登录跳 /
//   - 已登录访问 / 或 /register 自动跳 /home
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/privacy', component: () => import('../views/Privacy.vue'), meta: { title: '隐私政策' } },
  { path: '/', component: () => import('../views/Login.vue'), meta: { title: '登录' } },
  {
    path: '/home',
    component: () => import('../views/Home.vue'),
    meta: { title: '心情', requiresAuth: true },
  },
  { path: '/register', component: () => import('../views/Register.vue'), meta: { title: '注册' } },
  {
    path: '/posts',
    component: () => import('../views/PostList.vue'),
    meta: { title: '社区', requiresAuth: true },
  },
  {
    path: '/community/post/:id',
    component: () => import('../views/PostDetail.vue'),
    meta: { title: '帖子详情', requiresAuth: true },
  },
  {
    path: '/favorites',
    component: () => import('../views/Favorites.vue'),
    meta: { title: '收藏', requiresAuth: true },
  },
  {
    path: '/profile',
    component: () => import('../views/Profile.vue'),
    meta: { title: '我的', requiresAuth: true },
  },
  // 后台改为嵌套路由：父级提供独立布局，子路由只负责页面内容。
  // 网址、路由名称和 requiresAuth / requiresAdmin 的效果保持不变；
  // 权限放在父路由，Vue Router 会把父级 meta 合并到子路由的 to.meta 上。
  {
    path: '/admin',
    component: () => import('../components/admin/AdminLayout.vue'),
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      {
        path: '',
        name: 'AdminDashboard',
        component: () => import('../views/AdminDashboard.vue'),
        meta: { title: '概览' },
      },
      {
        path: 'users',
        component: () => import('../views/AdminUsers.vue'),
        meta: { title: '用户管理' },
      },
      {
        path: 'posts',
        component: () => import('../views/AdminPosts.vue'),
        meta: { title: '帖子管理' },
      },
      {
        path: 'replies',
        component: () => import('../views/AdminReplies.vue'),
        meta: { title: '回复管理' },
      },
      {
        path: 'topics',
        component: () => import('../views/AdminTopics.vue'),
        meta: { title: '话题管理' },
      },
      {
        path: 'ai-replies',
        name: 'AdminAiReplies',
        component: () => import('../views/AdminAiReplies.vue'),
        meta: { title: 'AI 记录' },
      },
    ],
  },
  // 设计对比页只在开发服务器注册，生产构建不包含该路由及其代码。
  ...(import.meta.env.DEV
    ? [{ path: '/design-lab', component: () => import('../design-lab/DesignLab.vue'), meta: { title: '设计对比' } }]
    : []),
  // 未知地址由登录守卫送回心情页或登录页。
  { path: '/:pathMatch(.*)*', redirect: '/home' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  // 返回时恢复滚动位置，前进新页面回到顶部
  scrollBehavior(to, from, savedPosition) {
    return savedPosition || { top: 0 }
  },
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  const rawUser = localStorage.getItem('loginUser')

  let loginUser
  try {
    loginUser = rawUser ? JSON.parse(rawUser) : null
  } catch {
    loginUser = null
  }

  if (to.meta.requiresAuth && !token) {
    return '/'
  }

  if (to.meta.requiresAdmin && loginUser?.role !== 'ADMIN') {
    return '/home'
  }

  if ((to.path === '/' || to.path === '/register') && token) {
    return '/home'
  }

  return true
})

router.afterEach((to, _from, failure) => {
  if (!failure) document.title = `${to.meta.title || '心情'} · 心屿`
})

export default router
