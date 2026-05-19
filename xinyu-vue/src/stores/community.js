import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { communityApi, topicApi } from '../api'

export const useCommunityStore = defineStore('community', () => {
  const topics = ref([])

  const posts = ref([])
  const currentPost = ref(null)
  const replies = ref([])

  const filters = reactive({
    topicId: null,
    mine: false
  })

  async function fetchTopics() {
    if (topics.value.length) return { ok: true, data: topics.value }
    const res = await topicApi.list()
    if (res.ok) topics.value = res.data || []
    return res
  }

  async function fetchPosts() {
    const res = await communityApi.postList({
      topicId: filters.topicId ?? undefined,
      mine: filters.mine || undefined
    })
    if (res.ok) posts.value = res.data || []
    return res
  }

  function setTopicFilter(topicId) {
    filters.topicId = topicId
  }

  function setMineFilter(mine) {
    filters.mine = mine
  }

  async function publishPost(payload) {
    const res = await communityApi.createPost(payload)
    if (res.ok) await fetchPosts()
    return res
  }

  async function updatePost(payload) {
    const res = await communityApi.updatePost(payload)
    if (res.ok) {
      await fetchPosts()
      if (currentPost.value && currentPost.value.id === payload.id) {
        await fetchPostDetail(payload.id)
      }
    }
    return res
  }

  async function removePost(id) {
    const res = await communityApi.removePost(id)
    if (res.ok) {
      posts.value = posts.value.filter((p) => p.id !== id)
      if (currentPost.value && currentPost.value.id === id) currentPost.value = null
    }
    return res
  }

  async function fetchPostDetail(id) {
    const res = await communityApi.postDetail(id)
    if (res.ok) currentPost.value = res.data
    return res
  }

  async function toggleLike(post) {
    const action = post.liked ? communityApi.unlike : communityApi.like
    const res = await action(post.id)
    if (res.ok) {
      const next = !post.liked
      post.liked = next
      post.likeCount = Math.max(0, (post.likeCount || 0) + (next ? 1 : -1))
      if (currentPost.value && currentPost.value.id === post.id) {
        currentPost.value.liked = next
        currentPost.value.likeCount = post.likeCount
      }
    }
    return res
  }

  async function toggleFavorite(post) {
    const action = post.favorited ? communityApi.unfavorite : communityApi.favorite
    const res = await action(post.id)
    if (res.ok) {
      const next = !post.favorited
      post.favorited = next
      if (currentPost.value && currentPost.value.id === post.id) {
        currentPost.value.favorited = next
      }
    }
    return res
  }

  /* ───── replies ───── */

  async function fetchReplies(postId) {
    const res = await communityApi.replyList({ postId })
    if (res.ok) replies.value = res.data || []
    return res
  }

  async function publishReply(payload) {
    const res = await communityApi.createReply(payload)
    if (res.ok) await fetchReplies(payload.postId)
    return res
  }

  async function updateReply(payload, postId) {
    const res = await communityApi.updateReply(payload)
    if (res.ok && postId) await fetchReplies(postId)
    return res
  }

  async function removeReply(id, postId) {
    const res = await communityApi.removeReply(id)
    if (res.ok) {
      replies.value = replies.value.filter((r) => r.id !== id)
      if (postId) await fetchReplies(postId)
    }
    return res
  }

  function reset() {
    posts.value = []
    currentPost.value = null
    replies.value = []
    filters.topicId = null
    filters.mine = false
  }

  return {
    topics,
    posts,
    currentPost,
    replies,
    filters,
    fetchTopics,
    fetchPosts,
    setTopicFilter,
    setMineFilter,
    publishPost,
    updatePost,
    removePost,
    fetchPostDetail,
    toggleLike,
    toggleFavorite,
    fetchReplies,
    publishReply,
    updateReply,
    removeReply,
    reset
  }
})
