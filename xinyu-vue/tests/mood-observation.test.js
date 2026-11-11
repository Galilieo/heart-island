import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createRenderer, defineComponent, reactive, nextTick } from 'vue'
import { createServer } from 'vite'
import { fileURLToPath } from 'node:url'

const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false, optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false } })
let useMoodPolling
try {
  useMoodPolling = (await server.ssrLoadModule('/src/composables/useMoodPolling.js')).useMoodPolling
} finally {
  await server.close()
}

test('真实Vue生命周期最多观察6项，隐藏、登出和卸载都会取消GET', async () => {
  const previousDocument = globalThis.document
  const document = new EventTarget()
  document.hidden = false
  globalThis.document = document
  const requests = []
  const updates = []
  const store = reactive({
    list: Array.from({ length: 8 }, (_, index) => ({ id: String(index), contentVersion: '1',
      jobId: String(index + 100), aiReplyState: 'running' })),
    selected: null, pendingIds: new Set(), observationEpoch: 0,
    reset() {}, markObservation() {}, applyJob: job => updates.push(job),
    fetchJob(id, { signal }) {
      return new Promise(resolve => requests.push({ id, signal, resolve }))
    },
  })
  const user = reactive({ token: 'fixture-token', isLoggedIn: true })
  // 使用Vue实际生命周期；不需要DOM，HTTP与document事件是外部替身。
  const renderer = createRenderer({
    createComment: () => ({}), insert() {}, remove() {}, parentNode() {}, nextSibling() {},
  })
  const app = renderer.createApp(defineComponent({
    setup() { useMoodPolling(store, user); return () => null },
  }))
  try {
    app.mount({})
    await nextTick()
    assert.equal(requests.length, 6)
    document.hidden = true
    document.dispatchEvent(new Event('visibilitychange'))
    await nextTick()
    assert.equal(requests.every(request => request.signal.aborted), true)

    for (const request of requests) {
      request.resolve({ id: request.id, targetId: String(Number(request.id) - 100),
        targetVersion: '1', status: 'succeeded' })
    }
    await new Promise(setImmediate)
    assert.deepEqual(updates, [])

    document.hidden = false
    document.dispatchEvent(new Event('visibilitychange'))
    await nextTick()
    assert.equal(requests.length, 12)
    user.token = ''
    user.isLoggedIn = false
    await nextTick()
    assert.equal(requests.every(request => request.signal.aborted), true)
  } finally {
    app.unmount()
    globalThis.document = previousDocument
  }
})
