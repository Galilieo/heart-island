// 回顾页状态 store：管理周报/近 30 天统计、生成/重试、周报历史及错误映射。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { moodApi } from '../api'

const HISTORY_PAGE_SIZE = 5

const ERROR_MESSAGES = {
  no_records: '这段时间还没有心情记录',
  no_ai_enabled_records: '这些记录都没有开启 AI 回应',
  week_not_finished: '本周还在进行中，周日之后可以生成',
  source_changed: '记录刚有变动，已为你刷新最新内容',
  capacity_exceeded: '小芽正忙，请稍后再试',
  job_not_retryable: '当前状态已更新，请刷新看看',
  job_changed: '当前状态已更新，请刷新看看'
}

/** 把后端业务错误码或 Axios 异常转换为面向用户的中文提示。 */
export function formatReviewError(error) {
  const code = error?.response?.data?.message || error?.message
  if (code === 'review_cooldown' || error?.response?.status === 429) {
    const retryAfter = Number(error?.response?.headers?.['retry-after'])
    if (Number.isFinite(retryAfter) && retryAfter > 0) {
      return `休息一下，${retryAfter} 秒后可以再试`
    }
    return '刚刚试过啦，请稍后再试'
  }

  if (code && ERROR_MESSAGES[code]) {
    return ERROR_MESSAGES[code]
  }

  return error?.userMessage || '生成失败，请稍后重试'
}

function isTerminalReport(report) {
  return Boolean(report && report.status !== 'queued' && report.status !== 'running')
}

export const useReviewStore = defineStore('review', () => {
  const weekStats = ref(null)
  const recent30Stats = ref(null)
  const weekLoading = ref(false)
  const recent30Loading = ref(false)
  const submitting = ref(false)
  const pollingTimedOut = ref(false)
  const actionError = ref('')

  const historyItems = ref([])
  const historyPage = ref(1)
  const historyHasMore = ref(false)
  const historyLoading = ref(false)

  let weekRequestSeq = 0
  let recent30RequestSeq = 0
  let historyRequestSeq = 0

  async function fetchWeek(start = '') {
    const seq = ++weekRequestSeq
    weekLoading.value = true
    try {
      const res = await moodApi.reviewWeek(start)
      if (seq === weekRequestSeq && res.ok) {
        weekStats.value = res.data
      }
      return res
    } finally {
      if (seq === weekRequestSeq) {
        weekLoading.value = false
      }
    }
  }

  async function fetchRecent30() {
    const seq = ++recent30RequestSeq
    recent30Loading.value = true
    try {
      const res = await moodApi.reviewRecent30()
      if (seq === recent30RequestSeq && res.ok) {
        recent30Stats.value = res.data
      }
      return res
    } finally {
      if (seq === recent30RequestSeq) {
        recent30Loading.value = false
      }
    }
  }

  async function fetchHistory(page = 1) {
    const seq = ++historyRequestSeq
    historyLoading.value = true
    try {
      const res = await moodApi.reviewWeekHistory(page)
      if (seq === historyRequestSeq && res.ok) {
        const list = Array.isArray(res.data) ? res.data : []
        historyItems.value = page === 1 ? list : [...historyItems.value, ...list]
        historyPage.value = page
        historyHasMore.value = list.length >= HISTORY_PAGE_SIZE
      }
      return res
    } finally {
      if (seq === historyRequestSeq) {
        historyLoading.value = false
      }
    }
  }

  function applyReportToPage(targetRef, report) {
    if (!targetRef.value || !report) {
      return
    }
    if (isTerminalReport(report)) {
      targetRef.value = {
        ...targetRef.value,
        report: report.status === 'succeeded' ? report : targetRef.value.report,
        active: null,
        failed: report.status === 'failed' ? report : null
      }
      return
    }
    targetRef.value = {
      ...targetRef.value,
      active: report,
      failed: null
    }
  }

  async function refreshMode(mode, weekStart = '') {
    if (mode === 'recent30') {
      await fetchRecent30()
      return
    }
    await fetchWeek(weekStart)
  }


  async function runGenerateAction(mode, weekStart, requestFn) {
    if (submitting.value) {
      return { ok: false, skipped: true }
    }
    submitting.value = true
    actionError.value = ''
    pollingTimedOut.value = false
    try {
      const res = await requestFn()
      if (res.ok && res.data) {
        applyReportToPage(mode === 'recent30' ? recent30Stats : weekStats, res.data)
      }
      return res
    } catch (error) {
      const message = formatReviewError(error)
      actionError.value = message
      await refreshMode(mode, weekStart).catch(() => {})
      return { ok: false, error, userMessage: message }
    } finally {
      submitting.value = false
    }
  }

  function generateWeek(start, { regenerate = false } = {}) {
    return runGenerateAction('week', start, () =>
      regenerate ? moodApi.regenerateWeekReview(start) : moodApi.generateWeekReview(start)
    )
  }

  function generateRecent30() {
    return runGenerateAction('recent30', '', () => moodApi.generateRecent30Review())
  }

  async function retryJob(jobId, mode = 'week', weekStart = '') {
    if (submitting.value || !jobId) {
      return { ok: false, skipped: true }
    }
    submitting.value = true
    actionError.value = ''
    pollingTimedOut.value = false
    try {
      const res = await moodApi.retryAiJob(jobId)
      if (res.ok) {
        const targetRef = mode === 'recent30' ? recent30Stats : weekStats
        const currentFailed = targetRef.value?.failed
        if (targetRef.value && currentFailed && res.data) {
          targetRef.value = {
            ...targetRef.value,
            active: {
              ...currentFailed,
              status: res.data.status || 'queued',
              jobId: String(res.data.id || jobId)
            },
            failed: null
          }
        }
        await refreshMode(mode, weekStart)
      }
      return res
    } catch (error) {
      const message = formatReviewError(error)
      actionError.value = message
      await refreshMode(mode, weekStart).catch(() => {})
      return { ok: false, error, userMessage: message }
    } finally {
      submitting.value = false
    }
  }

  function fetchAiJob(jobId, options = {}) {
    return moodApi.aiJob(jobId, options)
  }

  function setPollingTimedOut(value) {
    pollingTimedOut.value = Boolean(value)
  }

  function reset() {
    weekRequestSeq++
    recent30RequestSeq++
    historyRequestSeq++
    weekStats.value = null
    recent30Stats.value = null
    weekLoading.value = false
    recent30Loading.value = false
    submitting.value = false
    pollingTimedOut.value = false
    actionError.value = ''
    historyItems.value = []
    historyPage.value = 1
    historyHasMore.value = false
    historyLoading.value = false
  }

  return {
    weekStats,
    recent30Stats,
    weekLoading,
    recent30Loading,
    submitting,
    pollingTimedOut,
    actionError,
    historyItems,
    historyPage,
    historyHasMore,
    historyLoading,
    fetchWeek,
    fetchRecent30,
    fetchHistory,
    generateWeek,
    generateRecent30,
    retryJob,
    fetchAiJob,
    setPollingTimedOut,
    reset
  }
})
