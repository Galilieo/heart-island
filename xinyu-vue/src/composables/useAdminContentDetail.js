import { ref } from 'vue'
import { useToast } from './useToast'

/**
 * 后台帖子/回复详情弹窗与隐藏恢复状态操作复用。
 */
export function useAdminContentDetail({
  fetchDetail,
  updateStatus,
  loadErrorText,
  hideSuccessText
}) {
  const toast = useToast()

  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const currentDetail = ref(null)
  const selectedHideReason = ref('ILLEGAL')
  const savingStatus = ref(false)

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
    if (savingStatus.value) {
      return
    }
    detailVisible.value = false
    currentDetail.value = null
  }

  async function handleStatusAction(nextStatus) {
    if (!currentDetail.value || savingStatus.value) {
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

  return {
    detailVisible,
    detailLoading,
    currentDetail,
    selectedHideReason,
    savingStatus,
    openDetail,
    closeDetail,
    handleStatusAction
  }
}
