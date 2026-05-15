import axios from 'axios'

const request = axios.create({
    baseURL: 'http://localhost:8080',
    timeout:5000
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

export default request