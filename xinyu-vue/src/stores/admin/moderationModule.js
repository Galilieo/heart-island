import { reactive, ref } from 'vue'
import { adminApi } from '../../api'
import {
  appendNumericFilter,
  appendTrimmedFilter,
  applyPagedResult,
  createPageCount
} from './helpers'

/**
 * 后台帖子与回复审核状态管理（按域拆出，外部仍通过 useAdminStore 统一暴露）。
 */
export function createModerationModule() {
  const posts = ref([])
  const postsLoading = ref(false)
  const postsFilters = reactive({
    resultKind: '',
    status: '',
    topicId: '',
    postId: '',
    userId: '',
    keyword: ''
  })
  const postPagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  })
  const postPages = createPageCount(
    () => postPagination.total,
    () => postPagination.pageSize
  )

  const replies = ref([])
  const repliesLoading = ref(false)
  const replyFilters = reactive({
    status: '',
    postId: '',
    userId: '',
    keyword: ''
  })
  const replyPagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  })
  const replyPages = createPageCount(
    () => replyPagination.total,
    () => replyPagination.pageSize
  )

  async function refreshOnSuccessOrConflict(requestPromise, refreshFn) {
    const res = await requestPromise
    if (res.ok || res.status === 409) {
      await refreshFn()
    }
    return res
  }

  async function fetchPosts() {
    postsLoading.value = true
    const params = {
      pageNum: postPagination.pageNum,
      pageSize: postPagination.pageSize
    }
    appendNumericFilter(params, 'status', postsFilters.status)
    appendNumericFilter(params, 'topicId', postsFilters.topicId)
    appendTrimmedFilter(params, 'postId', postsFilters.postId)
    appendTrimmedFilter(params, 'userId', postsFilters.userId)
    appendTrimmedFilter(params, 'keyword', postsFilters.keyword)
    appendTrimmedFilter(params, 'resultKind', postsFilters.resultKind)

    try {
      const res = await adminApi.postPage(params)
      if (res.ok) {
        applyPagedResult(posts, postPagination, res.data)
      }
      return res
    } finally {
      postsLoading.value = false
    }
  }

  function updatePostStatus(id, status, hideReason) {
    return refreshOnSuccessOrConflict(adminApi.updatePostStatus(id, status, hideReason), fetchPosts)
  }

  function removePost(id, hideReason) {
    return refreshOnSuccessOrConflict(adminApi.removePost(id, hideReason), fetchPosts)
  }

  function fetchPostDetail(id) {
    return adminApi.postDetail(id)
  }

  function searchPosts() {
    postPagination.pageNum = 1
    return fetchPosts()
  }

  function resetPostFilters() {
    postsFilters.status = ''
    postsFilters.topicId = ''
    postsFilters.postId = ''
    postsFilters.userId = ''
    postsFilters.keyword = ''
    postsFilters.resultKind = ''
    postPagination.pageNum = 1
    return searchPosts()
  }

  function changePostPage(pageNum) {
    postPagination.pageNum = pageNum
    return fetchPosts()
  }

  async function fetchReplies() {
    repliesLoading.value = true
    const params = {
      pageNum: replyPagination.pageNum,
      pageSize: replyPagination.pageSize
    }
    appendNumericFilter(params, 'status', replyFilters.status)
    appendTrimmedFilter(params, 'postId', replyFilters.postId)
    appendTrimmedFilter(params, 'userId', replyFilters.userId)
    appendTrimmedFilter(params, 'keyword', replyFilters.keyword)

    try {
      const res = await adminApi.replyPage(params)
      if (res.ok) {
        applyPagedResult(replies, replyPagination, res.data)
      }
      return res
    } finally {
      repliesLoading.value = false
    }
  }

  function updateReplyStatus(id, status, hideReason) {
    return refreshOnSuccessOrConflict(
      adminApi.updateReplyStatus(id, status, hideReason),
      fetchReplies
    )
  }

  function removeReply(id, hideReason) {
    return refreshOnSuccessOrConflict(adminApi.removeReply(id, hideReason), fetchReplies)
  }

  function fetchReplyDetail(id) {
    return adminApi.replyDetail(id)
  }

  function searchReplies() {
    replyPagination.pageNum = 1
    return fetchReplies()
  }

  function resetReplyFilters() {
    replyFilters.status = ''
    replyFilters.postId = ''
    replyFilters.userId = ''
    replyFilters.keyword = ''
    replyPagination.pageNum = 1
    return fetchReplies()
  }

  function changeReplyPage(pageNum) {
    replyPagination.pageNum = pageNum
    return fetchReplies()
  }

  return {
    posts,
    postsLoading,
    postsFilters,
    postPagination,
    postPages,
    fetchPosts,
    fetchPostDetail,
    updatePostStatus,
    removePost,
    searchPosts,
    resetPostFilters,
    changePostPage,
    replies,
    repliesLoading,
    replyFilters,
    replyPagination,
    replyPages,
    fetchReplies,
    fetchReplyDetail,
    updateReplyStatus,
    removeReply,
    searchReplies,
    resetReplyFilters,
    changeReplyPage
  }
}
