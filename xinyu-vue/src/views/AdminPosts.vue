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
import { isSupportReply } from '../utils/mood'
import { contentStatusMeta, formatFullDateTime } from '../utils/communityReport'

const adminStore = useAdminStore()

const { posts, postsLoading, postsFilters, postPagination, postPages } = storeToRefs(adminStore)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常展示', value: '1' },
  { label: '已隐藏', value: '2' },
  { label: '作者已删除', value: '0' }
]

const resultKindOptions = [
  { label: '全部类型', value: '' },
  { label: '危机固定回应', value: 'SUPPORT' },
  { label: '模型生成', value: 'MODEL' }
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
  fetchDetail: (id) => adminStore.fetchPostDetail(id),
  updateStatus: (id, status, hideReason) => adminStore.updatePostStatus(id, status, hideReason),
  loadErrorText: '加载帖子详情失败',
  hideSuccessText: '已隐藏帖子'
})

const detailGridItems = computed(() => {
  const detail = currentDetail.value
  if (!detail) {
    return []
  }
  return [
    { label: '帖子 ID', value: detail.id },
    { label: '用户 ID', value: detail.userId },
    { label: '话题', value: detail.topicName || detail.topicId || '-' },
    { label: '状态', isStatus: true },
    { label: '发布时间', value: formatFullDateTime(detail.createTime) },
    { label: '抱抱 / 回复', value: `${detail.likeCount || 0} / ${detail.replyCount || 0}` }
  ]
})

const detailSections = computed(() => {
  const detail = currentDetail.value
  if (!detail) {
    return []
  }
  return [
    { title: '帖子内容', content: detail.content },
    { title: isSupportReply(detail) ? '危机固定回应' : '小芽回复', content: detail.aiReply }
  ]
})

// 表头「抱抱」为第 3 步确认后的统一文案；内容列自动伸缩并在手机上显示两行。
const columns = [
  { key: 'id', title: 'ID', width: 64, mono: true, mobile: 'eyebrow', mobilePrefix: '#' },
  { key: 'userId', title: '用户 ID', width: 96, mono: true, mobile: 'eyebrow' },
  {
    key: 'moodType',
    title: '情绪',
    width: 76,
    mobile: 'eyebrow',
    format: (row) => row.moodType || '-'
  },
  {
    key: 'content',
    title: '帖子内容',
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
  {
    key: 'replyCount',
    title: '回复',
    width: 64,
    align: 'right',
    mobile: 'meta',
    mobileLabel: '回复',
    format: (row) => row.replyCount || 0
  },
  {
    key: 'likeCount',
    title: '抱抱',
    width: 64,
    align: 'right',
    mobile: 'meta',
    mobileLabel: '抱抱',
    format: (row) => row.likeCount || 0
  },
  { key: 'status', title: '状态', width: 96, mobile: 'status' },
  {
    key: 'resultKind',
    title: 'AI 回复',
    width: 112,
    mobile: 'meta',
    format: (row) => (isSupportReply(row) ? '危机固定回应' : '—')
  },
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
  adminStore.fetchPosts()
})

function handleSearch() {
  adminStore.searchPosts()
}

function handleReset() {
  adminStore.resetPostFilters()
}

function handlePageChange(pageNum) {
  adminStore.changePostPage(pageNum)
}
</script>

<template>
  <div class="admin-posts">
    <AdminPageTitle
      title="帖子管理"
      desc="查看社区帖子，隐藏或恢复违规内容。作者自己删除的帖子只能查看。"
    />

    <AdminFilterBar :loading="postsLoading" @search="handleSearch" @reset="handleReset">
      <AdminFilterField size="wide" full>
        <BaseInput
          v-model="postsFilters.keyword"
          label="内容"
          placeholder="输入关键词"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="md">
        <BaseInput
          v-model="postsFilters.postId"
          label="帖子 ID"
          placeholder="全部"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="md">
        <BaseInput
          v-model="postsFilters.userId"
          label="用户 ID"
          placeholder="全部"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="sm" full>
        <BaseSelect
          v-model="postsFilters.status"
          label="状态"
          :options="statusOptions"
          placeholder=""
        />
      </AdminFilterField>
      <AdminFilterField size="md" full>
        <BaseSelect
          v-model="postsFilters.resultKind"
          label="回复类型"
          :options="resultKindOptions"
          placeholder=""
        />
      </AdminFilterField>
    </AdminFilterBar>

    <AdminDataTable
      :columns="columns"
      :rows="posts"
      :total="Number(postPagination.total)"
      :loading="postsLoading"
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
        <AdminRowAction @click="openDetail(row)"> 详情 </AdminRowAction>
      </template>

      <template #loading>
        <SkeletonCard v-for="i in 3" :key="i" rows="3" />
      </template>

      <template #empty>
        <EmptyState title="暂无帖子" hint="没有找到匹配的帖子，可以调整筛选条件后再查。" />
      </template>

      <template #pagination>
        <BasePagination
          :page-num="Number(postPagination.pageNum)"
          :pages="Number(postPages)"
          :total="Number(postPagination.total)"
          :page-size="Number(postPagination.pageSize)"
          @change="handlePageChange"
        />
      </template>
    </AdminDataTable>

    <AdminContentDetailDialog
      v-model="detailVisible"
      v-model:selected-hide-reason="selectedHideReason"
      title="帖子详情"
      :loading="detailLoading"
      :detail="currentDetail"
      :grid-items="detailGridItems"
      :sections="detailSections"
      :saving-status="savingStatus"
      readonly-tip="作者自己删除的帖子只能查看。"
      @close="closeDetail"
      @status-action="handleStatusAction"
    />
  </div>
</template>

<style scoped>
.admin-posts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
