// 社区相关接口：帖子 + 回复。
// 注意后端 like/unlike/favorite/unfavorite/delete 用的是 query param
// （不是请求体），所以这里都走 params 形式。
import { request, unwrap } from './_helpers'

// 已用有限真实调用校准：同步社区生成使用独立20秒等待，不改变全局5秒。
const SAVE_TIMEOUT_MS = 20_000

async function savePost(url, payload) {
  try {
    return await unwrap(request.post(url, payload, { timeout: SAVE_TIMEOUT_MS }))
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

  postDetail(id) {
    return unwrap(request.get('/community/post/detail', { params: { id } }))
  },

  createPost(payload) {
    return savePost('/community/post/add', payload)
  },

  updatePost(payload) {
    return savePost('/community/post/update', payload)
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
  }
}
