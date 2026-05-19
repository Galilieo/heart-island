import { reactive, computed } from 'vue'

/**
 * 分页状态 + 自动调用 fetcher 的小封装。
 *
 * @param {(params:{pageNum:number,pageSize:number}) => Promise<{total:number,pages:number}>} fetcher
 *        fetcher 必须返回 { total, pages }（或自己同步进 list）
 * @param {object} options
 * @param {number} [options.pageSize=10]
 * @param {number} [options.pageNum=1]
 */
export function usePagination(fetcher, options = {}) {
  const state = reactive({
    pageNum: options.pageNum ?? 1,
    pageSize: options.pageSize ?? 10,
    total: 0,
    pages: 0,
    loading: false
  })

  async function refresh(extra) {
    state.loading = true
    try {
      const res = await fetcher({
        pageNum: state.pageNum,
        pageSize: state.pageSize,
        ...(extra || {})
      })
      if (res && typeof res === 'object') {
        if (typeof res.total === 'number') state.total = res.total
        if (typeof res.pages === 'number') state.pages = res.pages
      }
      return res
    } finally {
      state.loading = false
    }
  }

  function goto(page) {
    const target = Math.max(1, Math.min(state.pages || 1, page))
    if (target === state.pageNum) return
    state.pageNum = target
    return refresh()
  }

  function reset() {
    state.pageNum = 1
    return refresh()
  }

  const hasPrev = computed(() => state.pageNum > 1)
  const hasNext = computed(() => state.pageNum < (state.pages || 1))

  return {
    state,
    refresh,
    goto,
    reset,
    next: () => goto(state.pageNum + 1),
    prev: () => goto(state.pageNum - 1),
    hasPrev,
    hasNext
  }
}
