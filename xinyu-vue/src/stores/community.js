// 社区 store：话题 / 帖子列表 / 当前帖子详情 / 回复列表。
// 设计思路：
//  - PostList 关心 posts + filters；PostDetail 关心 currentPost + replies
//  - toggleLike/toggleFavorite 走"乐观更新"：先把本地状态翻转，
//    后端返回失败时再 toast 错误（这一步由 view 接住）
import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { communityApi, topicApi } from '../api'
import { refreshAfterSave } from '../utils/saved-result.js'

export const useCommunityStore = defineStore('community', () => {
  const topics = ref([])

  const posts = ref([])
  const favorites = ref([])
  const currentPost = ref(null)
  const replies = ref([])
  const reportedTargets = ref({})

  const myReports = ref([])
  const myReportsTotal = ref(0)
  const myReportsPage = ref(1)
  const myReportsPageSize = 10
  const myReportsLoading = ref(false)
  const myReportsPages = computed(() =>
    Math.max(1, Math.ceil(myReportsTotal.value / myReportsPageSize))
  )

  const filters = reactive({
    topicId: null,
    mine: false,
    sort: 'latest' // latest | hot
  })

  async function fetchTopics() {
    const res = await topicApi.list()

    if (res.ok) {
      topics.value = res.data || []

      if (
        filters.topicId !== null &&
        !topics.value.some((topic) => topic.id === filters.topicId)
      ) {
        filters.topicId = null
      }
    }

    return res
  }

  async function fetchPosts() {
    const res = await communityApi.postList({
      topicId: filters.topicId ?? undefined,
      mine: filters.mine || undefined,
      sort: filters.sort || undefined
    })
    if (res.ok) posts.value = res.data || []
    return res
  }

  async function fetchFavorites() {
    const res = await communityApi.myFavorites()
    if (res.ok) favorites.value = res.data || []
    return res
  }

  function setTopicFilter(topicId) {
    filters.topicId = topicId
  }

  function setMineFilter(mine) {
    filters.mine = mine
  }

  function setSort(sort) {
    filters.sort = sort
  }

  async function publishPost(payload) {
    const res = await communityApi.createPost(payload)
    if (!res.ok) return res
    const refreshFailed = await refreshAfterSave(fetchPosts)
    return { ...res, refreshFailed }
  }

  async function updatePost(payload) {
    const res = await communityApi.updatePost(payload)
    if (!res.ok) return res
    const refreshers = [fetchPosts]
    if (currentPost.value && currentPost.value.id === payload.id) {
      refreshers.push(() => fetchPostDetail(payload.id))
    }
    const refreshFailed = await refreshAfterSave(...refreshers)
    return { ...res, refreshFailed }
  }

  async function removePost(id) {
    const res = await communityApi.removePost(id)
    if (res.ok) {
      posts.value = posts.value.filter((p) => p.id !== id)
      if (currentPost.value && currentPost.value.id === id) {
        currentPost.value = null
      }
    }
    return res
  }

  async function fetchPostDetail(id) {
    // 切换帖子或目标被隐藏/删除时立刻清空旧详情，避免残留上一篇内容
    currentPost.value = null
    const res = await communityApi.postDetail(id)
    if (res.ok) {
      currentPost.value = res.data
    }
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
      // 取消收藏后，从收藏列表里移除这条
      if (!next) {
        favorites.value = favorites.value.filter((p) => p.id !== post.id)
      }
    }
    return res
  }

  /* ───── replies ───── */

  async function fetchReplies(postId) {
    replies.value = []
    const res = await communityApi.replyList({ postId })
    if (res.ok) {
      replies.value = res.data || []
    }
    return res
  }

  async function publishReply(payload) {
    const res = await communityApi.createReply(payload)
    if (res.ok) {
      await fetchReplies(payload.postId)
    }
    return res
  }

  async function updateReply(payload, postId) {
    const res = await communityApi.updateReply(payload)
    if (res.ok && postId) {
      await fetchReplies(postId)
    }
    return res
  }

  async function removeReply(id, postId) {
    const res = await communityApi.removeReply(id)
    if (res.ok) {
      replies.value = replies.value.filter((r) => r.id !== id)
      if (postId) {
        await fetchReplies(postId)
      }
    }
    return res
  }

  async function fetchReportedTargets(postId) {
    if (!postId) {
      reportedTargets.value = {}
      return { ok: true, data: [] }
    }
    const res = await communityApi.reportedTargets(postId)
    if (res.ok) {
      const next = {}
      for (const item of res.data || []) {
        next[`${item.targetType}:${item.targetId}`] = item.status
      }
      reportedTargets.value = next
    }
    return res
  }

  async function submitReport(payload) {
    const res = await communityApi.report(payload)
    if (res.ok && payload?.targetType && payload?.targetId) {
      reportedTargets.value = {
        ...reportedTargets.value,
        [`${payload.targetType}:${payload.targetId}`]: 'PENDING'
      }
    }
    return res
  }

  async function fetchMyReports(pageNum = 1) {
    myReportsLoading.value = true
    myReportsPage.value = pageNum
    try {
      const res = await communityApi.myReports({
        pageNum,
        pageSize: myReportsPageSize
      })
      if (res.ok) {
        myReports.value = res.data?.records || []
        myReportsTotal.value = res.data?.total || 0
      }
      return res
    } finally {
      myReportsLoading.value = false
    }
  }

  function reset() {
    posts.value = []
    currentPost.value = null
    replies.value = []
    favorites.value = []
    reportedTargets.value = {}
    myReports.value = []
    myReportsTotal.value = 0
    myReportsPage.value = 1
    filters.topicId = null
    filters.mine = false
    filters.sort = 'latest'
  }

  return {
    topics,
    posts,
    favorites,
    currentPost,
    replies,
    reportedTargets,
    myReports,
    myReportsTotal,
    myReportsPage,
    myReportsPages,
    myReportsLoading,
    filters,
    fetchTopics,
    fetchPosts,
    fetchFavorites,
    setTopicFilter,
    setMineFilter,
    setSort,
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
    fetchReportedTargets,
    submitReport,
    fetchMyReports,
    reset
  }
})
