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
let useAdminStore
let useAdminReportHandle
let request
try {
  reportUtils = await server.ssrLoadModule('/src/utils/communityReport.js')
  useCommunityStore = (await server.ssrLoadModule('/src/stores/community.js')).useCommunityStore
  useAdminStore = (await server.ssrLoadModule('/src/stores/admin.js')).useAdminStore
  useAdminReportHandle = (await server.ssrLoadModule('/src/composables/useAdminReportHandle.js'))
    .useAdminReportHandle
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

test('myReportResultMeta 覆盖全部举报状态', () => {
  assert.deepEqual(reportUtils.myReportResultMeta('PENDING'), { text: '处理中', tone: 'warn' })
  assert.deepEqual(reportUtils.myReportResultMeta('HIDDEN'), { text: '已处理 · 内容已隐藏', tone: 'neutral' })
  assert.deepEqual(reportUtils.myReportResultMeta('DISMISSED'), { text: '已处理 · 未发现违规', tone: 'success' })
  assert.deepEqual(reportUtils.myReportResultMeta('CLOSED'), { text: '已处理 · 内容已被作者删除', tone: 'neutral' })
})

test('contentStatusMeta 覆盖全部内容状态', () => {
  assert.equal(reportUtils.contentStatusMeta(1).text, '正常')
  assert.equal(reportUtils.contentStatusMeta(2).text, '已隐藏')
  assert.equal(reportUtils.contentStatusMeta(0).text, '作者已删除')
})

test('formatExcerpt 对超长内容按 60 字截断并保留空白兜底', () => {
  assert.equal(reportUtils.formatExcerpt('   '), '')
  assert.equal(reportUtils.formatExcerpt('短内容'), '短内容')
  assert.equal(reportUtils.formatExcerpt('字'.repeat(65)), `${'字'.repeat(60)}…`)
})

test('submitReport 成功后标记已举报状态', async () => {
  request.defaults.adapter = async (config) => ok(config, true, '已收到举报，会尽快处理')
  const store = useCommunityStore()
  const successRes = await store.submitReport({ targetType: 'POST', targetId: '11', reason: 'AD' })
  assert.equal(successRes.ok, true)
  assert.equal(store.reportedTargets['POST:11'], 'PENDING')
})

test('submitReport 遇到 409 重复举报返回友好失败对象', async () => {
  request.defaults.adapter = async (config) => {
    throw httpError(config, 409, '你已经举报过这条内容了')
  }
  const store = useCommunityStore()
  const dupRes = await store.submitReport({ targetType: 'POST', targetId: '11', reason: 'AD' })
  assert.equal(dupRes.ok, false)
  assert.equal(dupRes.status, 409)
  assert.equal(dupRes.message, '你已经举报过这条内容了')
})

test('后台 handleReport 成功或遭遇 409 冲突都会刷新队列与待处理角标', async () => {
  const calls = []
  request.defaults.adapter = async (config) => {
    calls.push(config.url)
    if (config.url === '/admin/report/handle') {
      throw httpError(config, 409, '已被其他管理员处理')
    }
    if (config.url === '/admin/report/page') {
      return ok(config, { records: [], total: 1 })
    }
    if (config.url === '/admin/report/pending-count') {
      return ok(config, 1)
    }
    return ok(config, null)
  }

  const adminStore = useAdminStore()
  const conflictRes = await adminStore.handleReport({
    targetType: 'POST',
    targetId: 11,
    action: 'HIDE',
    hideReason: 'AD'
  })
  assert.equal(conflictRes.ok, false)
  assert.equal(conflictRes.conflict, true)
  assert.equal(conflictRes.message, '已被其他管理员处理')
  assert.equal(adminStore.pendingReportCount, 1)
  assert.ok(calls.includes('/admin/report/page'))
  assert.ok(calls.includes('/admin/report/pending-count'))
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

test('useAdminReportHandle 提交裁决时保留 targetId 字符串并区分加载动作', async () => {
  let sentPayload = null
  let resolveRequest
  const pendingPromise = new Promise((resolve) => {
    resolveRequest = resolve
  })

  const mockAdminStore = {
    async fetchReportDetail() {
      return {
        ok: true,
        data: {
          targetType: 'POST',
          targetId: '9007199254740993',
          suggestedHideReason: 'AD'
        }
      }
    },
    async handleReport(payload) {
      sentPayload = payload
      return pendingPromise
    }
  }
  const toasts = []
  const dialog = useAdminReportHandle({
    adminStore: mockAdminStore,
    toast: {
      success: (m) => toasts.push(m),
      warn: (m) => toasts.push(m),
      error: (m) => toasts.push(m)
    }
  })

  await dialog.openDetail({ targetType: 'POST', targetId: '9007199254740993' })
  const submitPromise = dialog.submitHandle('DISMISS')

  assert.equal(dialog.submitting.value, true)
  assert.equal(dialog.submittingAction?.value, 'DISMISS')

  resolveRequest({ ok: true, message: '已标记不违规，相关举报已关闭' })
  await submitPromise

  assert.equal(typeof sentPayload.targetId, 'string')
  assert.equal(sentPayload.targetId, '9007199254740993')
  assert.equal(dialog.submitting.value, false)
  assert.equal(dialog.submittingAction?.value, '')
})
