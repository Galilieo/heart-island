import { computed } from 'vue'

/**
 * 同步分页查询结果，保留现有页码/页大小兜底语义。
 */
export function applyPagedResult(itemsRef, paginationState, data) {
  itemsRef.value = data?.records || []
  paginationState.total = data?.total || 0
  paginationState.pageNum = data?.current || paginationState.pageNum
  paginationState.pageSize = data?.size || paginationState.pageSize
}

/** 计算总页数，数据为空时至少保留 1 页。 */
export function createPageCount(totalSource, pageSizeSource) {
  return computed(() => Math.max(1, Math.ceil(totalSource() / pageSizeSource())))
}

/** 仅当筛选值去空白后非空时写入查询参数对象。 */
export function appendTrimmedFilter(params, key, value) {
  const trimmed = String(value ?? '').trim()
  if (trimmed) {
    params[key] = trimmed
  }
}

/** 仅当筛选值非空串/null/undefined 时转换为数字写入查询参数对象。 */
export function appendNumericFilter(params, key, value) {
  if (value !== '' && value !== null && value !== undefined) {
    params[key] = Number(value)
  }
}
