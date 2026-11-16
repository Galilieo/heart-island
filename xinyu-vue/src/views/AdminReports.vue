<script setup>
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import { useToast } from '../composables/useToast'
import { useAdminStore } from '../stores/admin'
import {
  HIDE_REASONS,
  MAX_HANDLE_NOTE_LENGTH,
  reasonLabel,
  adminReportResultMeta,
  formatReportTime,
  formatFullDateTime
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

const columns = computed(() => {
  if (isPendingTab.value) {
    return [
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
  }
  return [
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
})

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

    <BaseDialog
      v-model="detailVisible"
      title="举报详情"
      width="lg"
      :persistent="submitting"
      @close="closeDetail"
    >
      <SkeletonCard v-if="detailLoading" rows="4" />

      <div v-else-if="currentDetail" class="admin-reports__detail">
        <div class="admin-reports__detail-grid">
          <div>
            <span>举报对象</span>
            <strong>
              {{ currentDetail.targetType === 'REPLY' ? '回复' : '帖子' }} #{{ currentDetail.targetId }}
            </strong>
          </div>
          <div>
            <span>作者</span>
            <strong>
              用户 #{{ currentDetail.targetUserId }} · 此前被隐藏 {{ currentDetail.authorHiddenCount || 0 }} 次
            </strong>
          </div>
          <div>
            <span>举报人数</span>
            <strong>{{ currentDetail.reporterCount }} 人</strong>
          </div>
          <div>
            <span>首次举报</span>
            <strong>{{ formatFullDateTime(currentDetail.firstReportTime) }}</strong>
          </div>
        </div>

        <div v-if="currentDetail.hasSafety" class="admin-reports__safety-banner">
          这是「担心发帖人的安全」举报。隐藏不一定是最好的处理：内容本身不违规时可以选「不违规」，并留意这位用户之后的帖子。
        </div>

        <section class="admin-reports__detail-section">
          <h3>举报时的内容</h3>
          <p class="admin-reports__snapshot">{{ currentDetail.snapshotContent || '-' }}</p>
          <p v-if="currentDetail.currentStatus === 0" class="admin-reports__diff-note">
            当前内容已被作者删除。
          </p>
          <p v-else-if="currentDetail.contentChanged" class="admin-reports__diff-note">
            作者在被举报后改过内容。现在是：{{ currentDetail.currentContent }}
          </p>
          <p v-else-if="currentDetail.currentStatus === 2" class="admin-reports__diff-note">
            当前内容已处于隐藏状态（原因：{{ reasonLabel(currentDetail.currentHideReason) }}）。
          </p>
        </section>

        <section class="admin-reports__detail-section">
          <h3>举报记录</h3>
          <ul class="admin-reports__record-list">
            <li v-for="rec in currentDetail.records" :key="rec.id" class="admin-reports__record">
              <div class="admin-reports__record-head">
                <strong>{{ reasonLabel(rec.reason) }}</strong>
                <span>举报人 #{{ rec.reporterId }} · {{ formatReportTime(rec.createTime) }}</span>
              </div>
              <p v-if="rec.detail" class="admin-reports__record-detail">{{ rec.detail }}</p>
            </li>
          </ul>
        </section>

        <section v-if="!isPendingTab && currentDetail.resultStatus" class="admin-reports__detail-section">
          <h3>处理结果</h3>
          <p>
            {{ adminReportResultMeta(currentDetail.resultStatus, currentDetail.hideReason).text }}
            · 处理人 {{ currentDetail.handlerName || '-' }}
            · {{ formatFullDateTime(currentDetail.handleTime) }}
          </p>
          <p v-if="currentDetail.handleNote" class="admin-reports__handled-note">
            内部备注：{{ currentDetail.handleNote }}
          </p>
        </section>
      </div>

      <template #footer>
        <div v-if="isPendingTab && currentDetail" class="admin-reports__footer">
          <div class="admin-reports__footer-inputs">
            <BaseSelect
              v-if="currentDetail.currentStatus !== 0"
              v-model="selectedHideReason"
              label="隐藏原因"
              size="sm"
              :options="HIDE_REASONS"
            />
            <BaseInput
              v-model="handleNote"
              label="内部备注"
              placeholder="选填，只有管理员看得到"
              :maxlength="MAX_HANDLE_NOTE_LENGTH"
            />
          </div>
          <div class="admin-reports__footer-btns">
            <BaseButton variant="plain" :disabled="submitting" @click="closeDetail">
              关闭
            </BaseButton>
            <BaseButton
              variant="secondary"
              :disabled="submitting"
              :loading="submitting"
              @click="submitHandle('DISMISS')"
            >
              {{ currentDetail.currentStatus === 0 ? '关闭举报' : '不违规' }}
            </BaseButton>
            <BaseButton
              v-if="currentDetail.currentStatus !== 0"
              variant="danger"
              :disabled="submitting"
              :loading="submitting"
              @click="submitHandle('HIDE')"
            >
              隐藏内容
            </BaseButton>
          </div>
        </div>
        <BaseButton v-else variant="plain" @click="closeDetail">
          关闭
        </BaseButton>
      </template>
    </BaseDialog>
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

.admin-reports__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-reports__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-reports__detail-grid > div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-soft);
}

.admin-reports__detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.admin-reports__detail-grid strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
  word-break: break-word;
}

.admin-reports__safety-banner {
  padding: 12px 14px;
  border: 1px solid var(--warn-line, rgba(212, 147, 57, 0.35));
  border-radius: var(--radius-2);
  background: var(--warn-soft, rgba(242, 184, 75, 0.12));
  color: var(--warn-ink, #9a6011);
  font-size: var(--fs-sm);
  line-height: 1.65;
}

.admin-reports__detail-section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

.admin-reports__detail-section h3 {
  margin: 0 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-reports__snapshot {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-reports__diff-note {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-1);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-reports__record-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-reports__record {
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.admin-reports__record:first-child {
  padding-top: 0;
  border-top: none;
}

.admin-reports__record-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.admin-reports__record-head strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
}

.admin-reports__record-detail {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
  white-space: pre-wrap;
}

.admin-reports__handled-note {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-reports__footer {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-reports__footer-inputs {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 10px;
}

.admin-reports__footer-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .admin-reports__detail-grid,
  .admin-reports__footer-inputs {
    grid-template-columns: 1fr;
  }
}
</style>
