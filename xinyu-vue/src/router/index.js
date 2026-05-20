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

const routes = [
  { path: '/', component: Login },
  { path: '/home', component: Home, meta: { requiresAuth: true } },
  { path: '/register', component: Register },
  { path: '/posts', component: PostList, meta: { requiresAuth: true } },
  { path: '/community/post/:id', component: PostDetail, meta: { requiresAuth: true } },
  { path: '/favorites', component: Favorites, meta: { requiresAuth: true } }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to) => {
  const token = localStorage.getItem('token')

  if (to.meta.requiresAuth && !token) {
    return '/'
  }

  if ((to.path === '/' || to.path === '/register') && token) {
    return '/home'
  }

  return true
})

export default router
