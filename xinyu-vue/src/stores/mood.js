// 心情保存、查询与任务状态分别维护；保存已确认后，刷新失败不改变保存结果。
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { moodApi } from '../api'
import { refreshAfterSave } from '../utils/saved-result.js'
import { groupLatestMoodByDay } from '../utils/mood'

// 月历一次取完当月记录后在前端聚合。后端 /mood/list 没有每页上限
// （MoodRecordController 只校验 pageSize ≥ 1），这里取 200 条；
// 单月记录超过 200 条时月历会不完整，后续改按月统计接口补齐。
const MONTH_PAGE_SIZE = 200

function currentMonth() {
  const now = new Date()
  return { year: now.getFullYear(), month: now.getMonth() + 1, days: {} }
}

// 本地时区的当月首末日期，作为 /mood/list 的日期范围参数；
// 后端按 [startDate 00:00:00, endDate 23:59:59] 过滤。
function monthDateRange(year, month) {
  const pad = (value) => String(value).padStart(2, '0')
  const monthKey = `${year}-${pad(month)}`
  const lastDay = new Date(year, month, 0).getDate()
  return {
    startDate: `${monthKey}-01`,
    endDate: `${monthKey}-${pad(lastDay)}`
  }
}

export const useMoodStore = defineStore('mood', () => {
  const list = ref([])
  const total = ref(0)
  const pages = ref(0)
  const selected = ref(null)
  const pendingIds = reactive(new Set())
  const observationEpoch = ref(0)
  const filters = reactive({ moodType: '', startDate: '', endDate: '' })
  const pagination = reactive({ pageNum: 1, pageSize: 5 })
  const trend = ref({ total: 0, mainMoodType: '', lastMoodType: '', lastRecordTime: '', moodCounts: [] })
  const month = ref(currentMonth())
  const retries = new Map()

  // 查询代次阻止旧筛选／详情回包覆盖新页面；修订号还隔离查询期间发生的保存和任务更新。
  let session = 0
  let revision = 0
  let observationRevision = 0
  let listRequest = 0
  let detailRequest = 0
  let trendRequest = 0
  let monthRequest = 0
  const same = (a, b) => String(a) === String(b)

  function visibleRecords() {
    return [...list.value, selected.value].filter(Boolean)
  }

  function findRecord(id) {
    return visibleRecords().find(record => same(record.id, id))
  }

  function sameSource(a, b) {
    return same(a.id, b.id) && same(a.contentVersion, b.contentVersion) && same(a.jobId, b.jobId)
  }

  function preserveObservation(record, observedAtStart = observationRevision) {
    const previous = findRecord(record.id)
    if (!previous || !sameSource(previous, record)) {
      return record
    }
    if (previous.jobRevision > observedAtStart) {
      // 列表查询开始后收到的任务结果更新，不能被该查询的旧快照覆盖；新列表行仍应接收。
      return { ...record, aiReplyState: previous.aiReplyState, aiReply: previous.aiReply,
        resultKind: previous.resultKind, aiJob: previous.aiJob, retryAvailableAt: previous.retryAvailableAt,
        observationIssue: previous.observationIssue, jobRevision: previous.jobRevision }
    }
    if (previous.aiReplyState === record.aiReplyState) {
      return { ...record, aiJob: previous.aiJob, retryAvailableAt: previous.retryAvailableAt,
        observationIssue: previous.observationIssue }
    }
    return record
  }

  async function fetchList() {
    const requestId = ++listRequest
    const startedSession = session
    const startedRevision = revision
    const observedAtStart = observationRevision
    const res = await moodApi.list({ moodType: filters.moodType || undefined,
      startDate: filters.startDate || undefined, endDate: filters.endDate || undefined, ...pagination })
    const current = requestId === listRequest && startedSession === session && startedRevision === revision
    if (res.ok && res.data && current) {
      const records = res.data.records || res.data.list || []
      list.value = records.map(record => preserveObservation(record, observedAtStart))
      total.value = res.data.total ?? records.length
      pages.value = res.data.pages ?? 0
    }
    return res
  }

  async function fetchTrend() {
    const requestId = ++trendRequest
    const startedSession = session
    const res = await moodApi.trendRecent7()
    if (res.ok && res.data && requestId === trendRequest && startedSession === session) {
      trend.value = { ...trend.value, ...res.data }
    }
    return res
  }

  // 月历查询与列表查询共用 /mood/list，按月历请求代次与账号会话隔离：
  // 旧回包不能覆盖后发的查询结果，切换账号后上一账号的响应直接丢弃。
  async function fetchMonth(year, monthValue) {
    const requestId = ++monthRequest
    const startedSession = session
    const range = monthDateRange(year, monthValue)

    let res
    try {
      res = await moodApi.list({ ...range, pageNum: 1, pageSize: MONTH_PAGE_SIZE })
    } catch (error) {
      // 月历只做侧栏展示，失败静默返回，不能抛出未捕获异常影响保存流程。
      return { ok: false, message: error.userMessage || '本月记录暂未取到' }
    }

    const current = requestId === monthRequest && startedSession === session
    if (!res.ok || !res.data || !current) {
      return res
    }

    const records = res.data.records || res.data.list || []
    month.value = { year, month: monthValue, days: groupLatestMoodByDay(records) }
    return res
  }

  // 保存成功后单独、静默刷新月历：不放进 refreshAfterSave，
  // 月历失败不得影响保存结果，也不触发“列表刷新失败”提示。
  function refreshMonthSilently() {
    fetchMonth(month.value.year, month.value.month)
  }

  function beginMutation(id) {
    revision++
    if (id != null) {
      pendingIds.add(String(id))
    }
    return session
  }

  function applySaved(payload, saved) {
    for (const record of visibleRecords()) {
      if (!same(record.id, saved.id)) {
        continue
      }
      const unchanged = same(record.contentVersion, saved.contentVersion)
      Object.assign(record, payload, saved)
      if (!unchanged) {
        record.aiReply = null
        record.aiJob = null
        record.resultKind = null
        record.observationIssue = null
      }
    }
  }

  async function add(payload) {
    const startedSession = beginMutation()
    const res = await moodApi.create(payload)
    if (!res.ok || startedSession !== session) {
      return res
    }
    pagination.pageNum = 1
    const refreshFailed = await refreshAfterSave(fetchList, fetchTrend)
    refreshMonthSilently()
    return { ...res, refreshFailed }
  }

  async function update(id, payload) {
    const startedSession = beginMutation(id)
    try {
      const res = await moodApi.update(id, payload)
      if (!res.ok || startedSession !== session) {
        return res
      }
      applySaved(payload, res.data)
      const refreshFailed = await refreshAfterSave(fetchList, fetchTrend)
      refreshMonthSilently()
      return { ...res, refreshFailed }
    } finally {
      if (startedSession === session) {
        pendingIds.delete(String(id))
      }
    }
  }

  async function remove(id) {
    const startedSession = beginMutation(id)
    detailRequest++
    try {
      const res = await moodApi.remove(id)
      if (!res.ok || startedSession !== session) {
        return res
      }
      list.value = list.value.filter(record => !same(record.id, id))
      if (selected.value && same(selected.value.id, id)) {
        selected.value = null
      }
      const refreshFailed = await refreshAfterSave(fetchList, fetchTrend)
      refreshMonthSilently()
      return { ...res, refreshFailed }
    } finally {
      if (startedSession === session) {
        pendingIds.delete(String(id))
      }
    }
  }

  async function openDetail(id) {
    const requestId = ++detailRequest
    const startedSession = session
    const startedRevision = revision
    const observedAtStart = observationRevision
    const res = await moodApi.detail(id)
    if (requestId !== detailRequest || startedSession !== session || startedRevision !== revision) {
      return { ok: false, stale: true }
    }
    if (res.ok) {
      selected.value = preserveObservation(res.data, observedAtStart)
    }
    return res
  }

  function closeDetail() {
    detailRequest++
    selected.value = null
  }

  async function fetchJob(id, options) {
    const res = await moodApi.aiJob(id, options)
    if (!res.ok) {
      throw new Error(res.message)
    }
    return res.data
  }

  function applyJob(job, allowedPendingId = null) {
    const jobRevision = ++observationRevision
    for (const record of visibleRecords()) {
      const matches = same(record.id, job.targetId) && same(record.contentVersion, job.targetVersion) &&
        same(record.jobId, job.id)
      const blocked = pendingIds.has(String(record.id)) && !same(record.id, allowedPendingId)
      if (!matches || blocked) {
        continue
      }
      Object.assign(record, { aiReplyState: job.status, aiJob: job, jobRevision,
        aiReply: job.status === 'succeeded' ? job.replyContent : null,
        resultKind: job.resultKind, observationIssue: null,
        retryAvailableAt: Date.now() + job.retryAfterSeconds * 1000 })
    }
  }

  function markObservation(id, reason, error) {
    if (!['network', 'limit'].includes(reason)) {
      return
    }
    for (const record of visibleRecords()) {
      if (same(record.id, id)) {
        record.observationIssue = error?.response?.status === 404 ? 'removed' : reason
      }
    }
  }

  // 双击共享同一次POST；失败（含429）后重新GET，错误响应体不被当成任务VO。
  function retry(id) {
    const key = String(id)
    if (retries.has(key)) {
      return retries.get(key)
    }
    const record = findRecord(id)
    if (!record || record.aiReplyState !== 'failed' || pendingIds.has(key)) {
      return Promise.resolve({ ok: false, message: '当前状态不能重试，请刷新后查看' })
    }
    const source = { id: record.id, contentVersion: record.contentVersion, jobId: record.jobId }
    const startedSession = beginMutation(id)
    const pending = performRetry(source, startedSession).finally(() => {
      if (retries.get(key) === pending) {
        retries.delete(key)
      }
      if (startedSession === session) {
        pendingIds.delete(key)
      }
    })
    retries.set(key, pending)
    return pending
  }

  async function performRetry(source, startedSession) {
    let res
    try {
      res = await moodApi.retryAiJob(source.jobId)
    } catch (error) {
      res = { ok: false, message: error.userMessage || '重试结果暂未确认，请刷新查看' }
    }
    if (startedSession !== session) {
      return res
    }
    if (res.ok) {
      applyJob(res.data, source.id)
      observationEpoch.value++
      return res
    }
    try {
      const job = await fetchJob(source.jobId)
      if (startedSession === session && findRecord(source.id) && sameSource(findRecord(source.id), source)) {
        applyJob(job, source.id)
      }
    } catch (error) {
      if (startedSession === session && findRecord(source.id) && sameSource(findRecord(source.id), source)) {
        markObservation(source.id, 'network', error)
      }
    }
    return res
  }

  function refreshObservation() {
    observationEpoch.value++
  }

  function setFilter(patch) {
    Object.assign(filters, patch)
    pagination.pageNum = 1
    listRequest++
  }

  function setPage(pageNum) {
    pagination.pageNum = pageNum
    listRequest++
  }

  function resetFilters() {
    setFilter({ moodType: '', startDate: '', endDate: '' })
  }

  function reset() {
    session++
    revision++
    closeDetail()
    list.value = []
    total.value = 0
    pages.value = 0
    trend.value = { total: 0, mainMoodType: '', lastMoodType: '', lastRecordTime: '', moodCounts: [] }
    // 月历请求代次一并失效，避免切换账号后旧账号的回包写入新账号的月历。
    monthRequest++
    month.value = currentMonth()
    pendingIds.clear()
    retries.clear()
    resetFilters()
  }

  return { list, total, pages, selected, filters, pagination, trend, month, pendingIds, observationEpoch,
    fetchList, fetchTrend, fetchMonth, add, update, remove, openDetail, closeDetail, fetchJob, applyJob,
    markObservation, retry, refreshObservation, setFilter, setPage, resetFilters, reset }
})
