import { onMounted, onUnmounted, ref, watch } from 'vue'
import { AI_JOB_STATE, createAiJobPoller } from '../utils/ai-job-polling.js'

const OBSERVED_STATES = new Set([AI_JOB_STATE.QUEUED, AI_JOB_STATE.RUNNING, AI_JOB_STATE.FAILED])
const OBSERVATION_ISSUES = new Set(['network', 'limit', 'changed'])

/** 每页至多观察一个帖子任务。终态后刷新同一帖子，切换来源或离开页面时取消请求。 */
export function useCommunityAiReply(source, store) {
  const now = ref(Date.now())
  const retrying = ref(false)
  let mounted = false
  let clock
  let replyRefresh

  function currentJobKey() {
    const post = source.value
    return post?.mine && !post.hidden && post.jobId ? `${post.id}:${post.jobId}` : null
  }

  const poller = createAiJobPoller({
    fetchJob: store.fetchAiJob,
    onUpdate(job) {
      store.applyAiJob(job)
      now.value = Date.now()
      if (job.status === AI_JOB_STATE.SUCCEEDED) {
        replyRefresh = new AbortController()
        // 轮询已交付回复；补读失败时保留该结果。
        void store.refreshAiReply(job, replyRefresh.signal).catch(() => {})
      }
    },
    onStop(reason, error) {
      if (!OBSERVATION_ISSUES.has(reason) || !currentJobKey()) return
      source.value.observationIssue = error?.response?.status === 404 ? 'removed' : reason
    }
  })

  function observe() {
    replyRefresh?.abort()
    poller.stop()
    if (!mounted || !currentJobKey() || !OBSERVED_STATES.has(source.value.aiReplyState)) return

    const post = source.value
    post.observationIssue = null
    poller.start({ recordId: post.id, jobId: post.jobId })
  }

  async function retry() {
    const key = currentJobKey()
    if (retrying.value || !key || source.value.aiReplyState !== AI_JOB_STATE.FAILED) {
      return { ok: false, message: '当前状态不能重试，请刷新后查看' }
    }

    retrying.value = true
    let result
    try {
      result = await store.retryAiJob(source.value.jobId)
    } catch (error) {
      result = { ok: false, message: error.userMessage || '重试结果暂未确认，请刷新查看' }
    } finally {
      retrying.value = false
    }

    if (mounted && key === currentJobKey()) {
      if (result.ok) store.applyAiJob(result.data)
      // 429 的响应不是任务 VO。重查任务以取得剩余冷却。
      observe()
    }
    return result
  }

  watch(currentJobKey, observe)
  onMounted(() => {
    mounted = true
    clock = setInterval(() => {
      now.value = Date.now()
    }, 1000)
    observe()
  })
  onUnmounted(() => {
    mounted = false
    clearInterval(clock)
    observe()
  })

  return { now, retrying, retry, observe }
}
