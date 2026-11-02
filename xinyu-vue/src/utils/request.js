import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 5000
})

// 请求拦截器：每次请求发出前都会执行
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')

    if (token) {
      config.headers.token = token
    }

    return config
  }
)

function clearSessionAndRedirect() {
  localStorage.removeItem('loginUser')
  localStorage.removeItem('token')
  if (window.location.pathname !== '/') window.location.href = '/'
}

// 响应拦截器：每次响应返回后都会执行
request.interceptors.response.use(
  response => {
    const result = response.data

    if (result && result.code !== 200) {
      const message = result.message || ''

      if (message.includes('请先登录') || message.includes('登录失效')) {
        clearSessionAndRedirect()
      }
    }

    return response
  },
  error => {
    if (error.response) {
      if (error.response.status === 401) clearSessionAndRedirect()
      const result = error.response.data
      error.userMessage = result?.message || '服务异常，请稍后再试'
    } else if (error.code === 'ECONNABORTED') {
      error.userMessage = '请求超时，请稍后再试'
    } else {
      error.userMessage = '暂时连接不上，请检查网络或稍后再试'
    }

    return Promise.reject(error)
  }
)

export default request