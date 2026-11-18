// 管理员相关接口：用户管理、后续帖子审核、话题管理等。
// api 层只负责“请求哪个后端接口”，不负责页面展示。
import { request, unwrap, unwrapHttp } from './_helpers'

export const adminApi = {
  overview() {
    return unwrap(request.get('/admin/overview'))
  },

  userPage(params = {}) {
    return unwrap(request.get('/admin/user/page', { params }))
  },

  updateUserStatus(id, status) {
    return unwrap(request.put(`/admin/user/${id}/status`, { status }))
  },

  postPage(params = {}) {
    return unwrap(request.get('/admin/post/page', { params }))
  },

  postDetail(id) {
    return unwrapHttp(request.get(`/admin/post/${id}`))
  },

  aiReplyPage(params = {}) {
    return unwrap(request.get('/admin/ai-replies/page', { params }))
  },

  updatePostStatus(id, status, hideReason) {
    const params = hideReason ? { status, hideReason } : { status }
    return unwrapHttp(request.put(`/admin/post/${id}/status`, null, { params }))
  },

  removePost(id, hideReason) {
    return unwrapHttp(request.delete(`/admin/post/${id}`, { params: { hideReason } }))
  },

  replyPage(params = {}) {
    return unwrap(request.get('/admin/reply/page', { params }))
  },

  replyDetail(id) {
    return unwrapHttp(request.get(`/admin/reply/${id}`))
  },

  updateReplyStatus(id, status, hideReason) {
    const params = hideReason ? { status, hideReason } : { status }
    return unwrapHttp(request.put(`/admin/reply/${id}/status`, null, { params }))
  },

  removeReply(id, hideReason) {
    return unwrapHttp(request.delete(`/admin/reply/${id}`, { params: { hideReason } }))
  },

  reportPage(params = {}) {
    return unwrapHttp(request.get('/admin/report/page', { params }))
  },

  reportDetail(targetType, targetId) {
    return unwrapHttp(request.get('/admin/report/detail', { params: { targetType, targetId } }))
  },

  handleReport(payload) {
    return unwrapHttp(request.post('/admin/report/handle', payload))
  },

  reportPendingCount() {
    return unwrapHttp(request.get('/admin/report/pending-count'))
  },

  topicList(params = {}) {
    return unwrap(request.get('/admin/topic/list', { params }))
  },

  addTopic(data) {
    return unwrap(request.post('/admin/topic', data))
  },

  updateTopic(id, data) {
    return unwrap(request.put(`/admin/topic/${id}`, data))
  },

  updateTopicStatus(id, status) {
    return unwrap(
      request.put(`/admin/topic/${id}/status`, null, {
        params: { status }
      })
    )
  }
}
