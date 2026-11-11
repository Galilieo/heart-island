import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'

// 加载真实Pinia/API层，只替换HTTP传输；不会启动业务后端或调用模型。
const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false, optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false } })
let useMoodStore
let request
try {
  useMoodStore = (await server.ssrLoadModule('/src/stores/mood.js')).useMoodStore
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

const record = (patch = {}) => ({ id: '11', contentVersion: '2', jobId: '8',
  aiReplyEnabled: true, aiReplyState: 'failed', content: '合成内容', moodType: '平静', ...patch })
const job = (patch = {}) => ({ id: '8', targetId: '11', targetVersion: '2', status: 'failed',
  retryable: true, retryAfterSeconds: 0, ...patch })
const success = (config, data) => ({ status: 200, data: { code: 200, data }, headers: {}, config })

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

const pad = (value) => String(value).padStart(2, '0')
const localDay = (year, month, day) => `${year}-${pad(month)}-${pad(day)}`

// 月历查询按本地时区生成当月首末日期；测试用同一规则构造期望值。
function localMonthRange(year, month) {
  const lastDay = new Date(year, month, 0).getDate()
  const monthKey = `${year}-${pad(month)}`
  return { startDate: `${monthKey}-01`, endDate: `${monthKey}-${pad(lastDay)}` }
}

function currentYearMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1 }
}

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage = { getItem: () => 'fixture-token', removeItem() {} }
  globalThis.window = { location: { pathname: '/home' } }
})

test('保存成功而列表刷新抛错，仍返回已保存的VO及刷新失败标记', async () => {
  request.defaults.adapter = async config => {
    if (config.url === '/mood/add') return success(config, record({ aiReplyState: 'queued' }))
    if (config.url === '/mood/trend/recent7') return success(config, {})
    throw new AxiosError('fixture offline', 'ERR_NETWORK', config)
  }
  const store = useMoodStore()
  const result = await store.add({ moodType: '平静', content: '合成内容' })
  assert.equal(result.ok, true)
  assert.equal(result.data.id, '11')
  assert.equal(result.refreshFailed, true)
})

test('同一任务双击重试只发一次POST，共享在途结果', async () => {
  const pending = deferred()
  let retries = 0
  request.defaults.adapter = async config => {
    if (config.url === '/mood/list') return success(config, { records: [record()] })
    if (config.url.endsWith('/retry')) {
      retries++
      await pending.promise
      return success(config, job({ status: 'queued', retryable: false }))
    }
    assert.fail(`非预期请求 ${config.url}`)
  }
  const store = useMoodStore()
  await store.fetchList()
  store.applyJob(job())
  const first = store.retry('11')
  const second = store.retry('11')
  pending.resolve()
  assert.equal((await first).ok, true)
  await second
  assert.equal(retries, 1)
  assert.equal(store.list[0].aiReplyState, 'queued')
})

test('429错误体无任务VO，重试失败后通过GET更新冷却状态', async () => {
  let queries = 0
  request.defaults.adapter = async config => {
    if (config.url === '/mood/list') return success(config, { records: [record()] })
    if (config.url.endsWith('/retry')) {
      throw new AxiosError('cooldown', 'ERR_BAD_RESPONSE', config, null,
        { status: 429, data: { code: 429, message: '请稍后重试' }, headers: { 'retry-after': '180' }, config })
    }
    queries++
    return success(config, job({ retryable: false, retryAfterSeconds: 180 }))
  }
  const store = useMoodStore()
  await store.fetchList()
  store.applyJob(job())
  assert.equal((await store.retry('11')).ok, false)
  assert.equal(queries, 1)
  assert.equal(store.list[0].aiJob.retryAfterSeconds, 180)
})

test('旧版本和已删除来源的迟到任务结果不能恢复回复', async () => {
  let deleted = false
  request.defaults.adapter = async config => {
    if (config.url === '/mood/list') return success(config,
      { records: deleted ? [] : [record({ contentVersion: '3' })] })
    if (config.url.includes('/delete/')) deleted = true
    return success(config, true)
  }
  const store = useMoodStore()
  await store.fetchList()
  store.applyJob(job({ status: 'succeeded', replyContent: '旧回复' }))
  assert.equal(store.list[0].aiReply, undefined)
  await store.remove('11')
  store.applyJob(job({ targetVersion: '3', status: 'succeeded', replyContent: '迟到回复' }))
  assert.equal(store.list.length, 0)
})

test('切换筛选后，先发后到的列表响应不能覆盖新列表', async () => {
  const old = deferred()
  let calls = 0
  request.defaults.adapter = async config => {
    calls++
    if (calls === 1) await old.promise
    return success(config, { records: [record({ content: config.params.moodType || '旧筛选' })] })
  }
  const store = useMoodStore()
  const first = store.fetchList()
  store.setFilter({ moodType: '开心' })
  await store.fetchList()
  old.resolve()
  await first
  assert.equal(store.list[0].content, '开心')
})

test('查询列表期间任务完成，保留较新回复，同时接收列表中的新记录', async () => {
  const pending = deferred()
  let calls = 0
  request.defaults.adapter = async config => {
    calls++
    if (calls === 1) return success(config, { records: [record({ aiReplyState: 'running' })] })
    await pending.promise
    return success(config, { records: [record({ aiReplyState: 'running' }),
      record({ id: '12', jobId: null, aiReplyState: 'disabled' })] })
  }
  const store = useMoodStore()
  await store.fetchList()
  const refresh = store.fetchList()
  store.applyJob(job({ status: 'succeeded', replyContent: '较新回复' }))
  pending.resolve()
  await refresh
  assert.equal(store.list.length, 2)
  assert.equal(store.list[0].aiReplyState, 'succeeded')
  assert.equal(store.list[0].aiReply, '较新回复')
})

test('旧retry的回查失败不能给后来编辑的新任务设置错误标记', async () => {
  const waiting = deferred()
  let queried
  const started = new Promise(resolve => { queried = resolve })
  request.defaults.adapter = async config => {
    if (config.url === '/mood/list') return success(config, { records: [record()] })
    if (config.url.endsWith('/retry')) throw new AxiosError('fixture', 'ERR_NETWORK', config)
    queried()
    await waiting.promise
    throw new AxiosError('obsolete lookup', 'ERR_NETWORK', config)
  }
  const store = useMoodStore()
  await store.fetchList()
  const retry = store.retry('11')
  await started
  Object.assign(store.list[0], { contentVersion: '3', jobId: '9', observationIssue: null })
  waiting.resolve()
  await retry
  assert.equal(store.list[0].observationIssue, null)
})

test('月历查询按当月首末日期取第一页 200 条并聚合每天最后一条心情', async () => {
  const { year, month } = currentYearMonth()
  const range = localMonthRange(year, month)
  const firstDay = localDay(year, month, 1)
  let params
  request.defaults.adapter = async config => {
    if (config.url !== '/mood/list') {
      assert.fail(`非预期请求 ${config.url}`)
    }
    params = config.params
    return success(config, { records: [
      { id: '21', moodType: '平静', createTime: `${firstDay}T10:00:00` },
      { id: '22', moodType: '开心', createTime: `${firstDay}T20:00:00` }
    ] })
  }
  const store = useMoodStore()
  const result = await store.fetchMonth(year, month)
  assert.equal(result.ok, true)
  assert.equal(params.startDate, range.startDate)
  assert.equal(params.endDate, range.endDate)
  assert.equal(params.pageNum, 1)
  assert.equal(params.pageSize, 200)
  assert.equal(store.month.days[firstDay], '开心')
})

test('月历旧回包不能覆盖后发的新查询', async () => {
  const old = deferred()
  const { year, month } = currentYearMonth()
  const firstDay = localDay(year, month, 1)
  let calls = 0
  request.defaults.adapter = async config => {
    const call = ++calls
    if (call === 1) {
      await old.promise
    }
    const moodType = call === 1 ? '平静' : '开心'
    return success(config, { records: [{ id: String(call), moodType, createTime: `${firstDay}T09:00:00` }] })
  }
  const store = useMoodStore()
  const first = store.fetchMonth(year, month)
  const second = store.fetchMonth(year, month)
  await second
  old.resolve()
  await first
  assert.equal(store.month.days[firstDay], '开心')
})
