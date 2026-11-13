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
  replies,
  repliesLoading,
  replyFilters,
  replyPagination,
  replyPages
} = storeToRefs(adminStore)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常展示', value: '1' },
  { label: '已隐藏', value: '0' }
]

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
  { key: 'actions', title: '操作', width: 100, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchReplies()
})

function statusText(status) {
  return status === 1 ? '正常' : '隐藏'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function handleSearch() {
  adminStore.searchReplies()
}

function handleReset() {
  adminStore.resetReplyFilters()
}

function handlePageChange(pageNum) {
  adminStore.changeReplyPage(pageNum)
}

async function handleToggleStatus(reply) {
  const nextStatus = reply.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '恢复' : '隐藏'

  const ok = await confirm({
    title: `${actionText}回复`,
    content: `确定要${actionText}这条回复吗？`,
    confirmText: actionText,
    cancelText: '取消',
    tone: nextStatus === 0 ? 'danger' : 'default'
  })

  if (!ok) return

  const res = await adminStore.updateReplyStatus(reply.id, nextStatus)

  if (res.ok) {
    toast.success(`${actionText}成功`)
  } else {
    toast.error(res.message || `${actionText}失败`)
  }
}
</script>

<template>
  <div class="admin-replies">
    <AdminPageTitle
      title="回复管理"
      desc="查找帖子回复，隐藏或恢复展示。"
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
  </div>
</template>

<style scoped>
.admin-replies {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
