import assert from 'node:assert/strict'
import { test } from 'node:test'
import { useAsyncAction } from '../src/composables/useAsyncAction.js'

test('并发读请求不被写操作互斥挡掉，loading等待全部请求退出', async () => {
  const pending = []
  const action = useAsyncAction(() => new Promise(resolve => pending.push(resolve)),
    { silent: true, concurrent: true })
  const first = action.run()
  const second = action.run()
  assert.equal(pending.length, 2)
  pending[0]({ ok: true })
  await first
  assert.equal(action.loading.value, true)
  pending[1]({ ok: true })
  await second
  assert.equal(action.loading.value, false)
})
