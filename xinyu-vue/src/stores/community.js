// 管理社区列表、详情、互动及任务观察结果。页面负责轮询的启停，
// store 只接受仍匹配帖子和任务的回复，并把在途列表期间确认的更新合并回去。
import { defineStore } from 'pinia'
import { ref, reactive, computed } from 'vue'
import { communityApi, topicApi } from '../api'
import { AI_JOB_STATE } from '../utils/ai-job-polling.js'
import { refreshAfterSave } from '../utils/saved-result.js'

export const useCommunityStore = defineStore('community', () => {
  const topics = ref([])

  const posts = ref([])
  const postsPage = ref(1)
  const postsTotal = ref(0)
  // 社区列表默认分页见 ops/features/community-report.md。
  const postsPageSize = 10
  const postsPages = computed(() => Math.max(1, Math.ceil(postsTotal.value / postsPageSize)))
  let postsRequest = 0
  let pendingViewerChanges = null
  let pendingJobChanges = null
  const favorites = ref([])
  const currentPost = ref(null)
  let detailRequest = 0
  let observationRevision = 0
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

      if (filters.topicId !== null && !topics.value.some((topic) => topic.id === filters.topicId)) {
        setTopicFilter(null)
      }
    }

    return res
  }

  async function fetchPosts(pageNum = postsPage.value) {
    const request = ++postsRequest
    const revision = observationRevision
    const viewerChanges = new Map()
    const jobChanges = new Map()
    pendingViewerChanges = viewerChanges
    pendingJobChanges = jobChanges
    let res
    try {
      res = await communityApi.postList({
        topicId: filters.topicId ?? undefined,
        mine: filters.mine || undefined,
        sort: filters.sort || undefined,
        pageNum,
        pageSize: postsPageSize
      })
    } catch (error) {
      if (request !== postsRequest) return { ok: true, skipped: true }
      throw error
    } finally {
      if (request === postsRequest) {
        pendingViewerChanges = null
        pendingJobChanges = null
      }
    }
    if (request !== postsRequest) return { ok: true, skipped: true }
    if (!res.ok) return res

    const records = res.data?.records
    const total = Number(res.data?.total)
    if (!Array.isArray(records) || !Number.isSafeInteger(total) || total < 0) {
      return { ok: false, message: '帖子列表格式有误，请刷新页面后重试' }
    }

    const lastPage = Math.max(1, Math.ceil(total / postsPageSize))
    // 删除末页最后一条后，重新拉取仍有内容的页。
    if (pageNum > lastPage && records.length === 0) {
      return fetchPosts(lastPage)
    }

    mergePostListUpdates(records, viewerChanges, jobChanges, revision)
    posts.value = records
    postsTotal.value = total
    postsPage.value = pageNum
    return res
  }

  function mergePostListUpdates(records, viewerChanges, jobChanges, revision) {
    const previous = new Map(posts.value.map((post) => [String(post.id), post]))
    for (const post of records) {
      const observed = post.mine && jobChanges.get(jobKey(post.id, post.jobId))
      if (observed) {
        Object.assign(post, observed)
      } else {
        keepObservedJob(post, previous.get(String(post.id)), revision)
      }
      const changes = viewerChanges.get(post.id)
      if (changes) applyViewerChanges(post, changes)
    }
  }

  async function fetchFavorites() {
    const res = await communityApi.myFavorites()
    if (res.ok) favorites.value = res.data || []
    return res
  }

  function setTopicFilter(topicId) {
    if (filters.topicId === topicId) return
    filters.topicId = topicId
    resetPostPage()
  }

  function setMineFilter(mine) {
    if (filters.mine === mine) return
    filters.mine = mine
    resetPostPage()
  }

  function setSort(sort) {
    if (filters.sort === sort) return
    filters.sort = sort
    resetPostPage()
  }

  function resetPostPage() {
    // 筛选或会话变更后，丢弃此前发出的列表请求。
    postsRequest++
    pendingViewerChanges = null
    pendingJobChanges = null
    postsPage.value = 1
    postsTotal.value = 0
    posts.value = []
  }

  async function publishPost(payload) {
    const res = await communityApi.createPost(payload)
    if (!res.ok) return res
    resetPostPage()
    const refreshFailed = await refreshAfterSave(fetchPosts)
    return { ...res, refreshFailed }
  }

  async function updatePost(payload) {
    const res = await communityApi.updatePost(payload)
    if (!res.ok) return res
    applySavedPost(payload, res.data)
    const refreshers = [fetchPosts]
    if (String(currentPost.value?.id) === String(payload.id)) {
      refreshers.push(() => fetchPostDetail(payload.id, { keepOnFailure: true }))
    }
    const refreshFailed = await refreshAfterSave(...refreshers)
    return { ...res, refreshFailed }
  }

  async function removePost(id) {
    const res = await communityApi.removePost(id)
    if (!res.ok) return res

    posts.value = posts.value.filter((post) => post.id !== id)
    if (currentPost.value?.id === id) {
      detailRequest++
      currentPost.value = null
    }
    const refreshFailed = await refreshAfterSave(fetchPosts)
    return { ...res, refreshFailed }
  }

  function applySavedPost(payload, saved) {
    if (String(saved?.postId) !== String(payload.id)) return
    for (const post of [currentPost.value, ...posts.value]) {
      if (!post?.mine || String(post.id) !== String(saved.postId)) continue

      Object.assign(post, {
        content: payload.content,
        moodType: payload.moodType,
        topicId: payload.topicId,
        jobId: saved.jobId,
        aiReplyState: saved.aiReplyState,
        aiReply: null,
        resultKind: null,
        aiJob: null,
        observationIssue: null,
        retryAvailableAt: 0
      })
    }
  }

  async function fetchPostDetail(id, { keepOnFailure = false } = {}) {
    const request = ++detailRequest
    const revision = observationRevision
    // 导航先清空上一篇，编辑确认后的同帖刷新保留已保存的本地视图。
    if (String(currentPost.value?.id) !== String(id)) {
      currentPost.value = null
    }
    const res = await communityApi.postDetail(id)
    if (request !== detailRequest) return { ok: true, skipped: true }

    if (res.ok) {
      keepObservedJob(res.data, currentPost.value, revision)
      currentPost.value = res.data
    } else if (!keepOnFailure) {
      currentPost.value = null
    }
    return res
  }

  function keepObservedJob(post, previous, requestRevision) {
    if (!post?.mine || !previous?.mine || !previous.aiJob || !samePostJob(post, previous.aiJob)) {
      return
    }
    if (previous.jobRevision <= requestRevision && post.aiReplyState !== previous.aiReplyState) {
      return
    }

    // 同一任务的详情和轮询可并行；较早开始的读不能丢掉已观察到的结果。
    Object.assign(post, {
      aiReplyState: previous.aiReplyState,
      aiReply: previous.aiReply,
      resultKind: previous.resultKind,
      aiJob: previous.aiJob,
      jobRevision: previous.jobRevision,
      observationIssue: previous.observationIssue,
      retryAvailableAt: previous.retryAvailableAt
    })
  }

  async function fetchAiJob(jobId, options) {
    const result = await communityApi.aiJob(jobId, options)
    if (!result.ok) {
      throw new Error(result.message)
    }
    return result.data
  }

  function retryAiJob(jobId) {
    return communityApi.retryAiJob(jobId)
  }

  function matchingPosts(job) {
    return [currentPost.value, ...posts.value].filter(
      (post) => post?.mine && !post.hidden && samePostJob(post, job)
    )
  }

  function samePostJob(post, job) {
    return String(post.id) === String(job.targetId) && String(post.jobId) === String(job.id)
  }

  function jobKey(postId, jobId) {
    return `${postId}:${jobId}`
  }

  function applyAiJob(job) {
    const observation = {
      aiReplyState: job.status,
      aiReply: job.status === AI_JOB_STATE.SUCCEEDED ? job.replyContent : null,
      resultKind: job.resultKind,
      aiJob: job,
      jobRevision: ++observationRevision,
      observationIssue: null,
      retryAvailableAt: Date.now() + (job.retryAfterSeconds || 0) * 1000
    }
    pendingJobChanges?.set(jobKey(job.targetId, job.id), observation)
    for (const post of matchingPosts(job)) {
      Object.assign(post, observation)
    }
  }

  async function refreshAiReply(job, signal) {
    if (!matchingPosts(job).length) return

    const result = await communityApi.postDetail(job.targetId, { signal })
    const current = result.data
    if (signal.aborted || !result.ok || !current?.mine || !samePostJob(current, job)) return

    for (const post of matchingPosts(job)) {
      post.aiReply = current.aiReply
      post.resultKind = current.resultKind
    }
  }

  async function toggleLike(post) {
    const next = !post.liked
    const action = next ? communityApi.like : communityApi.unlike
    const res = await action(post.id)
    if (res.ok) {
      updateViewerState(post, { liked: next })
    }
    return res
  }

  async function toggleFavorite(post) {
    const next = !post.favorited
    const action = next ? communityApi.favorite : communityApi.unfavorite
    const res = await action(post.id)
    if (res.ok) {
      updateViewerState(post, { favorited: next })
      // 取消收藏后，从收藏列表里移除这条
      if (!next) {
        favorites.value = favorites.value.filter((p) => p.id !== post.id)
      }
    }
    return res
  }

  function updateViewerState(post, changes) {
    // 请求发出后才确认的互动可能尚未包含在列表响应中。
    if (pendingViewerChanges) {
      const previous = pendingViewerChanges.get(post.id)
      pendingViewerChanges.set(post.id, { ...previous, ...changes })
    }

    const matches = (item) => item?.id === post.id
    const views = [
      post,
      posts.value.find(matches),
      favorites.value.find(matches),
      currentPost.value
    ]
    for (const target of new Set(views.filter(matches))) {
      applyViewerChanges(target, changes)
    }
  }

  function applyViewerChanges(post, { liked, favorited }) {
    // 若返回的快照已含本次点赞，只保留原计数。
    if (liked !== undefined && post.liked !== liked) {
      post.liked = liked
      post.likeCount = Math.max(0, (post.likeCount || 0) + (liked ? 1 : -1))
    }
    if (favorited !== undefined) {
      post.favorited = favorited
    }
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
    if (!res.ok) return res

    const refreshFailed = await refreshAfterSave(() => fetchReplies(payload.postId))
    return { ...res, refreshFailed }
  }

  async function updateReply(payload, postId) {
    const res = await communityApi.updateReply(payload)
    if (!res.ok || !postId) return res

    const refreshFailed = await refreshAfterSave(() => fetchReplies(postId))
    return { ...res, refreshFailed }
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
    detailRequest++
    resetPostPage()
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
    postsPage,
    postsTotal,
    postsPageSize,
    postsPages,
    favorites,
    currentPost,
    replies,
    reportedTargets,
    myReports,
    myReportsTotal,
    myReportsPage,
    myReportsPageSize,
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
    fetchAiJob,
    retryAiJob,
    applyAiJob,
    refreshAiReply,
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
