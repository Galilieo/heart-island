// 心情记录相关接口：增删改查 + 7 天趋势统计。
// 保存只创建后台任务；GET查询状态，POST重试必须由用户主动触发。
import { request, unwrap } from './_helpers'

export const moodApi = {
  list(params = {}) {
    return unwrap(request.get('/mood/list', { params }))
  },

  detail(id) {
    return unwrap(request.get(`/mood/detail/${id}`))
  },

  create({ moodType, content, needAiReply = true }) {
    return unwrap(request.post('/mood/add', { moodType, content, needAiReply }))
  },

  update(id, { moodType, content, needAiReply = true }) {
    return unwrap(request.put(`/mood/update/${id}`, { moodType, content, needAiReply }))
  },

  remove(id) {
    return unwrap(request.delete(`/mood/delete/${id}`))
  },

  aiJob(id, options = {}) {
    return unwrap(request.get(`/mood/ai-job/${id}`, options))
  },

  retryAiJob(id) {
    return unwrap(request.post(`/mood/ai-job/${id}/retry`))
  },

  trendRecent7() {
    return unwrap(request.get('/mood/trend/recent7'))
  },

  // 回顾页统计：GET 只读，不生成报告、不调用模型。
  reviewWeek(start) {
    const params = start ? { start } : {}
    return unwrap(request.get('/mood/report/week', { params }))
  },

  reviewRecent30() {
    return unwrap(request.get('/mood/analysis', { params: { range: '30d' } }))
  }
}
