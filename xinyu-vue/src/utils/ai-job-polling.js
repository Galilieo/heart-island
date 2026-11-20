// 字符串与任务 API 一致。failed 只需一次读取，供页面取得失败原因及冷却时间。
export const AI_JOB_STATE = Object.freeze({
  QUEUED: 'queued',
  RUNNING: 'running',
  FAILED: 'failed',
  SUCCEEDED: 'succeeded'
})
const ACTIVE_STATES = new Set([AI_JOB_STATE.QUEUED, AI_JOB_STATE.RUNNING])
const defaultScheduler = {
  now: () => Date.now(),
  setTimeout: (callback, delay) => setTimeout(callback, delay),
  clearTimeout: (timer) => clearTimeout(timer)
}

/**
 * 只观察任务GET，不触发生成。一次请求结束后才安排下一次；默认2秒间隔、最多观察120秒。
 * stop立即使本地代次失效并取消请求；底层取消尚未结束时，新的start仍等待旧请求退出。
 */
export function createAiJobPoller({
  fetchJob,
  onUpdate,
  onStop = () => {},
  intervalMs = 2000,
  maxObserveMs = 120_000,
  scheduler = defaultScheduler
}) {
  let currentObservation = null
  let requestInFlight = false
  let nextTimer = null
  let limitTimer = null

  function stop(reason = 'stopped', error) {
    const previous = currentObservation
    currentObservation = null
    scheduler.clearTimeout(nextTimer)
    scheduler.clearTimeout(limitTimer)
    previous?.controller.abort()
    if (previous) {
      onStop(reason, error)
    }
  }

  // 心情和回顾还提供来源版本；帖子用当前版本的唯一 jobId 区分编辑代次。
  function matchesObservation(job, source) {
    return (
      String(job.id) === String(source.jobId) &&
      String(job.targetId) === String(source.recordId) &&
      (source.contentVersion === undefined ||
        String(job.targetVersion) === String(source.contentVersion))
    )
  }

  function schedule(delay) {
    scheduler.clearTimeout(nextTimer)
    nextTimer = scheduler.setTimeout(tick, delay)
  }

  async function tick() {
    if (!currentObservation || requestInFlight) {
      return
    }
    const observation = currentObservation
    requestInFlight = true

    try {
      const job = await fetchJob(observation.jobId, { signal: observation.controller.signal })
      if (currentObservation !== observation) {
        return
      }
      if (!matchesObservation(job, observation)) {
        stop('changed')
        return
      }

      onUpdate(job)
      if (currentObservation !== observation) {
        return
      }
      if (!ACTIVE_STATES.has(job.status)) {
        stop('complete')
        return
      }
      schedule(intervalMs)
    } catch (error) {
      if (currentObservation === observation) {
        stop('network', error)
      }
    } finally {
      requestInFlight = false
      // 旧代次退出后才允许启动等待中的新代次，不能因start/stop交错让GET重叠。
      if (currentObservation && currentObservation !== observation) {
        schedule(0)
      }
    }
  }

  function start(source) {
    stop()
    currentObservation = {
      ...source,
      controller: new AbortController(),
      startedAt: scheduler.now()
    }
    limitTimer = scheduler.setTimeout(() => stop('limit'), maxObserveMs)
    void tick()
  }

  return { start, stop }
}
