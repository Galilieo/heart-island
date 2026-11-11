import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'

const server = await createServer({ root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false, optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false } })
let communityApi
let useCommunityStore
let request
try {
  communityApi = (await server.ssrLoadModule('/src/api/community.js')).communityApi
  useCommunityStore = (await server.ssrLoadModule('/src/stores/community.js')).useCommunityStore
  request = (await server.ssrLoadModule('/src/utils/request.js')).default
} finally {
  await server.close()
}

beforeEach(() => {
  setActivePinia(createPinia())
  globalThis.localStorage = { getItem: () => 'fixture-token', removeItem() {} }
  globalThis.window = { location: { pathname: '/posts' } }
})

const success = (config, data) => ({ status: 200, data: { code: 200, data }, headers: {}, config })

test('社区发布和编辑单独等待20秒，普通查询仍为5秒', async () => {
  const sent = []
  request.defaults.adapter = async config => { sent.push(config); return success(config, true) }
  await communityApi.createPost({ content: '合成内容' })
  await communityApi.updatePost({ id: '1', content: '合成编辑' })
  await communityApi.postList()
  assert.deepEqual(sent.map(config => config.timeout), [20_000, 20_000, 5000])
})

test('发布或编辑超时只报告结果未确认，不重发、不假称保存失败', async () => {
  let calls = 0
  request.defaults.adapter = async config => {
    calls++
    throw new AxiosError('fixture timeout', 'ECONNABORTED', config)
  }
  for (const save of [communityApi.createPost, communityApi.updatePost]) {
    await assert.rejects(save({ content: '保留的合成输入' }), error => {
      assert.match(error.userMessage, /结果尚未确认/)
      assert.match(error.userMessage, /刷新核对/)
      return true
    })
  }
  assert.equal(calls, 2)
})

test('社区保存已成功但刷新抛错，保存结果仍成功', async () => {
  request.defaults.adapter = async config => {
    if (config.url === '/community/post/add') return success(config, true)
    throw new AxiosError('fixture offline', 'ERR_NETWORK', config)
  }
  const result = await useCommunityStore().publishPost({ content: '合成内容' })
  assert.equal(result.ok, true)
  assert.equal(result.refreshFailed, true)
})
