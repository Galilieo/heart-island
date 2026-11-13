<script setup>
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminFilterBar from '../components/admin/AdminFilterBar.vue'
import AdminFilterField from '../components/admin/AdminFilterField.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import { useConfirm } from '../composables/useConfirm'
import { useToast } from '../composables/useToast'
import { useAdminStore } from '../stores/admin'

const adminStore = useAdminStore()
const { confirm } = useConfirm()
const toast = useToast()

const {
  posts,
  postsLoading,
  postsFilters,
  postPagination,
  postPages
} = storeToRefs(adminStore)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常展示', value: '1' },
  { label: '已隐藏', value: '0' }
]

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
  { key: 'status', title: '状态', width: 80, mobile: 'status' },
  {
    key: 'createTime',
    title: '发布时间',
    width: 140,
    mono: true,
    mobile: 'meta',
    mobileLabel: '',
    format: (row) => formatTime(row.createTime)
  },
  { key: 'actions', title: '操作', width: 120, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchPosts()
})

function statusText(status) {
  return status === 1 ? '正常' : '隐藏'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function handleSearch() {
  adminStore.searchPosts()
}

function handleReset() {
  adminStore.resetPostFilters()
}

function handlePageChange(pageNum) {
  adminStore.changePostPage(pageNum)
}

async function handleToggleStatus(post) {
  const nextStatus = post.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '恢复' : '隐藏'

  const ok = await confirm({
    title: `${actionText}帖子`,
    content: `确定要${actionText}这条帖子吗？`,
    confirmText: actionText,
    cancelText: '取消',
    tone: nextStatus === 0 ? 'danger' : 'default'
  })

  if (!ok) return

  const res = await adminStore.updatePostStatus(post.id, nextStatus)

  if (res.ok) {
    toast.success(`${actionText}成功`)
  } else {
    toast.error(res.message || `${actionText}失败`)
  }
}
</script>

<template>
  <div class="admin-posts">
    <AdminPageTitle
      title="帖子管理"
      desc="查找社区帖子，隐藏或恢复展示。"
    />

    <AdminFilterBar
      :loading="postsLoading"
      @search="handleSearch"
      @reset="handleReset"
    >
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
          :text="statusText(row.status)"
          :tone="row.status === 1 ? 'ok' : 'bad'"
        />
      </template>

      <template #actions="{ row }">
        <AdminRowAction
          :tone="row.status === 1 ? 'danger' : 'default'"
          @click="handleToggleStatus(row)"
        >
          {{ row.status === 1 ? '隐藏' : '恢复' }}
        </AdminRowAction>
      </template>

      <template #loading>
        <SkeletonCard v-for="i in 3" :key="i" rows="3" />
      </template>

      <template #empty>
        <EmptyState
          title="暂无帖子"
          hint="没有找到匹配的帖子，可以调整筛选条件后再查。"
        />
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
  </div>
</template>

<style scoped>
.admin-posts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
