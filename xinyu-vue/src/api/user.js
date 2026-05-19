// 用户相关接口：登录 / 注册。
// 由 stores/user.js 调用，view 不直接 import。
import { request, unwrap } from './_helpers'

export const userApi = {
  login(payload) {
    return unwrap(request.post('/user/login', payload))
  },

  register(payload) {
    return unwrap(request.post('/user/register', payload))
  }
}
