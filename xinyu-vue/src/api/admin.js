// 管理员相关接口：用户管理、后续帖子审核、话题管理等。
// api 层只负责“请求哪个后端接口”，不负责页面展示。
import { request, unwrap } from './_helpers'

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

  aiReplyPage(params = {}) {
    return unwrap(request.get('/admin/ai-replies/page', { params }))
  },

  updatePostStatus(id, status) {
    return unwrap(request.put(`/admin/post/${id}/status`, null, {
      params: { status }
    }))
  },

  removePost(id) {
    return unwrap(request.delete(`/admin/post/${id}`))
  },

  replyPage(params = {}) {
    return unwrap(request.get('/admin/reply/page', { params }))
  },

  updateReplyStatus(id, status) {
    return unwrap(request.put(`/admin/reply/${id}/status`, null, {
      params: { status }
    }))
  },
  
  removeReply(id) {
    return unwrap(request.delete(`/admin/reply/${id}`))
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
    return unwrap(request.put(`/admin/topic/${id}/status`, null, {
      params: { status }
    }))
  }

}
