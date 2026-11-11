const ACTIVE_STATES = new Set(['queued', 'running'])
const defaultScheduler = {
  now: () => Date.now(),
  setTimeout: (callback, delay) => setTimeout(callback, delay),
  clearTimeout: timer => clearTimeout(timer),
}

/**
 * 只观察任务GET，不触发生成。一次请求结束后才安排下一次；默认2秒间隔、最多观察120秒。
 * stop立即使本地代次失效并取消请求；底层取消尚未结束时，新的start仍等待旧请求退出。
 */
export function createAiJobPoller({
  fetchJob, onUpdate, onStop = () => {}, intervalMs = 2000,
  maxObserveMs = 120_000, scheduler = defaultScheduler,
}) {
  let current = null
  let requesting = false
  let nextTimer = null
  let limitTimer = null

  function stop(reason = 'stopped', error) {
    const previous = current
    current = null
    scheduler.clearTimeout(nextTimer)
    scheduler.clearTimeout(limitTimer)
    previous?.controller.abort()
    if (previous) {
      onStop(reason, error)
    }
  }

  function matches(job, source) {
    return String(job.id) === String(source.jobId) &&
      String(job.targetId) === String(source.recordId) &&
      String(job.targetVersion) === String(source.contentVersion)
  }

  function schedule(delay) {
    scheduler.clearTimeout(nextTimer)
    nextTimer = scheduler.setTimeout(tick, delay)
  }

  async function tick() {
    if (!current || requesting) {
      return
    }
    const observation = current
    requesting = true

    try {
      const job = await fetchJob(observation.jobId, { signal: observation.controller.signal })
      if (current !== observation) {
        return
      }
      if (!matches(job, observation)) {
        stop('changed')
        return
      }

      onUpdate(job)
      if (current !== observation) {
        return
      }
      if (!ACTIVE_STATES.has(job.status)) {
        stop('complete')
        return
      }
      schedule(intervalMs)
    } catch (error) {
      if (current === observation) {
        stop('network', error)
      }
    } finally {
      requesting = false
      // 旧代次退出后才允许启动等待中的新代次，不能因start/stop交错让GET重叠。
      if (current && current !== observation) {
        schedule(0)
      }
    }
  }

  function start(source) {
    stop()
    current = { ...source, controller: new AbortController(), startedAt: scheduler.now() }
    limitTimer = scheduler.setTimeout(() => stop('limit'), maxObserveMs)
    void tick()
  }

  return { start, stop }
}
