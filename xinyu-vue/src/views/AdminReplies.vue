<script setup>
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminFilterBar from '../components/admin/AdminFilterBar.vue'
import AdminFilterField from '../components/admin/AdminFilterField.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import AdminContentDetailDialog from '../components/admin/AdminContentDetailDialog.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import { useAdminContentDetail } from '../composables/useAdminContentDetail'
import { useAdminStore } from '../stores/admin'
import { contentStatusMeta, formatFullDateTime } from '../utils/communityReport'

const adminStore = useAdminStore()

const {
  replies,
  repliesLoading,
  replyFilters,
  replyPagination,
  replyPages
} = storeToRefs(adminStore)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常展示', value: '1' },
  { label: '已隐藏', value: '2' },
  { label: '作者已删除', value: '0' }
]

const {
  detailVisible,
  detailLoading,
  currentDetail,
  selectedHideReason,
  savingStatus,
  openDetail,
  closeDetail,
  handleStatusAction
} = useAdminContentDetail({
  fetchDetail: (id) => adminStore.fetchReplyDetail(id),
  updateStatus: (id, status, hideReason) => adminStore.updateReplyStatus(id, status, hideReason),
  loadErrorText: '加载回复详情失败',
  hideSuccessText: '已隐藏回复'
})

const detailGridItems = computed(() => {
  const detail = currentDetail.value
  if (!detail) {
    return []
  }
  return [
    { label: '回复 ID', value: detail.id },
    { label: '所属帖子 ID', value: detail.postId },
    { label: '用户 ID', value: detail.userId },
    { label: '状态', isStatus: true },
    { label: '发布时间', value: formatFullDateTime(detail.createTime) }
  ]
})

const detailSections = computed(() => {
  const detail = currentDetail.value
  if (!detail) {
    return []
  }
  return [
    { title: '回复内容', content: detail.content },
    { title: '所属帖子', content: detail.postContent }
  ]
})

// 回复内容自动伸缩，表格里截断、title 放全文；手机卡片按 mobile 角色渲染。
const columns = [
  { key: 'id', title: 'ID', width: 64, mono: true, mobile: 'eyebrow', mobilePrefix: '#' },
  {
    key: 'postId',
    title: '帖子 ID',
    width: 92,
    mono: true,
    mobile: 'eyebrow',
    mobilePrefix: '帖子 '
  },
  {
    key: 'userId',
    title: '用户 ID',
    width: 92,
    mono: true,
    mobile: 'eyebrow',
    mobilePrefix: '用户 '
  },
  {
    key: 'content',
    title: '回复内容',
    mobile: 'body',
    format: (row) => row.content || '-',
    tooltip: (row) => row.content || ''
  },
  {
    key: 'anonymousName',
    title: '匿名名',
    width: 96,
    mono: true,
    mobile: 'hidden',
    format: (row) => row.anonymousName || '-'
  },
  { key: 'status', title: '状态', width: 96, mobile: 'status' },
  {
    key: 'createTime',
    title: '发布时间',
    width: 140,
    mono: true,
    mobile: 'meta',
    mobileLabel: '',
    format: (row) => formatFullDateTime(row.createTime)
  },
  { key: 'actions', title: '操作', width: 92, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchReplies()
})

function handleSearch() {
  adminStore.searchReplies()
}

function handleReset() {
  adminStore.resetReplyFilters()
}

function handlePageChange(pageNum) {
  adminStore.changeReplyPage(pageNum)
}
</script>

<template>
  <div class="admin-replies">
    <AdminPageTitle
      title="回复管理"
      desc="查看帖子回复，隐藏或恢复违规内容。作者自己删除的回复只能查看。"
    />

    <AdminFilterBar
      :loading="repliesLoading"
      @search="handleSearch"
      @reset="handleReset"
    >
      <AdminFilterField size="wide" full>
        <BaseInput
          v-model="replyFilters.keyword"
          label="回复内容"
          placeholder="输入关键词搜索回复内容"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="md">
        <BaseInput
          v-model="replyFilters.postId"
          label="帖子 ID"
          placeholder="按帖子 ID 筛选"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="md">
        <BaseInput
          v-model="replyFilters.userId"
          label="用户 ID"
          placeholder="按用户 ID 筛选"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="sm" full>
        <BaseSelect
          v-model="replyFilters.status"
          label="状态"
          :options="statusOptions"
          placeholder=""
        />
      </AdminFilterField>
    </AdminFilterBar>

    <AdminDataTable
      :columns="columns"
      :rows="replies"
      :total="Number(replyPagination.total)"
      :loading="repliesLoading"
      row-key="id"
      order-hint="按发布时间倒序"
    >
      <template #cell-status="{ row }">
        <AdminStatus
          :text="contentStatusMeta(row.status).text"
          :tone="contentStatusMeta(row.status).tone"
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
          title="暂无回复"
          hint="没有找到匹配的回复，可以调整筛选条件后再查。"
        />
      </template>

      <template #pagination>
        <BasePagination
          :page-num="Number(replyPagination.pageNum)"
          :pages="Number(replyPages)"
          :total="Number(replyPagination.total)"
          :page-size="Number(replyPagination.pageSize)"
          @change="handlePageChange"
        />
      </template>
    </AdminDataTable>

    <AdminContentDetailDialog
      v-model="detailVisible"
      v-model:selected-hide-reason="selectedHideReason"
      title="回复详情"
      :loading="detailLoading"
      :detail="currentDetail"
      :grid-items="detailGridItems"
      :sections="detailSections"
      :saving-status="savingStatus"
      readonly-tip="作者自己删除的回复只能查看。"
      @close="closeDetail"
      @status-action="handleStatusAction"
    />
  </div>
</template>

<style scoped>
.admin-replies {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
