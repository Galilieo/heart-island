// 用户 store：登录态 + 用户信息的唯一来源。
// view 不再直接读 localStorage，统一通过这个 store。
// 持久化策略：token + loginUser 同步写入 localStorage，刷新页面后自动恢复。
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '../api'

const TOKEN_KEY = 'token'
const USER_KEY = 'loginUser'

export const useUserStore = defineStore('user', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')

  const loginUser = ref(
    (() => {
      try {
        const raw = localStorage.getItem(USER_KEY)
        return raw ? JSON.parse(raw) : null
      } catch {
        return null
      }
    })()
  )

  const isLoggedIn = computed(() => Boolean(token.value))

  // 设置/清空登录态：传 null 视为登出
  function setSession(payload) {
    token.value = payload?.token || ''
    loginUser.value = payload?.user || null

    if (token.value) {
      localStorage.setItem(TOKEN_KEY, token.value)
    } else {
      localStorage.removeItem(TOKEN_KEY)
    }

    if (loginUser.value) {
      localStorage.setItem(USER_KEY, JSON.stringify(loginUser.value))
    } else {
      localStorage.removeItem(USER_KEY)
    }
  }

  async function login({ username, password }) {
    const res = await userApi.login({ username, password })
    if (res.ok) {
      setSession(res.data)
    }
    return res
  }

  async function register(payload) {
    return await userApi.register(payload)
  }

  function logout() {
    setSession(null)
  }

  return {
    token,
    loginUser,
    isLoggedIn,
    login,
    register,
    logout,
    setSession
  }
})
