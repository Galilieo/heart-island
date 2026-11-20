import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createPinia, setActivePinia } from 'pinia'
import { createServer } from 'vite'

if (!globalThis.localStorage) {
  globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
}

// 运行真实 store 和 API 封装，通过 Axios adapter 提供合成分页结果。
const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

let useCommunityStore
let request
try {
  useCommunityStore = (await server.ssrLoadModule('/src/stores/community.js')).useCommunityStore
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

function ok(config, data) {
  return { status: 200, data: { code: 200, data }, headers: {}, config }
}

beforeEach(() => {
  setActivePinia(createPinia())
})

test('读取分页数据，改变话题、范围或排序后回到第一页', async () => {
  const queries = []
  request.defaults.adapter = async (config) => {
    queries.push(config.params)
    return ok(config, { records: [{ id: '11' }], total: '23' })
  }
  const store = useCommunityStore()

  await store.fetchPosts(2)
  assert.equal(store.postsPage, 2)
  assert.deepEqual(store.posts, [{ id: '11' }])
  assert.equal(store.postsTotal, 23)
  assert.equal(store.postsPages, 3)

  store.setTopicFilter('4')
  await store.fetchPosts()
  await store.fetchPosts(2)
  store.setMineFilter(true)
  await store.fetchPosts()
  await store.fetchPosts(2)
  store.setSort('hot')
  await store.fetchPosts()

  assert.deepEqual(
    queries.map(({ pageNum }) => pageNum),
    [2, 1, 2, 1, 2, 1]
  )
  assert.deepEqual(queries.at(-1), {
    topicId: '4',
    mine: true,
    sort: 'hot',
    pageNum: 1,
    pageSize: 10
  })
})

test('发帖回到第一页，删除末页唯一帖子后退回上一页', async () => {
  const pagesRequested = []
  let deleted = false
  request.defaults.adapter = async (config) => {
    if (config.url === '/community/post/delete') {
      deleted = true
      return ok(config, null)
    }
    if (config.method === 'post') {
      return ok(config, null)
    }
    const { pageNum } = config.params
    pagesRequested.push(pageNum)
    const records = deleted && pageNum === 2 ? [] : [{ id: pageNum === 2 ? '11' : '1' }]
    return ok(config, { records, total: deleted ? '10' : '11' })
  }
  const store = useCommunityStore()

  await store.fetchPosts(2)
  await store.publishPost({ needAiReply: false })
  assert.equal(store.postsPage, 1)

  await store.fetchPosts(2)
  await store.removePost('11')
  assert.equal(store.postsPage, 1)
  assert.equal(store.postsTotal, 10)
  assert.deepEqual(store.posts, [{ id: '1' }])
  assert.deepEqual(pagesRequested, [2, 1, 2, 2, 1])
})

test('切换筛选后旧页请求晚返回也不能覆盖新列表', async () => {
  let completeOldRequest
  request.defaults.adapter = async (config) => {
    if (config.params.topicId) {
      return ok(config, { records: [{ id: '21' }], total: '1' })
    }
    return new Promise((resolve) => {
      completeOldRequest = () => resolve(ok(config, { records: [{ id: '11' }], total: '20' }))
    })
  }
  const store = useCommunityStore()
  const oldPage = store.fetchPosts(2)

  store.setTopicFilter('4')
  await store.fetchPosts()
  completeOldRequest()
  await oldPage

  assert.equal(store.postsPage, 1)
  assert.equal(store.postsTotal, 1)
  assert.deepEqual(store.posts, [{ id: '21' }])
})

test('互动请求先发后回时同步新列表同一帖子，已更新的计数不累加', async () => {
  let completeLike
  let completeFavorite
  let listReads = 0
  request.defaults.adapter = async (config) => {
    if (config.url === '/community/post/like') {
      return new Promise((resolve) => {
        completeLike = () => resolve(ok(config, null))
      })
    }
    if (config.url === '/community/post/favorite') {
      return new Promise((resolve) => {
        completeFavorite = () => resolve(ok(config, null))
      })
    }
    const liked = listReads++ > 0
    return ok(config, {
      records: [{ id: '1', liked, likeCount: liked ? 3 : 2, favorited: false }],
      total: '1'
    })
  }
  const store = useCommunityStore()
  await store.fetchPosts()
  const oldPost = store.posts[0]

  const liking = store.toggleLike(oldPost)
  const saving = store.toggleFavorite(oldPost)
  await store.fetchPosts()
  completeLike()
  completeFavorite()
  await Promise.all([liking, saving])

  assert.deepEqual(store.posts[0], { id: '1', liked: true, likeCount: 3, favorited: true })
})

test('列表迟到的旧快照不覆盖已确认的点赞和收藏', async () => {
  let completeList
  let listReads = 0
  const page = { records: [{ id: '1', liked: false, likeCount: 2, favorited: false }], total: '1' }
  request.defaults.adapter = async (config) => {
    if (config.method === 'post') return ok(config, null)
    const snapshot = structuredClone(page)
    if (listReads++ === 0) return ok(config, snapshot)
    return new Promise((resolve) => {
      completeList = () => resolve(ok(config, snapshot))
    })
  }
  const store = useCommunityStore()
  await store.fetchPosts()

  const refresh = store.fetchPosts()
  await store.toggleLike(store.posts[0])
  await store.toggleFavorite(store.posts[0])
  completeList()
  await refresh

  assert.deepEqual(store.posts[0], { id: '1', liked: true, likeCount: 3, favorited: true })
})

test('评论发布或编辑已保存时，列表刷新失败仍保留支持文案', async () => {
  const supportText = '合成服务端支持文字'
  request.defaults.adapter = async (config) => {
    if (config.method === 'get') {
      throw new Error('刷新失败')
    }
    return ok(config, { supportText })
  }
  const store = useCommunityStore()
  const reply = { postId: '11', content: '合成正文' }

  const publish = await store.publishReply(reply)
  const update = await store.updateReply({ id: '22', content: reply.content }, reply.postId)
  for (const result of [publish, update]) {
    assert.equal(result.ok, true)
    assert.equal(result.refreshFailed, true)
    assert.equal(result.data.supportText, supportText)
  }
})

test('编辑确认后使用返回的新任务，详情刷新失败时保留已保存正文', async () => {
  const store = useCommunityStore()
  store.currentPost = { id: '7', mine: true, jobId: '81', aiReply: '旧回复', resultKind: 'MODEL' }
  request.defaults.adapter = async (config) => {
    if (config.url === '/community/post/update') {
      return ok(config, { postId: '7', jobId: '82', aiReplyState: 'queued' })
    }
    if (config.url === '/community/post/detail') throw new Error('断开刷新')
    return ok(config, { records: [], total: '0' })
  }

  const result = await store.updatePost({
    id: '7',
    content: '新正文',
    moodType: '平静',
    topicId: '1'
  })
  assert.equal(result.ok, true)
  assert.equal(result.refreshFailed, true)
  assert.equal(store.currentPost.content, '新正文')
  assert.equal(store.currentPost.jobId, '82')
  assert.equal(store.currentPost.aiReplyState, 'queued')
  assert.equal(store.currentPost.aiReply, null)
})

test('列表请求开始后观察到的成功状态可以抵抗迟到的 queued 快照', async () => {
  const store = useCommunityStore()
  const queued = { id: '7', mine: true, jobId: '82', aiReplyState: 'queued' }
  // 切换筛选后卡片暂时不在页面，仍需要把结果合并到在途列表。
  store.posts = []
  let reply
  request.defaults.adapter = (config) =>
    new Promise((resolve) => {
      reply = () => resolve(ok(config, { records: [{ ...queued }], total: '1' }))
    })

  const refreshing = store.fetchPosts()
  await new Promise(setImmediate)
  store.applyAiJob({
    id: '82',
    targetId: '7',
    status: 'succeeded',
    resultKind: 'MODEL',
    replyContent: '新回复'
  })
  reply()
  await refreshing

  assert.equal(store.posts[0].aiReplyState, 'succeeded')
  assert.equal(store.posts[0].aiReply, '新回复')
})
