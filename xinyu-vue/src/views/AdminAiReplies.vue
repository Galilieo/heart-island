<script setup>
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminFilterBar from '../components/admin/AdminFilterBar.vue'
import AdminFilterField from '../components/admin/AdminFilterField.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import { useAdminStore } from '../stores/admin'

const adminStore = useAdminStore()
const detailVisible = ref(false)
const currentRecord = ref(null)

const {
  aiReplies,
  aiRepliesTotal,
  aiRepliesLoading,
  aiReplyFilters,
  aiReplyPages
} = storeToRefs(adminStore)

const targetTypeOptions = [
  { label: '全部类型', value: '' },
  { label: '心情记录', value: 'MOOD_RECORD' },
  { label: '帖子', value: 'POST' },
  { label: '总结', value: 'SUMMARY' }
]

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '成功', value: '1' },
  { label: '失败', value: '0' }
]

// 固定列合计 686，加 2 个自动列（AI 回复、错误信息）×160 = 1006，
// 1280 宽度下不出现横向滚动；两个自动列截断后用 title 展示全文。
const columns = [
  { key: 'id', title: 'ID', width: 64, mono: true, mobile: 'eyebrow', mobilePrefix: '#' },
  {
    key: 'userId',
    title: '用户 ID',
    width: 80,
    mono: true,
    mobile: 'eyebrow',
    mobilePrefix: '用户 '
  },
  {
    key: 'targetType',
    title: '关联类型',
    width: 80,
    mobile: 'eyebrow',
    format: (row) => targetTypeText(row.targetType)
  },
  { key: 'targetId', title: '关联 ID', width: 64, mono: true, mobile: 'hidden' },
  {
    key: 'modelName',
    title: '模型',
    width: 110,
    mobile: 'hidden',
    format: (row) => row.modelName || '-',
    tooltip: (row) => row.modelName || ''
  },
  { key: 'status', title: '状态', width: 72, mobile: 'status' },
  {
    key: 'replyContent',
    title: 'AI 回复',
    mobile: 'body',
    format: (row) => row.replyContent || '-',
    tooltip: (row) => row.replyContent || ''
  },
  {
    key: 'errorMessage',
    title: '错误信息',
    mobile: 'hidden',
    format: (row) => row.errorMessage || '-',
    tooltip: (row) => row.errorMessage || ''
  },
  {
    key: 'createTime',
    title: '创建时间',
    width: 140,
    mono: true,
    mobile: 'meta',
    mobileLabel: '',
    format: (row) => formatTime(row.createTime)
  },
  { key: 'actions', title: '操作', width: 76, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchAiReplies()
})

function statusText(status) {
  return status === 1 ? '成功' : '失败'
}

function targetTypeText(targetType) {
  const map = {
    MOOD_RECORD: '心情记录',
    POST: '帖子',
    SUMMARY: '总结'
  }

  return map[targetType] || targetType || '-'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function handleSearch() {
  adminStore.searchAiReplies()
}

function handleReset() {
  adminStore.resetAiReplyFilters()
}

function handlePageChange(pageNum) {
  adminStore.changeAiReplyPage(pageNum)
}

function openDetail(record) {
  currentRecord.value = record
  detailVisible.value = true
}

function closeDetail() {
  detailVisible.value = false
  currentRecord.value = null
}
</script>

<template>
  <div class="admin-ai-replies">
    <AdminPageTitle
      title="AI 记录"
      desc="查看 AI 回复、失败原因，以及对应的心情记录或帖子。"
    />

    <AdminFilterBar
      :loading="aiRepliesLoading"
      @search="handleSearch"
      @reset="handleReset"
    >
      <AdminFilterField size="md">
        <BaseInput
          v-model="aiReplyFilters.userId"
          label="用户ID"
          placeholder="输入用户ID"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="md">
        <BaseSelect
          v-model="aiReplyFilters.targetType"
          label="关联类型"
          :options="targetTypeOptions"
          placeholder=""
        />
      </AdminFilterField>

      <AdminFilterField size="sm" full>
        <BaseSelect
          v-model="aiReplyFilters.status"
          label="状态"
          :options="statusOptions"
          placeholder=""
        />
      </AdminFilterField>

      <AdminFilterField size="wide" full>
        <BaseInput
          v-model="aiReplyFilters.keyword"
          label="关键词"
          placeholder="搜索回复、错误或提示词"
          @enter="handleSearch"
        />
      </AdminFilterField>
    </AdminFilterBar>

    <AdminDataTable
      :columns="columns"
      :rows="aiReplies"
      :total="Number(aiRepliesTotal)"
      :loading="aiRepliesLoading"
      row-key="id"
      order-hint="按创建时间倒序"
    >
      <template #cell-status="{ row }">
        <AdminStatus
          :text="statusText(row.status)"
          :tone="row.status === 1 ? 'ok' : 'bad'"
        />
      </template>

      <template #actions="{ row }">
        <AdminRowAction @click="openDetail(row)">
          详情
        </AdminRowAction>
      </template>

      <template #loading>
        <SkeletonCard v-for="i in 3" :key="i" rows="3" />
      </template>

      <template #empty>
        <EmptyState
          title="暂无 AI 回复记录"
          hint="没有找到匹配的调用记录，可以调整筛选条件后再查。"
        />
      </template>

      <template #pagination>
        <BasePagination
          :page-num="Number(aiReplyFilters.pageNum)"
          :pages="Number(aiReplyPages)"
          :total="Number(aiRepliesTotal)"
          :page-size="Number(aiReplyFilters.pageSize)"
          @change="handlePageChange"
        />
      </template>
    </AdminDataTable>

    <BaseDialog
      v-model="detailVisible"
      title="AI 回复详情"
      width="lg"
      @close="closeDetail"
    >
      <div v-if="currentRecord" class="admin-ai-replies__detail">
        <div class="admin-ai-replies__detail-grid">
          <div>
            <span>记录ID</span>
            <strong>{{ currentRecord.id }}</strong>
          </div>
          <div>
            <span>用户ID</span>
            <strong>{{ currentRecord.userId }}</strong>
          </div>
          <div>
            <span>关联类型</span>
            <strong>{{ targetTypeText(currentRecord.targetType) }}</strong>
          </div>
          <div>
            <span>关联ID</span>
            <strong>{{ currentRecord.targetId }}</strong>
          </div>
          <div>
            <span>模型</span>
            <strong>{{ currentRecord.modelName || '-' }}</strong>
          </div>
          <div>
            <span>状态</span>
            <BaseTag :tone="currentRecord.status === 1 ? 'success' : 'danger'">
              {{ statusText(currentRecord.status) }}
            </BaseTag>
          </div>
          <div>
            <span>创建时间</span>
            <strong>{{ formatTime(currentRecord.createTime) }}</strong>
          </div>
        </div>

        <section class="admin-ai-replies__detail-section">
          <h3>提示词</h3>
          <p>{{ currentRecord.prompt || '-' }}</p>
        </section>

        <section class="admin-ai-replies__detail-section">
          <h3>AI 回复</h3>
          <p>{{ currentRecord.replyContent || '-' }}</p>
        </section>

        <section class="admin-ai-replies__detail-section">
          <h3>错误信息</h3>
          <p>{{ currentRecord.errorMessage || '-' }}</p>
        </section>
      </div>

      <template #footer>
        <BaseButton variant="plain" @click="closeDetail">
          关闭
        </BaseButton>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.admin-ai-replies {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-ai-replies__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-ai-replies__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-ai-replies__detail-grid > div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-soft);
}

.admin-ai-replies__detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.admin-ai-replies__detail-grid strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
  word-break: break-all;
}

.admin-ai-replies__detail-section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

.admin-ai-replies__detail-section h3 {
  margin: 0 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-ai-replies__detail-section p {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

@media (max-width: 768px) {
  .admin-ai-replies__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
