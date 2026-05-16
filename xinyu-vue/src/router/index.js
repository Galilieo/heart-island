import { createRouter, createWebHistory } from 'vue-router'

import Login from '../views/Login.vue'
import Home from '../views/Home.vue'
import Register from '../views/Register.vue'

const routes = [
  { path: '/', component: Login },
  { path: '/home', component: Home,meta:{requiresAuth: true} },
  { path: '/register', component: Register }
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