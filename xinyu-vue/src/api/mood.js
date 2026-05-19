// 心情记录相关接口：增删改查 + 7 天趋势统计。
// AI 回复由后端在 create/update 时自动生成，不需要前端单独调。
import { request, unwrap } from './_helpers'

export const moodApi = {
  list(params = {}) {
    return unwrap(request.get('/mood/list', { params }))
  },

  detail(id) {
    return unwrap(request.get(`/mood/detail/${id}`))
  },

  create({ moodType, content }) {
    return unwrap(request.post('/mood/add', { moodType, content }))
  },

  update(id, { moodType, content }) {
    return unwrap(request.put(`/mood/update/${id}`, { moodType, content }))
  },

  remove(id) {
    return unwrap(request.delete(`/mood/delete/${id}`))
  },

  trendRecent7() {
    return unwrap(request.get('/mood/trend/recent7'))
  }
}
