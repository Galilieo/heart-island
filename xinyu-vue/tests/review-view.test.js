import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { defineComponent, createRenderer, nextTick, ref } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createServer } from 'vite'
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

let useReviewNavigation
let useReviewStore
let resolveVisibleSources
let shouldShowReviewLetter
let request
try {
  useReviewNavigation = (await server.ssrLoadModule('/src/composables/useReviewNavigation.js'))
    .useReviewNavigation
  useReviewStore = (await server.ssrLoadModule('/src/stores/review.js')).useReviewStore
  const reviewUtils = await server.ssrLoadModule('/src/utils/review.js')
  resolveVisibleSources = reviewUtils.resolveVisibleSources
  shouldShowReviewLetter = reviewUtils.shouldShowReviewLetter
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

const ok = (config, data) => ({ status: 200, data: { code: 200, data }, headers: {}, config })
const httpError = (config, status, message) =>
  new AxiosError(message, 'ERR_BAD_REQUEST', config, {}, { status, data: { code: status, message }, headers: {}, config })

let prevAdapter = null
afterEach(() => {
  if (prevAdapter !== null) {
    request.defaults.adapter = prevAdapter
    prevAdapter = null
  }
})

test('navigateToWeek 只触发一次请求，且加载失败时 URL 保持原周', async () => {
  setActivePinia(createPinia())
  const reviewStore = useReviewStore()
  const weekRequests = []
  let failStart = ''

  prevAdapter = request.defaults.adapter
  request.defaults.adapter = async (config) => {
    if (config.url === '/mood/report/week') {
      const start = config.params?.start || ''
      weekRequests.push(start)
      if (failStart && start === failStart) {
        return Promise.reject(httpError(config, 500, '加载失败'))
      }
      const rangeStart = start || '2026-11-09'
      return ok(config, {
        rangeStart,
        rangeEnd: '2026-11-16',
        total: 2,
        daysWithRecords: 2,
        aiEnabledCount: 2,
        moodCounts: [],
        dailyMoods: []
      })
    }
    return ok(config, {})
  }

  const Dummy = defineComponent({ template: '<div />', setup: () => () => null })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/review', component: Dummy }]
  })
  await router.push('/review')
  await router.isReady()

  const mode = ref('week')
  const weekStart = ref('')
  const loadError = ref('')
  const toastErrors = []
  let nav

  const renderer = createRenderer({
    createComment: () => ({}),
    insert() {},
    remove() {},
    parentNode() {},
    nextSibling() {}
  })
  const app = renderer.createApp(
    defineComponent({
      setup() {
        nav = useReviewNavigation({
          route: {
            get query() {
              return router.currentRoute.value.query
            }
          },
          router,
          reviewStore,
          mode,
          weekStart,
          loadError,
          stopPolling: () => {},
          toast: { error: (msg) => toastErrors.push(msg) }
        })
        return () => null
      }
    })
  )
  app.use(router)
  app.mount({})

  try {
    await nav.loadWeek('')
    assert.equal(weekStart.value, '2026-11-09')

    weekRequests.length = 0
    await nav.navigateToWeek('2026-11-02')
    await new Promise((r) => setTimeout(r, 10))
    await nextTick()

    assert.deepEqual(weekRequests, ['2026-11-02'])
    assert.equal(router.currentRoute.value.query.start, '2026-11-02')
    assert.equal(weekStart.value, '2026-11-02')

    failStart = '2026-10-26'
    weekRequests.length = 0
    await nav.navigateToWeek('2026-10-26')
    await new Promise((r) => setTimeout(r, 10))
    await nextTick()

    assert.deepEqual(weekRequests, ['2026-10-26'])
    assert.equal(router.currentRoute.value.query.start, '2026-11-02')
    assert.equal(weekStart.value, '2026-11-02')
    assert.equal(toastErrors.length, 1)
  } finally {
    app.unmount()
  }
})

test('resolveVisibleSources 与信纸显示状态一致，generating / stale_deleted / polling_timeout 不显示来源卡片', () => {
  const report = {
    body: '旧信正文 {{r1}}',
    sources: [{ ref: 'r1', recordId: '101', excerpt: '原文', time: '2026-11-03T10:00:00' }]
  }

  for (const state of ['ready', 'stale_edited', 'failed_with_report']) {
    assert.equal(shouldShowReviewLetter(state, report), true)
    assert.equal(resolveVisibleSources(state, report).length, 1)
  }

  for (const state of ['generating', 'polling_timeout', 'crisis', 'stale_deleted', 'not_generated']) {
    assert.equal(shouldShowReviewLetter(state, report), false)
    assert.deepEqual(resolveVisibleSources(state, report), [])
  }
})
