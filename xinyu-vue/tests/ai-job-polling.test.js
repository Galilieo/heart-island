import assert from 'node:assert/strict'
import { test } from 'node:test'
import { createAiJobPoller } from '../src/utils/ai-job-polling.js'

const source = { jobId: '8', recordId: '11', contentVersion: '2' }
const job = (status = 'running', patch = {}) => ({
  id: '8', targetId: '11', targetVersion: '2', status, ...patch,
})
const flush = () => new Promise(setImmediate)

function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

// 只替换时钟和任务GET，确定性推进等待；不调用真实API或模型。
function clock() {
  let now = 0
  let sequence = 0
  const pending = new Map()
  return {
    now: () => now,
    setTimeout(fn, delay) {
      const id = ++sequence
      pending.set(id, { fn, at: now + delay })
      return id
    },
    clearTimeout: id => pending.delete(id),
    async advance(milliseconds) {
      const until = now + milliseconds
      while (true) {
        const next = [...pending].sort((a, b) => a[1].at - b[1].at)[0]
        if (!next || next[1].at > until) break
        now = next[1].at
        pending.delete(next[0])
        next[1].fn()
        await flush()
      }
      now = until
      await flush()
    },
  }
}

test('慢查询结束后才安排下一次GET，不能重叠', async () => {
  const scheduler = clock()
  const first = deferred()
  let requests = 0
  const poller = createAiJobPoller({ scheduler, onUpdate() {}, fetchJob() {
    requests++
    return requests === 1 ? first.promise : Promise.resolve(job('succeeded'))
  } })
  poller.start(source)
  await scheduler.advance(10_000)
  assert.equal(requests, 1)
  first.resolve(job())
  await flush()
  await scheduler.advance(1999)
  assert.equal(requests, 1)
  await scheduler.advance(1)
  assert.equal(requests, 2)
  await scheduler.advance(20_000)
  assert.equal(requests, 2)
})

test('停止会取消GET，迟到成功回包不再应用', async () => {
  const reply = deferred()
  let signal
  const updates = []
  const poller = createAiJobPoller({ scheduler: clock(), onUpdate: value => updates.push(value),
    fetchJob: (_id, options) => { signal = options.signal; return reply.promise } })
  poller.start(source)
  poller.stop()
  assert.equal(signal.aborted, true)
  reply.resolve(job('succeeded'))
  await flush()
  assert.deepEqual(updates, [])
})

test('同一轮询器切换版本，旧请求结束前不发送新请求，也不应用旧结果', async () => {
  const scheduler = clock()
  const old = deferred()
  const updates = []
  let calls = 0
  const poller = createAiJobPoller({ scheduler, onUpdate: value => updates.push(value), fetchJob() {
    calls++
    return calls === 1 ? old.promise : Promise.resolve(job('succeeded', { targetVersion: '3' }))
  } })
  poller.start(source)
  poller.start({ ...source, contentVersion: '3' })
  assert.equal(calls, 1)
  old.resolve(job('succeeded'))
  await flush()
  await scheduler.advance(0)
  assert.equal(calls, 2)
  assert.deepEqual(updates.map(value => value.targetVersion), ['3'])
})

test('响应的任务、来源或版本不匹配时不能应用', async () => {
  for (const patch of [{ id: '9' }, { targetId: '12' }, { targetVersion: '1' }]) {
    const updates = []
    const poller = createAiJobPoller({ scheduler: clock(), onUpdate: value => updates.push(value),
      fetchJob: async () => job('succeeded', patch) })
    poller.start(source)
    await flush()
    assert.deepEqual(updates, [])
  }
})

test('失败或其他终态只应用一次，不自动重试生成', async () => {
  for (const state of ['failed', 'cancelled', 'superseded', 'succeeded']) {
    const scheduler = clock()
    let calls = 0
    const poller = createAiJobPoller({ scheduler, onUpdate() {}, fetchJob: async () => {
      calls++
      return job(state)
    } })
    poller.start(source)
    await flush()
    await scheduler.advance(120_000)
    assert.equal(calls, 1)
  }
})

test('网络错误停止观察，只报告查询失败，不伪造任务failed', async () => {
  const scheduler = clock()
  const stopped = []
  const updates = []
  const poller = createAiJobPoller({ scheduler, onUpdate: value => updates.push(value),
    onStop: reason => stopped.push(reason), fetchJob: async () => { throw new Error('offline') } })
  poller.start(source)
  await flush()
  await scheduler.advance(120_000)
  assert.deepEqual(updates, [])
  assert.deepEqual(stopped, ['network'])
})

test('120秒停止观察并取消未返回GET，不改变后端任务状态', async () => {
  const scheduler = clock()
  const stopped = []
  const pending = deferred()
  let signal
  const poller = createAiJobPoller({ scheduler, onUpdate: () => assert.fail('不能伪造终态'),
    onStop: reason => stopped.push(reason),
    fetchJob: (_id, options) => { signal = options.signal; return pending.promise } })
  poller.start(source)
  await scheduler.advance(120_000)
  assert.equal(signal.aborted, true)
  assert.deepEqual(stopped, ['limit'])
  pending.resolve(job('succeeded'))
  await flush()
})
