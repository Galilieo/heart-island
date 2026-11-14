import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'

if (!globalThis.localStorage) {
  globalThis.localStorage = { getItem: () => null, setItem: () => {}, removeItem: () => {} }
}

// 加载真实 Pinia/API 层，仅替换 Axios adapter；不连接业务库或调用模型。
const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

let useReviewStore
let request
try {
  useReviewStore = (await server.ssrLoadModule('/src/stores/review.js')).useReviewStore
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

const ok = (config, data) => ({ status: 200, data: { code: 200, data }, headers: {}, config })

function deferred() {
  let resolve
  let reject
  const promise = new Promise((done, fail) => {
    resolve = done
    reject = fail
  })
  return { promise, resolve, reject }
}

const samplePage = (patch = {}) => ({
  rangeStart: '2026-11-02',
  rangeEnd: '2026-11-09',
  total: 3,
  daysWithRecords: 2,
  days: [],
  moodCounts: [],
  report: null,
  active: null,
  failed: null,
  aiEnabledCount: 3,
  ...patch
})

beforeEach(() => {
  setActivePinia(createPinia())
})

test('周报切换先发后到时，旧响应不能覆盖新周统计', async () => {
  const first = deferred()
  const second = deferred()

  request.defaults.adapter = async (config) => {
    if (config.params?.start === '2026-10-26') {
      return first.promise
    }
    return second.promise
  }

  const store = useReviewStore()
  const p1 = store.fetchWeek('2026-10-26')
  const p2 = store.fetchWeek('2026-11-02')

  second.resolve(ok({ url: '/mood/report/week' }, samplePage({ rangeStart: '2026-11-02', total: 5 })))
  await p2
  assert.equal(store.weekStats.rangeStart, '2026-11-02')

  first.resolve(ok({ url: '/mood/report/week' }, samplePage({ rangeStart: '2026-10-26', total: 1 })))
  await p1
  assert.equal(store.weekStats.rangeStart, '2026-11-02')
  assert.equal(store.weekStats.total, 5)
})

test('generateWeek 并发防重入并在成功排队后立刻进入 active 状态', async () => {
  const pending = deferred()
  let postCount = 0

  request.defaults.adapter = async (config) => {
    if (config.method === 'post') {
      postCount++
      return pending.promise
    }
    return ok(config, samplePage())
  }

  const store = useReviewStore()
  await store.fetchWeek('2026-11-02')

  const firstClick = store.generateWeek('2026-11-02')
  const secondClick = store.generateWeek('2026-11-02')
  assert.equal((await secondClick).skipped, true)

  pending.resolve(
    ok(
      { url: '/mood/report/week/generate' },
      { id: '10', revision: 1, status: 'queued', jobId: '88', rangeStart: '2026-11-02', rangeEnd: '2026-11-09' }
    )
  )
  const res = await firstClick
  assert.equal(res.ok, true)
  assert.equal(postCount, 1)
  assert.equal(store.weekStats.active.status, 'queued')
  assert.equal(store.weekStats.active.jobId, '88')
})

test('retryJob 遇到 429 冷却或 source_changed 时转换中文提示并回查刷新', async () => {
  const requests = []
  request.defaults.adapter = async (config) => {
    requests.push(`${config.method?.toUpperCase()} ${config.url}`)
    if (config.url === '/mood/ai-job/99/retry') {
      throw new AxiosError(
        'Too Many Requests',
        'ERR_BAD_REQUEST',
        config,
        {},
        {
          status: 429,
          headers: { 'retry-after': '45' },
          data: { code: 429, message: 'review_cooldown' },
          config
        }
      )
    }
    if (config.url === '/mood/report/week') {
      return ok(config, samplePage({ failed: { id: '10', status: 'failed', jobId: '99' } }))
    }
    return ok(config, samplePage())
  }

  const store = useReviewStore()
  const result = await store.retryJob('99', 'week', '2026-11-02')

  assert.equal(result.ok, false)
  assert.match(result.userMessage, /45 秒/)
  assert.deepEqual(requests, ['POST /mood/ai-job/99/retry', 'GET /mood/report/week'])
})

test('fetchHistory 支持首页替换、翻页追加和 reset 清空', async () => {
  const makeItems = (prefix, count) =>
    Array.from({ length: count }, (_, idx) => ({
      id: `${prefix}-${idx}`,
      rangeStart: '2026-11-02',
      rangeEnd: '2026-11-09',
      revision: 1,
      status: 'succeeded',
      total: 4
    }))

  request.defaults.adapter = async (config) => {
    const page = config.params?.page
    return ok(config, page === 1 ? makeItems('p1', 5) : makeItems('p2', 2))
  }

  const store = useReviewStore()
  await store.fetchHistory(1)
  assert.equal(store.historyItems.length, 5)
  assert.equal(store.historyHasMore, true)

  await store.fetchHistory(2)
  assert.equal(store.historyItems.length, 7)
  assert.equal(store.historyHasMore, false)

  store.reset()
  assert.deepEqual(store.historyItems, [])
  assert.equal(store.weekStats, null)
})

