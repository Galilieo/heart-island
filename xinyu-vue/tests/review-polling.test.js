import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRenderer, defineComponent, nextTick, reactive, ref } from 'vue'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

let useReviewPolling
try {
  useReviewPolling = (await server.ssrLoadModule('/src/composables/useReviewPolling.js'))
    .useReviewPolling
} finally {
  await server.close()
}

function createHarness() {
  const requests = []
  const completions = []
  const networkErrors = []
  const timedOutUpdates = []
  let resetCount = 0

  const mode = ref('week')
  const weekStart = ref('2026-11-09')
  const activeStats = ref(null)
  const pollingTimedOut = ref(false)
  const userStore = reactive({ token: 'tok-1', isLoggedIn: true })
  const reviewStore = {
    fetchAiJob(jobId, options) {
      return new Promise((resolve, reject) => {
        requests.push({ jobId, options, resolve, reject })
      })
    },
    setPollingTimedOut(value) {
      pollingTimedOut.value = value
      timedOutUpdates.push(value)
    },
    reset() {
      resetCount += 1
      activeStats.value = null
      pollingTimedOut.value = false
    }
  }

  const renderer = createRenderer({
    createComment: () => ({}),
    insert() {},
    remove() {},
    parentNode() {},
    nextSibling() {}
  })

  let pollingApi
  const app = renderer.createApp(
    defineComponent({
      setup() {
        pollingApi = useReviewPolling({
          mode,
          weekStart,
          activeStats,
          pollingTimedOut,
          reviewStore,
          userStore,
          onComplete: (doneMode, doneWeekStart) => completions.push({ doneMode, doneWeekStart }),
          onNetworkError: () => networkErrors.push(true)
        })
        return () => null
      }
    })
  )

  return {
    app,
    mode,
    weekStart,
    activeStats,
    pollingTimedOut,
    userStore,
    requests,
    completions,
    networkErrors,
    timedOutUpdates,
    getResetCount: () => resetCount,
    getApi: () => pollingApi
  }
}

test('useReviewPolling 启动轮询，并在页面隐藏、恢复、登出与卸载时正确取消与恢复', async () => {
  const previousDocument = globalThis.document
  const fakeDocument = new EventTarget()
  fakeDocument.hidden = false
  globalThis.document = fakeDocument

  const ctx = createHarness()
  try {
    ctx.app.mount({})
    await nextTick()
    assert.equal(ctx.requests.length, 0)

    ctx.activeStats.value = { active: { id: 'r1', revision: '2', jobId: 'job-1' } }
    await nextTick()
    assert.equal(ctx.requests.length, 1)
    assert.equal(ctx.requests[0].jobId, 'job-1')
    assert.equal(ctx.requests[0].options.signal.aborted, false)

    fakeDocument.hidden = true
    fakeDocument.dispatchEvent(new Event('visibilitychange'))
    await nextTick()
    assert.equal(ctx.requests[0].options.signal.aborted, true)

    // 迟到响应在停止后不应触发完成回调
    ctx.requests[0].resolve({
      id: 'job-1',
      targetId: 'r1',
      targetVersion: '2',
      status: 'succeeded'
    })
    await new Promise(setImmediate)
    assert.deepEqual(ctx.completions, [])

    fakeDocument.hidden = false
    fakeDocument.dispatchEvent(new Event('visibilitychange'))
    await nextTick()
    assert.equal(ctx.requests.length, 2)
    assert.equal(ctx.requests[1].options.signal.aborted, false)

    ctx.userStore.token = ''
    ctx.userStore.isLoggedIn = false
    await nextTick()
    assert.equal(ctx.requests[1].options.signal.aborted, true)
    assert.equal(ctx.getResetCount(), 1)
  } finally {
    ctx.app.unmount()
    globalThis.document = previousDocument
  }
})

test('useReviewPolling 完成任务时回传启动轮询时的 mode 与 weekStart，卸载时会中止活跃请求', async () => {
  const previousDocument = globalThis.document
  const fakeDocument = new EventTarget()
  fakeDocument.hidden = false
  globalThis.document = fakeDocument

  const ctx = createHarness()
  try {
    ctx.app.mount({})
    ctx.activeStats.value = { active: { id: 'r1', revision: '1', jobId: 'job-done' } }
    await nextTick()
    assert.equal(ctx.requests.length, 1)

    ctx.requests[0].resolve({
      id: 'job-done',
      targetId: 'r1',
      targetVersion: '1',
      status: 'succeeded'
    })
    await new Promise(setImmediate)
    assert.deepEqual(ctx.completions, [{ doneMode: 'week', doneWeekStart: '2026-11-09' }])

    ctx.mode.value = 'recent30'
    ctx.activeStats.value = { active: { id: 'r2', revision: '1', jobId: 'job-2' } }
    await nextTick()
    assert.equal(ctx.requests.length, 2)

    ctx.app.unmount()
    assert.equal(ctx.requests[1].options.signal.aborted, true)
  } finally {
    globalThis.document = previousDocument
  }
})
