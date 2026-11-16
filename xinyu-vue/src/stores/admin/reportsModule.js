import { reactive, ref } from 'vue'
import { adminApi } from '../../api'
import { createPageCount } from './helpers'

/**
 * 后台举报队列与处理状态管理。
 */
export function createReportsModule() {
  const pendingReportCount = ref(0)
  const reports = ref([])
  const reportsTotal = ref(0)
  const reportsLoading = ref(false)
  const reportFilters = reactive({
    status: 'PENDING',
    pageNum: 1,
    pageSize: 10
  })
  const reportPages = createPageCount(
    () => reportsTotal.value,
    () => reportFilters.pageSize
  )

  async function fetchPendingReportCount() {
    const res = await adminApi.reportPendingCount()
    if (res.ok) {
      pendingReportCount.value = Number(res.data || 0)
    }
    return res
  }

  async function fetchReports() {
    reportsLoading.value = true
    try {
      const res = await adminApi.reportPage({
        status: reportFilters.status,
        pageNum: reportFilters.pageNum,
        pageSize: reportFilters.pageSize
      })
      if (res.ok) {
        reports.value = res.data?.records || []
        reportsTotal.value = Number(res.data?.total || 0)
      }
      return res
    } finally {
      reportsLoading.value = false
    }
  }

  function setReportTab(tab) {
    reportFilters.status = tab
    reportFilters.pageNum = 1
    return fetchReports()
  }

  function changeReportPage(pageNum) {
    reportFilters.pageNum = pageNum
    return fetchReports()
  }

  function fetchReportDetail(targetType, targetId) {
    return adminApi.reportDetail(targetType, targetId)
  }

  async function handleReport(payload) {
    const res = await adminApi.handleReport(payload)
    const conflict = res.status === 409 || String(res.message || '').includes('已被其他管理员处理')
    if (res.ok || conflict) {
      await Promise.all([fetchReports(), fetchPendingReportCount()])
    }
    return { ...res, conflict }
  }

  return {
    pendingReportCount,
    reports,
    reportsTotal,
    reportsLoading,
    reportFilters,
    reportPages,
    fetchPendingReportCount,
    fetchReports,
    setReportTab,
    changeReportPage,
    fetchReportDetail,
    handleReport
  }
}
