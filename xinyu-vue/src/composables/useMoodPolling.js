import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { createAiJobPoller } from '../utils/ai-job-polling.js'

// 列表默认5条＋当前详情1条；页面最多观察6项，不把全部历史任务放进内存队列。
const MAX_OBSERVATIONS = 6
const OBSERVABLE_STATES = new Set(['queued', 'running', 'failed'])

/** 页面负责生命周期，store负责版本匹配；查询停止不改变服务器上的任务状态。 */
export function useMoodPolling(store, userStore) {
  const visible = ref(!document.hidden)
  const now = ref(Date.now())
  let clockTimer

  // 固定6个可复用槽位。切换来源也复用轮询器，等待已取消的旧GET退出后才发新GET。
  const slots = Array.from({ length: MAX_OBSERVATIONS }, () => {
    const slot = { key: null, source: null, poller: null }
    slot.poller = createAiJobPoller({
      fetchJob: store.fetchJob,
      onUpdate: store.applyJob,
      onStop: (reason, error) => {
        if (slot.source) {
          store.markObservation(slot.source.recordId, reason, error)
        }
      },
    })
    return slot
  })

  const sources = computed(() => {
    const unique = new Map()
    for (const record of [store.selected, ...store.list]) {
      if (!record?.jobId || !OBSERVABLE_STATES.has(record.aiReplyState)) {
        continue
      }
      if (store.pendingIds.has(String(record.id))) {
        continue
      }
      const key = [record.id, record.contentVersion, record.jobId, store.observationEpoch].join(':')
      unique.set(key, { key, recordId: record.id, contentVersion: record.contentVersion, jobId: record.jobId })
    }
    return [...unique.values()].slice(0, MAX_OBSERVATIONS)
  })

  function reconcile() {
    const desired = visible.value && userStore.isLoggedIn ? sources.value : []
    const keys = new Set(desired.map(source => source.key))
    const retained = new Set(slots.filter(slot => keys.has(slot.key)))

    for (const source of desired) {
      if (slots.some(slot => slot.key === source.key)) {
        continue
      }
      const slot = slots.find(candidate => !retained.has(candidate))
      slot.poller.stop()
      slot.key = source.key
      slot.source = source
      retained.add(slot)
      slot.poller.start(source)
    }
    for (const slot of slots) {
      if (retained.has(slot)) {
        continue
      }
      slot.poller.stop()
      slot.key = null
      slot.source = null
    }
  }

  watch(() => userStore.token, () => store.reset(), { immediate: true, flush: 'sync' })
  watch([sources, visible, () => userStore.isLoggedIn], reconcile)

  function visibilityChanged() {
    visible.value = !document.hidden
    now.value = Date.now()
  }

  onMounted(() => {
    document.addEventListener('visibilitychange', visibilityChanged)
    clockTimer = setInterval(() => { now.value = Date.now() }, 1000)
    reconcile()
  })
  onUnmounted(() => {
    document.removeEventListener('visibilitychange', visibilityChanged)
    clearInterval(clockTimer)
    for (const slot of slots) slot.poller.stop()
  })

  return { now }
}
