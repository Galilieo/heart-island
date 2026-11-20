import { ref } from 'vue'
import { useToast } from './useToast'
import { PENDING_RISK } from '../utils/communityRisk'

/**
 * 后台帖子/回复详情弹窗与隐藏恢复状态操作复用。
 */
export function useAdminContentDetail({
  fetchDetail,
  updateStatus,
  reviewRisk,
  loadErrorText,
  hideSuccessText
}) {
  const toast = useToast()

  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const currentDetail = ref(null)
  const selectedHideReason = ref('ILLEGAL')
  const savingStatus = ref(false)
  const reviewingRisk = ref(false)

  async function openDetail(row) {
    detailVisible.value = true
    detailLoading.value = true
    currentDetail.value = null
    selectedHideReason.value = 'ILLEGAL'
    try {
      const res = await fetchDetail(row.id)
      if (res.ok && res.data) {
        currentDetail.value = res.data
      } else {
        detailVisible.value = false
        toast.error(res.message || loadErrorText)
      }
    } finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    if (savingStatus.value || reviewingRisk.value) {
      return
    }
    detailVisible.value = false
    currentDetail.value = null
  }

  async function handleStatusAction(nextStatus) {
    if (!currentDetail.value || savingStatus.value || reviewingRisk.value) {
      return
    }
    savingStatus.value = true
    try {
      const hideReason = nextStatus === 2 ? selectedHideReason.value : undefined
      const res = await updateStatus(currentDetail.value.id, nextStatus, hideReason)
      if (res.ok) {
        toast.success(nextStatus === 2 ? hideSuccessText : '已恢复显示')
        detailVisible.value = false
        currentDetail.value = null
      } else {
        toast.error(res.message || '操作失败')
        if (res.status === 409) {
          detailVisible.value = false
          currentDetail.value = null
        }
      }
    } finally {
      savingStatus.value = false
    }
  }

  async function handleRiskReview() {
    const detail = currentDetail.value
    if (detail?.riskStatus !== PENDING_RISK || savingStatus.value || reviewingRisk.value) {
      return
    }

    reviewingRisk.value = true
    try {
      const result = await reviewRisk(detail.id)
      if (result.ok) {
        toast.success('已标为关注')
      } else if (result.status === 409) {
        toast.warn(result.message || '这条内容已不在待关注状态')
      } else {
        toast.error(result.message || '标记失败')
      }

      if (result.refreshFailed) {
        toast.warn('列表刷新失败，请刷新查看')
      }

      if (result.ok || result.status === 409) {
        detailVisible.value = false
        currentDetail.value = null
      }
    } catch (error) {
      toast.error(error?.userMessage || '操作结果待确认，请刷新列表')
    } finally {
      reviewingRisk.value = false
    }
  }

  return {
    detailVisible,
    detailLoading,
    currentDetail,
    selectedHideReason,
    savingStatus,
    reviewingRisk,
    openDetail,
    closeDetail,
    handleStatusAction,
    handleRiskReview
  }
}
