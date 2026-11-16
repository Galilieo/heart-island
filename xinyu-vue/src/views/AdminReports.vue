<script setup>
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import AdminReportDetailDialog from '../components/admin/AdminReportDetailDialog.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { useToast } from '../composables/useToast'
import { useAdminStore } from '../stores/admin'
import {
  MAX_HANDLE_NOTE_LENGTH,
  reasonLabel,
  adminReportResultMeta,
  formatReportTime
} from '../utils/communityReport'

const adminStore = useAdminStore()
const toast = useToast()

const {
  reports,
  reportsTotal,
  reportsLoading,
  reportFilters,
  reportPages,
  pendingReportCount
} = storeToRefs(adminStore)

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)
const selectedHideReason = ref('ILLEGAL')
const handleNote = ref('')
const submitting = ref(false)

const isPendingTab = computed(() => reportFilters.value.status !== 'DONE')

const PENDING_COLUMNS = [
  { key: 'targetType', title: '对象', width: 150, mobile: 'eyebrow' },
  {
    key: 'excerpt',
    title: '内容',
    mobile: 'body',
    format: (row) => row.excerpt || '-',
    tooltip: (row) => row.excerpt || ''
  },
  {
    key: 'reporterCount',
    title: '举报人数',
    width: 84,
    align: 'right',
    mobile: 'meta',
    format: (row) => `${row.reporterCount || 0} 人`
  },
  {
    key: 'reasonCounts',
    title: '原因',
    width: 190,
    mobile: 'meta',
    format: (row) => formatReasonCounts(row.reasonCounts)
  },
  {
    key: 'firstReportTime',
    title: '首次举报',
    width: 120,
    mono: true,
    mobile: 'meta',
    format: (row) => formatReportTime(row.firstReportTime)
  },
  { key: 'targetUserId', title: '作者 ID', width: 88, mono: true, mobile: 'hidden' },
  { key: 'actions', title: '操作', width: 88, align: 'right', mobile: 'hidden' }
]

const DONE_COLUMNS = [
  {
    key: 'targetType',
    title: '对象',
    width: 80,
    mobile: 'eyebrow',
    format: (row) => (row.targetType === 'REPLY' ? '回复' : '帖子')
  },
  {
    key: 'excerpt',
    title: '内容',
    mobile: 'body',
    format: (row) => row.excerpt || '-',
    tooltip: (row) => row.excerpt || ''
  },
  { key: 'resultStatus', title: '结果', width: 180, mobile: 'status' },
  {
    key: 'handlerName',
    title: '处理人',
    width: 96,
    mono: true,
    mobile: 'meta',
    format: (row) => row.handlerName || '-'
  },
  {
    key: 'handleTime',
    title: '时间',
    width: 120,
    mono: true,
    mobile: 'meta',
    format: (row) => formatReportTime(row.handleTime)
  },
  {
    key: 'handleNote',
    title: '备注',
    width: 150,
    mobile: 'hidden',
    format: (row) => row.handleNote || '-'
  },
  { key: 'actions', title: '操作', width: 88, align: 'right', mobile: 'hidden' }
]

const columns = computed(() => (isPendingTab.value ? PENDING_COLUMNS : DONE_COLUMNS))

onMounted(() => {
  adminStore.fetchReports()
  adminStore.fetchPendingReportCount().catch(() => null)
})

function formatReasonCounts(counts) {
  if (!counts) return '-'
  return Object.entries(counts)
    .map(([code, count]) => (count > 1 ? `${reasonLabel(code)} ×${count}` : reasonLabel(code)))
    .join(' · ')
}

function rowKeyOf(row) {
  return `${row.targetType}-${row.targetId}`
}

function switchTab(tab) {
  adminStore.setReportTab(tab)
}

function handlePageChange(pageNum) {
  adminStore.changeReportPage(pageNum)
}

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
  if (submitting.value) return
  detailVisible.value = false
  currentDetail.value = null
}

async function submitHandle(action) {
  if (!currentDetail.value || submitting.value) return
  if (handleNote.value.trim().length > MAX_HANDLE_NOTE_LENGTH) {
    toast.error(`内部备注不能超过 ${MAX_HANDLE_NOTE_LENGTH} 字`)
    return
  }
  submitting.value = true
  try {
    const res = await adminStore.handleReport({
      targetType: currentDetail.value.targetType,
      targetId: Number(currentDetail.value.targetId),
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
    submitting.value = false
  }
}
</script>

<template>
  <div class="admin-reports">
    <AdminPageTitle
      title="举报处理"
      desc="同一条内容的举报合成一行，「安全担心」排在最前，其余按首次举报时间先后。"
    />

    <div class="admin-reports__tabs" role="tablist" aria-label="举报状态筛选">
      <button
        type="button"
        role="tab"
        class="admin-reports__tab"
        :class="{ 'is-active': isPendingTab }"
        :aria-selected="isPendingTab"
        @click="switchTab('PENDING')"
      >
        待处理
        <span v-if="pendingReportCount > 0" class="admin-reports__tab-badge">
          {{ pendingReportCount }}
        </span>
      </button>
      <button
        type="button"
        role="tab"
        class="admin-reports__tab"
        :class="{ 'is-active': !isPendingTab }"
        :aria-selected="!isPendingTab"
        @click="switchTab('DONE')"
      >
        已处理
      </button>
    </div>

    <AdminDataTable
      :columns="columns"
      :rows="reports"
      :total="Number(reportsTotal)"
      :loading="reportsLoading"
      :order-hint="isPendingTab ? '含安全担心优先 · 首次举报正序' : '按处理时间倒序'"
      :row-key="rowKeyOf"
    >
      <template #cell-targetType="{ row }">
        <span class="admin-reports__type-cell">
          <span>{{ row.targetType === 'REPLY' ? '回复' : '帖子' }}</span>
          <BaseTag v-if="row.hasSafety && isPendingTab" tone="warn" size="sm">
            安全担心 · 优先
          </BaseTag>
        </span>
      </template>

      <template #cell-resultStatus="{ row }">
        <AdminStatus
          :text="adminReportResultMeta(row.resultStatus, row.hideReason).text"
          :tone="adminReportResultMeta(row.resultStatus, row.hideReason).tone"
        />
      </template>

      <template #actions="{ row }">
        <AdminRowAction @click="openDetail(row)">
          {{ isPendingTab ? '处理' : '详情' }}
        </AdminRowAction>
      </template>

      <template #loading>
        <SkeletonCard v-for="i in 3" :key="i" rows="3" />
      </template>

      <template #empty>
        <EmptyState
          :title="isPendingTab ? '暂无待处理举报' : '暂无已处理举报'"
          :hint="isPendingTab ? '社区内容都已核对完毕。' : '处理后的举报记录会保留在这里。'"
        />
      </template>

      <template #pagination>
        <BasePagination
          :page-num="Number(reportFilters.pageNum)"
          :pages="Number(reportPages)"
          :total="Number(reportsTotal)"
          :page-size="Number(reportFilters.pageSize)"
          @change="handlePageChange"
        />
      </template>
    </AdminDataTable>

    <AdminReportDetailDialog
      v-model="detailVisible"
      v-model:selected-hide-reason="selectedHideReason"
      v-model:handle-note="handleNote"
      :loading="detailLoading"
      :detail="currentDetail"
      :is-pending-tab="isPendingTab"
      :submitting="submitting"
      @close="closeDetail"
      @submit="submitHandle"
    />
  </div>
</template>

<style scoped>
.admin-reports {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-reports__tabs {
  display: flex;
  gap: 8px;
}

.admin-reports__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 36px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
}

.admin-reports__tab.is-active {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.admin-reports__tab-badge {
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--danger-ink);
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
}

.admin-reports__type-cell {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
</style>
