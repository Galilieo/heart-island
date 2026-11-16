import { watch } from 'vue'

export function parseQueryStart(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
}

/**
 * 回顾页周次加载与路由 query.start 同步。
 */
export function useReviewNavigation({
  route,
  router,
  reviewStore,
  mode,
  weekStart,
  loadError,
  stopPolling,
  toast
}) {
  async function loadWeek(start = '') {
    loadError.value = ''
    reviewStore.setPollingTimedOut(false)
    try {
      const res = await reviewStore.fetchWeek(start)
      if (!res.ok) {
        loadError.value = res.message || '统计加载失败'
        toast.error(loadError.value)
        return false
      }
      weekStart.value = res.data.rangeStart
      return true
    } catch (error) {
      loadError.value = error?.userMessage || error?.message || '统计加载失败'
      toast.error(loadError.value)
      return false
    }
  }

  async function loadRecent30() {
    loadError.value = ''
    reviewStore.setPollingTimedOut(false)
    try {
      const res = await reviewStore.fetchRecent30()
      if (!res.ok) {
        loadError.value = res.message || '统计加载失败'
        toast.error(loadError.value)
      }
    } catch (error) {
      loadError.value = error?.userMessage || error?.message || '统计加载失败'
      toast.error(loadError.value)
    }
  }

  async function navigateToWeek(start) {
    if (!start || start === weekStart.value) {
      return
    }
    stopPolling()
    const ok = await loadWeek(start)
    if (ok && parseQueryStart(route.query.start) !== weekStart.value) {
      await router.replace({ query: { ...route.query, start: weekStart.value } })
    }
  }

  watch(
    () => route.query.start,
    (nextStart, prevStart) => {
      if (nextStart === prevStart || mode.value !== 'week') {
        return
      }
      const parsed = parseQueryStart(nextStart)
      if (!parsed) {
        stopPolling()
        loadWeek('')
        return
      }
      if (parsed !== weekStart.value) {
        stopPolling()
        loadWeek(parsed)
      }
    }
  )

  return {
    loadWeek,
    loadRecent30,
    navigateToWeek
  }
}
