// 用户相关接口：登录 / 注册。
// 由 stores/user.js 调用，view 不直接 import。
import { request, unwrap } from './_helpers'

export const userApi = {
  login(payload) {
    return unwrap(request.post('/user/login', payload))
  },

  register(payload) {
    return unwrap(request.post('/user/register', payload))
  },

  // 修改个人资料（昵称 / 性别 / 生日 / 签名 / 城市）
  updateProfile(payload) {
    return unwrap(request.post('/user/update-profile', payload))
  },

  // 修改密码
  updatePassword(payload) {
    return unwrap(request.post('/user/update-password', payload))
  },

  // 个人资料页顶部数据小条
  profileStats() {
    return unwrap(request.get('/user/profile-stats'))
  }
}
