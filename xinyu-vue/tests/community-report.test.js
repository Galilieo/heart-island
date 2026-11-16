import assert from 'node:assert/strict'
import { beforeEach, test } from 'node:test'
import { fileURLToPath } from 'node:url'
import { createServer } from 'vite'
import { createPinia, setActivePinia } from 'pinia'
import { AxiosError } from 'axios'

const server = await createServer({
  root: fileURLToPath(new URL('..', import.meta.url)),
  configFile: false,
  optimizeDeps: { noDiscovery: true, include: [] },
  server: { middlewareMode: true, hmr: false, ws: false }
})

let reportUtils
let useCommunityStore
let request
try {
  reportUtils = await server.ssrLoadModule('/src/utils/communityReport.js')
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

const ok = (config, data, message = '操作成功') => ({
  status: 200,
  data: { code: 200, message, data },
  headers: {},
  config
})

const httpError = (config, status, message) => {
  const err = new AxiosError(message, 'ERR_BAD_REQUEST', config, null, {
    status,
    data: { code: status, message, data: null },
    headers: {},
    config
  })
  err.userMessage = message
  return err
}

test('举报表单校验覆盖未选原因、OTHER 缺说明和 200 字上限', () => {
  assert.equal(reportUtils.validateReportForm({ reason: '', detail: '' }), '请选择举报原因')
  assert.equal(reportUtils.validateReportForm({ reason: 'OTHER', detail: '   ' }), '选「其他」时请写一句原因')
  assert.match(reportUtils.validateReportForm({ reason: 'AD', detail: '字'.repeat(201) }), /200/)
  assert.equal(reportUtils.validateReportForm({ reason: 'SAFETY', detail: '' }), '')
  assert.equal(reportUtils.validateReportForm({ reason: 'OTHER', detail: '包含引流广告' }), '')
})

test('我的举报与内容状态文案映射覆盖全部枚举', () => {
  assert.deepEqual(reportUtils.myReportResultMeta('PENDING'), { text: '处理中', tone: 'warn' })
  assert.deepEqual(reportUtils.myReportResultMeta('HIDDEN'), { text: '已处理 · 内容已隐藏', tone: 'neutral' })
  assert.deepEqual(reportUtils.myReportResultMeta('DISMISSED'), { text: '已处理 · 未发现违规', tone: 'success' })
  assert.deepEqual(reportUtils.myReportResultMeta('CLOSED'), { text: '已处理 · 内容已被作者删除', tone: 'neutral' })

  assert.equal(reportUtils.contentStatusMeta(1).text, '正常')
  assert.equal(reportUtils.contentStatusMeta(2).text, '已隐藏')
  assert.equal(reportUtils.contentStatusMeta(0).text, '作者已删除')
})

test('submitReport 成功后标记已举报状态，409/429 返回友好失败对象', async () => {
  request.defaults.adapter = async (config) => ok(config, true, '已收到举报，会尽快处理')
  const store = useCommunityStore()
  const successRes = await store.submitReport({ targetType: 'POST', targetId: '11', reason: 'AD' })
  assert.equal(successRes.ok, true)
  assert.equal(store.reportedTargets['POST:11'], 'PENDING')

  request.defaults.adapter = async (config) => {
    throw httpError(config, 409, '你已经举报过这条内容了')
  }
  const dupRes = await store.submitReport({ targetType: 'POST', targetId: '11', reason: 'AD' })
  assert.equal(dupRes.ok, false)
  assert.equal(dupRes.status, 409)
  assert.equal(dupRes.message, '你已经举报过这条内容了')
})

test('切换查看已被隐藏或删除的帖子详情时清空上一篇 currentPost 与 replies', async () => {
  const store = useCommunityStore()
  request.defaults.adapter = async (config) => {
    if (config.url === '/community/post/detail') {
      if (config.params?.id === '1') {
        return ok(config, { id: '1', content: '正常帖子', mine: false })
      }
      return {
        status: 200,
        data: { code: 500, message: '帖子不存在或者已隐藏', data: null },
        headers: {},
        config
      }
    }
    if (config.url === '/community/reply/list') {
      if (config.params?.postId === '1') {
        return ok(config, [{ id: '10', content: '上一篇的回复' }])
      }
      return ok(config, [])
    }
    return ok(config, null)
  }

  await store.fetchPostDetail('1')
  await store.fetchReplies('1')
  assert.equal(store.currentPost.id, '1')
  assert.equal(store.replies.length, 1)

  const hiddenRes = await store.fetchPostDetail('2')
  await store.fetchReplies('2')
  assert.equal(hiddenRes.ok, false)
  assert.equal(store.currentPost, null)
  assert.equal(store.replies.length, 0)
})
