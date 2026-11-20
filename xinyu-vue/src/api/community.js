// 社区相关接口：帖子 + 回复。
// 注意后端 like/unlike/favorite/unfavorite/delete 用的是 query param
// （不是请求体），所以这里都走 params 形式。
import { request, unwrap, unwrapHttp } from './_helpers'

async function savePost(url, payload) {
  try {
    return await unwrap(request.post(url, payload))
  } catch (error) {
    // 未收到响应无法判断是否已落库；保留输入，由用户刷新核对，不自动重发。
    if (!error.response) {
      error.userMessage = '发布或修改结果尚未确认，帖子可能已保存，请先刷新核对，避免重复提交。'
    }
    throw error
  }
}

export const communityApi = {
  /* ───── posts ───── */

  postList(params = {}) {
    return unwrap(request.get('/community/post/list', { params }))
  },

  // 当前用户收藏的帖子列表
  myFavorites() {
    return unwrap(request.get('/community/post/favorites'))
  },

  postDetail(id, options) {
    return unwrap(request.get('/community/post/detail', { ...options, params: { id } }))
  },

  createPost(payload) {
    return savePost('/community/post/add', payload)
  },

  updatePost(payload) {
    return savePost('/community/post/update', payload)
  },

  aiJob(id, options) {
    return unwrap(request.get(`/community/ai-job/${id}`, options))
  },

  retryAiJob(id) {
    return unwrapHttp(request.post(`/community/ai-job/${id}/retry`))
  },

  removePost(id) {
    return unwrap(request.post('/community/post/delete', null, { params: { id } }))
  },

  like(postId) {
    return unwrap(request.post('/community/post/like', null, { params: { postId } }))
  },

  unlike(postId) {
    return unwrap(request.post('/community/post/unlike', null, { params: { postId } }))
  },

  favorite(postId) {
    return unwrap(request.post('/community/post/favorite', null, { params: { postId } }))
  },

  unfavorite(postId) {
    return unwrap(request.post('/community/post/unfavorite', null, { params: { postId } }))
  },

  /* ───── replies ───── */

  replyList(params = {}) {
    return unwrap(request.get('/community/reply/list', { params }))
  },

  createReply(payload) {
    return unwrap(request.post('/community/reply/add', payload))
  },

  updateReply(payload) {
    return unwrap(request.post('/community/reply/update', payload))
  },

  removeReply(id) {
    return unwrap(request.post('/community/reply/delete', null, { params: { id } }))
  },

  /* ───── reports ───── */

  report(payload) {
    return unwrapHttp(request.post('/community/report', payload))
  },

  myReports(params = {}) {
    return unwrapHttp(request.get('/community/report/mine', { params }))
  },

  reportedTargets(postId) {
    return unwrapHttp(request.get('/community/report/reported-targets', { params: { postId } }))
  }
}
