import { computed, ref } from 'vue'
import { MAX_HANDLE_NOTE_LENGTH } from '../utils/communityReport'

/**
 * 举报详情弹窗状态与裁决提交流程。
 */
export function useAdminReportHandle({ adminStore, toast }) {
  const detailVisible = ref(false)
  const detailLoading = ref(false)
  const currentDetail = ref(null)
  const selectedHideReason = ref('ILLEGAL')
  const handleNote = ref('')
  const submittingAction = ref('')
  const submitting = computed(() => Boolean(submittingAction.value))

  async function openDetail(row) {
    detailVisible.value = true
    detailLoading.value = true
    currentDetail.value = null
    handleNote.value = ''
    try {
      const res = await adminStore.fetchReportDetail(row.targetType, row.targetId)
      if (res.ok && res.data) {
        currentDetail.value = res.data
        selectedHideReason.value = res.data.suggestedHideReason || 'ILLEGAL'
      } else {
        detailVisible.value = false
        toast.error(res.message || '加载举报详情失败')
      }
    } finally {
      detailLoading.value = false
    }
  }

  function closeDetail() {
    if (submitting.value) {
      return
    }
    detailVisible.value = false
    currentDetail.value = null
  }

  async function submitHandle(action) {
    if (!currentDetail.value || submitting.value) {
      return
    }
    if (handleNote.value.trim().length > MAX_HANDLE_NOTE_LENGTH) {
      toast.error(`内部备注不能超过 ${MAX_HANDLE_NOTE_LENGTH} 字`)
      return
    }
    submittingAction.value = action
    try {
      const res = await adminStore.handleReport({
        targetType: currentDetail.value.targetType,
        targetId: String(currentDetail.value.targetId),
        action,
        hideReason: action === 'HIDE' ? selectedHideReason.value : null,
        note: handleNote.value.trim() || null
      })
      if (res.ok) {
        toast.success(res.message || '处理完成')
        detailVisible.value = false
        currentDetail.value = null
      } else if (res.conflict) {
        toast.warn(res.message || '已被其他管理员处理')
        detailVisible.value = false
        currentDetail.value = null
      } else {
        toast.error(res.message || '处理失败')
      }
    } finally {
      submittingAction.value = ''
    }
  }

  return {
    detailVisible,
    detailLoading,
    currentDetail,
    selectedHideReason,
    handleNote,
    submitting,
    submittingAction,
    openDetail,
    closeDetail,
    submitHandle
  }
}
