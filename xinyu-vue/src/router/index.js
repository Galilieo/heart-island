// 路由表 + 全局守卫。
// 守卫规则：
//   - 带 requiresAuth 的页面，未登录跳 /
//   - 已登录访问 / 或 /register 自动跳 /home
import { createRouter, createWebHistory } from 'vue-router'

import Login from '../views/Login.vue'
import Home from '../views/Home.vue'
import Register from '../views/Register.vue'
import PostList from '../views/PostList.vue'
import PostDetail from '../views/PostDetail.vue'
import Favorites from '../views/Favorites.vue'
import Profile from '../views/Profile.vue'
import AdminUsers from '../views/AdminUsers.vue'
import AdminPosts from '../views/AdminPosts.vue'
import AdminReplies from '../views/AdminReplies.vue'
import AdminDashboard from '../views/AdminDashboard.vue'
import AdminTopics from '../views/AdminTopics.vue'
import AdminAiReplies from '../views/AdminAiReplies.vue'

const routes = [
  { path: '/', component: Login },
  { path: '/home', component: Home, meta: { requiresAuth: true } },
  { path: '/register', component: Register },
  { path: '/posts', component: PostList, meta: { requiresAuth: true } },
  { path: '/community/post/:id', component: PostDetail, meta: { requiresAuth: true } },
  { path: '/favorites', component: Favorites, meta: { requiresAuth: true } },
  { path: '/profile', component: Profile, meta: { requiresAuth: true } },
  {path:'/admin',name:'AdminDashboard',component:AdminDashboard,meta:{requiresAuth:true,requiresAdmin:true}},
  {
    path: '/admin/users', component: AdminUsers, meta: { requiresAuth: true, requiresAdmin: true }
  },
  { path: '/admin/posts', component: AdminPosts, meta: { requiresAuth: true, requiresAdmin: true } },
  {
    path: '/admin/replies', component: AdminReplies, meta: { requiresAuth: true, requiresAdmin: true }
  },
  {
    path: '/admin/topics', component: AdminTopics, meta: { requiresAuth: true, requiresAdmin: true }
  },
  { path: '/admin/ai-replies', name: 'AdminAiReplies', component: AdminAiReplies, meta: { requiresAuth: true, requiresAdmin: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
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

export default router
