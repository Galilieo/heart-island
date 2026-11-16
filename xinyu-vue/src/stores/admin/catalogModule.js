import { reactive, ref } from 'vue'
import { adminApi } from '../../api'
import {
  appendNumericFilter,
  appendTrimmedFilter,
  applyPagedResult,
  createPageCount
} from './helpers'

function emptyOverview() {
  return {
    userTotal: 0,
    userDisabled: 0,
    postTotal: 0,
    postHidden: 0,
    replyTotal: 0,
    replyHidden: 0,
    aiReplyTotal: 0,
    aiReplySuccess: 0,
    aiReplyFailed: 0
  }
}

/**
 * 后台概览、用户、话题与 AI 回复日志管理。
 */
export function createCatalogModule() {
  const overview = ref(emptyOverview())
  const overviewLoading = ref(false)

  const users = ref([])
  const loading = ref(false)
  const filters = reactive({
    username: '',
    status: ''
  })
  const pagination = reactive({
    pageNum: 1,
    pageSize: 10,
    total: 0
  })
  const pages = createPageCount(
    () => pagination.total,
    () => pagination.pageSize
  )

  const topics = ref([])
  const topicsLoading = ref(false)
  const topicFilters = reactive({
    keyword: '',
    status: ''
  })

  const aiReplies = ref([])
  const aiRepliesTotal = ref(0)
  const aiRepliesLoading = ref(false)
  const aiReplyFilters = reactive({
    userId: '',
    targetType: '',
    status: '',
    keyword: '',
    pageNum: 1,
    pageSize: 10
  })
  const aiReplyPages = createPageCount(
    () => aiRepliesTotal.value,
    () => aiReplyFilters.pageSize
  )

  async function fetchOverview() {
    overviewLoading.value = true
    try {
      const res = await adminApi.overview()
      if (res.ok) {
        overview.value = {
          userTotal: res.data?.userTotal || 0,
          userDisabled: res.data?.userDisabled || 0,
          postTotal: res.data?.postTotal || 0,
          postHidden: res.data?.postHidden || 0,
          replyTotal: res.data?.replyTotal || 0,
          replyHidden: res.data?.replyHidden || 0,
          aiReplyTotal: res.data?.aiReplyTotal || 0,
          aiReplySuccess: res.data?.aiReplySuccess || 0,
          aiReplyFailed: res.data?.aiReplyFailed || 0
        }
      }
      return res
    } finally {
      overviewLoading.value = false
    }
  }

  async function fetchUsers() {
    loading.value = true
    const params = {
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
    appendTrimmedFilter(params, 'username', filters.username)
    appendNumericFilter(params, 'status', filters.status)

    try {
      const res = await adminApi.userPage(params)
      if (res.ok) {
        applyPagedResult(users, pagination, res.data)
      }
      return res
    } finally {
      loading.value = false
    }
  }

  async function updateUserStatus(id, status) {
    const res = await adminApi.updateUserStatus(id, status)
    if (res.ok) {
      await fetchUsers()
    }
    return res
  }

  function searchUsers() {
    pagination.pageNum = 1
    return fetchUsers()
  }

  function resetFilters() {
    filters.username = ''
    filters.status = ''
    pagination.pageNum = 1
    return searchUsers()
  }

  function changePage(pageNum) {
    pagination.pageNum = pageNum
    return fetchUsers()
  }

  async function fetchTopics() {
    topicsLoading.value = true
    const params = {}
    appendTrimmedFilter(params, 'keyword', topicFilters.keyword)
    appendNumericFilter(params, 'status', topicFilters.status)

    try {
      const res = await adminApi.topicList(params)
      if (res.ok) {
        topics.value = res.data || []
      }
      return res
    } finally {
      topicsLoading.value = false
    }
  }

  async function mutateTopicAndRefresh(requestPromise) {
    const res = await requestPromise
    if (res.ok) {
      await fetchTopics()
    }
    return res
  }

  function addTopic(data) {
    return mutateTopicAndRefresh(adminApi.addTopic(data))
  }

  function updateTopic(id, data) {
    return mutateTopicAndRefresh(adminApi.updateTopic(id, data))
  }

  function updateTopicStatus(id, status) {
    return mutateTopicAndRefresh(adminApi.updateTopicStatus(id, status))
  }

  function searchTopics() {
    return fetchTopics()
  }

  function resetTopicFilters() {
    topicFilters.keyword = ''
    topicFilters.status = ''
    return fetchTopics()
  }

  async function fetchAiReplies() {
    aiRepliesLoading.value = true
    const params = {
      pageNum: aiReplyFilters.pageNum,
      pageSize: aiReplyFilters.pageSize
    }
    appendTrimmedFilter(params, 'userId', aiReplyFilters.userId)
    if (aiReplyFilters.targetType !== '') {
      params.targetType = aiReplyFilters.targetType
    }
    appendNumericFilter(params, 'status', aiReplyFilters.status)
    appendTrimmedFilter(params, 'keyword', aiReplyFilters.keyword)

    try {
      const res = await adminApi.aiReplyPage(params)
      if (res.ok) {
        aiReplies.value = res.data?.records || []
        aiRepliesTotal.value = res.data?.total || 0
        aiReplyFilters.pageNum = res.data?.current || aiReplyFilters.pageNum
        aiReplyFilters.pageSize = res.data?.size || aiReplyFilters.pageSize
      }
      return res
    } finally {
      aiRepliesLoading.value = false
    }
  }

  function searchAiReplies() {
    aiReplyFilters.pageNum = 1
    return fetchAiReplies()
  }

  function resetAiReplyFilters() {
    aiReplyFilters.userId = ''
    aiReplyFilters.targetType = ''
    aiReplyFilters.status = ''
    aiReplyFilters.keyword = ''
    aiReplyFilters.pageNum = 1
    return fetchAiReplies()
  }

  function changeAiReplyPage(pageNum) {
    aiReplyFilters.pageNum = pageNum
    return fetchAiReplies()
  }

  return {
    overview,
    overviewLoading,
    fetchOverview,
    users,
    loading,
    filters,
    pagination,
    pages,
    fetchUsers,
    updateUserStatus,
    searchUsers,
    resetFilters,
    changePage,
    topics,
    topicsLoading,
    topicFilters,
    fetchTopics,
    addTopic,
    updateTopic,
    updateTopicStatus,
    searchTopics,
    resetTopicFilters,
    aiReplies,
    aiRepliesTotal,
    aiRepliesLoading,
    aiReplyFilters,
    aiReplyPages,
    fetchAiReplies,
    searchAiReplies,
    resetAiReplyFilters,
    changeAiReplyPage
  }
}
