import { onMounted, onUnmounted, ref, watch } from 'vue'
import { createAiJobPoller } from '../utils/ai-job-polling'

/**
 * 回顾页任务轮询与页面可见性同步。
 * 保持轮询的启动、停止、页面隐藏/恢复与卸载清理顺序，只做 GET 观察，不触发生成。
 */
export function useReviewPolling({
  mode,
  weekStart,
  activeStats,
  pollingTimedOut,
  reviewStore,
  userStore,
  onComplete,
  onNetworkError
}) {
  const pageVisible = ref(typeof document === 'undefined' ? true : !document.hidden)

  let activePollKey = ''
  let pollingMode = 'week'
  let pollingWeekStart = ''

  const poller = createAiJobPoller({
    fetchJob: (jobId, options) => reviewStore.fetchAiJob(jobId, options),
    onUpdate: () => {},
    onStop: (reason) => {
      activePollKey = ''
      if (reason === 'complete') {
        onComplete?.(pollingMode, pollingWeekStart)
        return
      }
      if (reason === 'limit') {
        reviewStore.setPollingTimedOut(true)
        return
      }
      if (reason === 'network') {
        reviewStore.setPollingTimedOut(true)
        onNetworkError?.()
      }
    }
  })

  function stopPolling() {
    activePollKey = ''
    poller.stop()
  }

  function syncPoller() {
    const active = activeStats.value?.active
    const shouldPoll =
      pageVisible.value && userStore.isLoggedIn && Boolean(active?.jobId) && !pollingTimedOut.value

    if (!shouldPoll) {
      stopPolling()
      return
    }

    const nextKey = `${mode.value}:${active.id}:${active.revision}:${active.jobId}`
    if (nextKey === activePollKey) {
      return
    }

    activePollKey = nextKey
    pollingMode = mode.value
    pollingWeekStart = weekStart.value
    poller.start({
      jobId: active.jobId,
      recordId: active.id,
      contentVersion: active.revision
    })
  }

  function handleVisibilityChange() {
    pageVisible.value = !document.hidden
    syncPoller()
  }

  onMounted(() => {
    if (typeof document !== 'undefined') {
      document.addEventListener('visibilitychange', handleVisibilityChange)
    }
  })

  onUnmounted(() => {
    if (typeof document !== 'undefined') {
      document.removeEventListener('visibilitychange', handleVisibilityChange)
    }
    stopPolling()
  })

  watch(
    () => [
      mode.value,
      weekStart.value,
      activeStats.value?.active?.jobId,
      activeStats.value?.active?.revision,
      pollingTimedOut.value,
      userStore.isLoggedIn
    ],
    () => syncPoller()
  )

  watch(
    () => userStore.token,
    (next, prev) => {
      if (next !== prev) {
        stopPolling()
        reviewStore.reset()
      }
    }
  )

  return {
    stopPolling
  }
}
