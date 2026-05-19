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

// 响应拦截器：每次响应返回后都会执行
request.interceptors.response.use(
  response => {
    const result = response.data

    if (result && result.code !== 200) {
      const message = result.message || ''

      if (message.includes('请先登录') || message.includes('登录失效')) {
        localStorage.removeItem('loginUser')
        localStorage.removeItem('token')

        if (window.location.pathname !== '/') {
          window.location.href = '/'
        }
      }
    }

    return response
  },
  error => {
    if (error.response) {
      const result = error.response.data
      error.userMessage = result?.message || '服务异常，请稍后再试'
    } else if (error.code === 'ECONNABORTED') {
      error.userMessage = '请求超时，请稍后再试'
    } else {
      error.userMessage = '网络连接失败，请检查后端是否启动'
    }

    return Promise.reject(error)
  }
)

export default request