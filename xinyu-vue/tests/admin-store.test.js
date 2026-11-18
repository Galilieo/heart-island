import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'

if (!globalThis.localStorage) {
  globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
}

const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

let useAdminStore
let request
try {
  useAdminStore = (await server.ssrLoadModule('/src/stores/admin.js')).useAdminStore
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

const ok = (config, data) => ({
  status: 200,
  data: { code: 200, message: 'OK', data },
  headers: {},
  config
})
const httpError = (config, status, message) =>
  new AxiosError(
    message,
    'ERR_BAD_REQUEST',
    config,
    {},
    { status, data: { code: status, message }, headers: {}, config }
  )

beforeEach(() => {
  setActivePinia(createPinia())
})

test('useAdminStore 对外导出的 state/computed/action 键集合保持稳定', () => {
  const store = useAdminStore()
  const expectedKeys = [
    'overview',
    'overviewLoading',
    'fetchOverview',
    'users',
    'loading',
    'filters',
    'pagination',
    'pages',
    'fetchUsers',
    'updateUserStatus',
    'searchUsers',
    'resetFilters',
    'changePage',
    'posts',
    'postsLoading',
    'postsFilters',
    'postPagination',
    'postPages',
    'fetchPosts',
    'fetchPostDetail',
    'updatePostStatus',
    'removePost',
    'searchPosts',
    'resetPostFilters',
    'changePostPage',
    'replies',
    'repliesLoading',
    'replyFilters',
    'replyPagination',
    'replyPages',
    'fetchReplies',
    'fetchReplyDetail',
    'updateReplyStatus',
    'removeReply',
    'searchReplies',
    'resetReplyFilters',
    'changeReplyPage',
    'pendingReportCount',
    'reports',
    'reportsTotal',
    'reportsLoading',
    'reportFilters',
    'reportPages',
    'fetchPendingReportCount',
    'fetchReports',
    'setReportTab',
    'changeReportPage',
    'fetchReportDetail',
    'handleReport',
    'topics',
    'topicsLoading',
    'topicFilters',
    'fetchTopics',
    'addTopic',
    'updateTopic',
    'updateTopicStatus',
    'searchTopics',
    'resetTopicFilters',
    'aiReplies',
    'aiRepliesTotal',
    'aiRepliesLoading',
    'aiReplyFilters',
    'aiReplyPages',
    'fetchAiReplies',
    'searchAiReplies',
    'resetAiReplyFilters',
    'changeAiReplyPage'
  ]
  for (const key of expectedKeys) {
    assert.ok(key in store, `missing store key: ${key}`)
  }
})

test('fetchOverview 携带限流后端状态与今日拒绝计数', async () => {
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    return ok(config, {
      userTotal: 1,
      rateLimitBackend: 'redis',
      rateLimitRejections: { mood_hour: '2', review_day: '1' }
    })
  }
  try {
    const store = useAdminStore()
    await store.fetchOverview()
    assert.equal(store.overview.rateLimitBackend, 'redis')
    assert.deepEqual(store.overview.rateLimitRejections, { mood_hour: '2', review_day: '1' })
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('fetchOverview 把后端字符串计数转成数字，"0" 不被当成有值', async () => {
  const prevAdapter = request.defaults.adapter
  // 后端 JacksonConfig 把 Long 序列化为字符串，概览计数实际以 "0"、"3" 形式返回。
  request.defaults.adapter = async (config) =>
    ok(config, { aiReplyTotal: '0', aiReplyFailed: '0', postTotal: '3' })
  try {
    const store = useAdminStore()
    await store.fetchOverview()
    assert.equal(store.overview.aiReplyTotal, 0)
    assert.equal(store.overview.aiReplyFailed, 0)
    assert.equal(store.overview.postTotal, 3)
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('fetchOverview 加载概览并在缺省字段时回落为 0', async () => {
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    assert.equal(config.url, '/admin/overview')
    return ok(config, { userTotal: 12, postHidden: 3 })
  }
  try {
    const store = useAdminStore()
    const res = await store.fetchOverview()
    assert.equal(res.ok, true)
    assert.equal(store.overview.userTotal, 12)
    assert.equal(store.overview.postHidden, 3)
    assert.equal(store.overview.aiReplyFailed, 0)
    assert.equal(store.overviewLoading, false)
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('用户管理：筛选、分页、重置与状态更新后刷新列表', async () => {
  const calls = []
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, params: config.params, data: config.data })
    if (config.url === '/admin/user/page') {
      return ok(config, {
        records: [{ id: '1', username: 'alice' }],
        total: 25,
        current: config.params.pageNum,
        size: 10
      })
    }
    return ok(config, null)
  }
  try {
    const store = useAdminStore()
    store.filters.username = '  alice  '
    store.filters.status = '0'
    store.pagination.pageNum = 3
    await store.searchUsers()
    assert.equal(calls[0].params.pageNum, 1)
    assert.equal(calls[0].params.username, 'alice')
    assert.equal(calls[0].params.status, 0)
    assert.equal(store.pages, 3)

    await store.changePage(2)
    assert.equal(calls[1].params.pageNum, 2)

    await store.updateUserStatus('1', 0)
    assert.equal(calls[2].url, '/admin/user/1/status')
    assert.equal(calls[3].url, '/admin/user/page')

    await store.resetFilters()
    assert.equal(store.filters.username, '')
    assert.equal(store.filters.status, '')
    assert.equal(calls[4].params.username, undefined)
    assert.equal(calls[4].params.status, undefined)
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('帖子管理：筛选查询、分页、重置、状态更新与 409 冲突也刷新列表', async () => {
  const calls = []
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, params: config.params })
    if (config.url === '/admin/post/page') {
      return ok(config, {
        records: [{ id: '10' }],
        total: 21,
        current: config.params.pageNum,
        size: 10
      })
    }
    if (config.url === '/admin/post/10/status') {
      return Promise.reject(httpError(config, 409, '状态已变更'))
    }
    if (config.url === '/admin/post/10') {
      return ok(config, { id: '10', content: 'detail' })
    }
    return ok(config, null)
  }
  try {
    const store = useAdminStore()
    store.postsFilters.status = '1'
    store.postsFilters.topicId = '5'
    store.postsFilters.postId = ' 10 '
    store.postsFilters.userId = ' 2 '
    store.postsFilters.keyword = ' 测试 '
    await store.searchPosts()
    assert.deepEqual(calls[0].params, {
      pageNum: 1,
      pageSize: 10,
      status: 1,
      topicId: 5,
      postId: '10',
      userId: '2',
      keyword: '测试'
    })
    assert.equal(store.postPages, 3)

    const detail = await store.fetchPostDetail('10')
    assert.equal(detail.data.id, '10')

    const statusRes = await store.updatePostStatus('10', 2, '敏感词')
    assert.equal(statusRes.status, 409)
    assert.equal(calls[3].url, '/admin/post/page')

    await store.changePostPage(2)
    assert.equal(calls[4].params.pageNum, 2)

    await store.resetPostFilters()
    assert.deepEqual(calls[5].params, { pageNum: 1, pageSize: 10 })
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('回复管理：筛选查询、分页、重置、删除与 409 冲突也刷新列表', async () => {
  const calls = []
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, params: config.params })
    if (config.url === '/admin/reply/page') {
      return ok(config, {
        records: [{ id: '20' }],
        total: 15,
        current: config.params.pageNum,
        size: 10
      })
    }
    if (config.url === '/admin/reply/20') {
      return Promise.reject(httpError(config, 409, '已被处理'))
    }
    return ok(config, null)
  }
  try {
    const store = useAdminStore()
    store.replyFilters.status = '2'
    store.replyFilters.postId = ' 10 '
    store.replyFilters.userId = ' 3 '
    store.replyFilters.keyword = ' 回复 '
    await store.searchReplies()
    assert.deepEqual(calls[0].params, {
      pageNum: 1,
      pageSize: 10,
      status: 2,
      postId: '10',
      userId: '3',
      keyword: '回复'
    })
    assert.equal(store.replyPages, 2)

    const rmRes = await store.removeReply('20', '违规')
    assert.equal(rmRes.status, 409)
    assert.equal(calls[2].url, '/admin/reply/page')

    await store.changeReplyPage(2)
    assert.equal(calls[3].params.pageNum, 2)

    await store.resetReplyFilters()
    assert.deepEqual(calls[4].params, { pageNum: 1, pageSize: 10 })
  } finally {
    request.defaults.adapter = prevAdapter
  }
})

test('举报与话题与 AI 记录管理：筛选、切换 Tab、翻页与更新刷新', async () => {
  const calls = []
  const prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    calls.push({ method: config.method, url: config.url, params: config.params, data: config.data })
    if (config.url === '/admin/report/page') {
      return ok(config, { records: [{ targetId: '11' }], total: 22 })
    }
    if (config.url === '/admin/report/pending-count') {
      return ok(config, 7)
    }
    if (config.url === '/admin/topic/list') {
      return ok(config, [{ id: 1, name: '树洞' }])
    }
    if (config.url === '/admin/ai-replies/page') {
      return ok(config, {
        records: [{ id: '99' }],
        total: 11,
        current: config.params.pageNum,
        size: 10
      })
    }
    return ok(config, null)
  }
  try {
    const store = useAdminStore()
    await store.fetchPendingReportCount()
    assert.equal(store.pendingReportCount, 7)

    await store.setReportTab('DONE')
    assert.equal(store.reportFilters.status, 'DONE')
    assert.equal(store.reportFilters.pageNum, 1)
    assert.equal(store.reportPages, 3)

    await store.changeReportPage(2)
    assert.equal(store.reportFilters.pageNum, 2)

    store.topicFilters.keyword = ' 树洞 '
    store.topicFilters.status = '1'
    await store.searchTopics()
    assert.deepEqual(calls.at(-1).params, { keyword: '树洞', status: 1 })
    await store.addTopic({ name: '新话题' })
    await store.resetTopicFilters()
    assert.deepEqual(calls.at(-1).params, {})

    store.aiReplyFilters.userId = ' 8 '
    store.aiReplyFilters.targetType = 'POST'
    store.aiReplyFilters.status = '2'
    store.aiReplyFilters.keyword = ' 焦虑 '
    await store.searchAiReplies()
    assert.deepEqual(calls.at(-1).params, {
      pageNum: 1,
      pageSize: 10,
      userId: '8',
      targetType: 'POST',
      status: 2,
      keyword: '焦虑'
    })
    assert.equal(store.aiReplyPages, 2)

    await store.changeAiReplyPage(2)
    assert.equal(store.aiReplyFilters.pageNum, 2)
    await store.resetAiReplyFilters()
    assert.deepEqual(calls.at(-1).params, { pageNum: 1, pageSize: 10 })
  } finally {
    request.defaults.adapter = prevAdapter
  }
})
