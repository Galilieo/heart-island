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
