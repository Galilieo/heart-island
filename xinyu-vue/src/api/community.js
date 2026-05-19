import { request, unwrap } from './_helpers'

export const communityApi = {
  /* ───── posts ───── */

  postList(params = {}) {
    return unwrap(request.get('/community/post/list', { params }))
  },

  postDetail(id) {
    return unwrap(request.get('/community/post/detail', { params: { id } }))
  },

  createPost(payload) {
    return unwrap(request.post('/community/post/add', payload))
  },

  updatePost(payload) {
    return unwrap(request.post('/community/post/update', payload))
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
