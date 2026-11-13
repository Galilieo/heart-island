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

const { users, loading, filters, pagination, pages } = storeToRefs(adminStore)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '正常', value: '1' },
  { label: '禁用', value: '0' }
]

// 固定列宽合计 814px，小于 1280 宽度下的内容区，不需要横向滚动。
const columns = [
  { key: 'id', title: 'ID', width: 64, mono: true, mobile: 'eyebrow', mobilePrefix: '#' },
  { key: 'username', title: '用户名', width: 160, mobile: 'eyebrow' },
  {
    key: 'nickname',
    title: '昵称',
    width: 160,
    mobile: 'eyebrow',
    format: (row) => row.nickname || '-'
  },
  {
    key: 'role',
    title: '角色',
    width: 110,
    mobile: 'meta',
    mobileLabel: '角色',
    format: (row) => roleText(row.role)
  },
  { key: 'status', title: '状态', width: 80, mobile: 'status' },
  {
    key: 'createTime',
    title: '创建时间',
    width: 140,
    mono: true,
    mobile: 'meta',
    mobileLabel: '',
    format: (row) => formatTime(row.createTime)
  },
  { key: 'actions', title: '操作', width: 100, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchUsers()
})

function roleText(role) {
  return role === 'ADMIN' ? '管理员' : '普通用户'
}

function statusText(status) {
  return status === 1 ? '正常' : '禁用'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function handleSearch() {
  adminStore.searchUsers()
}

function handleReset() {
  adminStore.resetFilters()
}

function handlePageChange(pageNum) {
  adminStore.changePage(pageNum)
}

async function handleToggleStatus(user) {
  const nextStatus = user.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'

  const confirmed = await confirm({
    title: `${actionText}用户`,
    content: `确定要${actionText}用户“${user.username}”吗？`,
    confirmText: actionText,
    cancelText: '取消',
    tone: nextStatus === 0 ? 'danger' : 'default'
  })

  if (!confirmed) return

  const res = await adminStore.updateUserStatus(user.id, nextStatus)

  if (res.ok) {
    toast.success(`${actionText}成功`)
  } else {
    toast.error(res.message || `${actionText}失败`)
  }
}
</script>

<template>
  <div class="admin-users">
    <AdminPageTitle
      title="用户管理"
      desc="查找用户，启用或禁用账号。"
    />

    <AdminFilterBar
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
    >
      <AdminFilterField size="wide" full>
        <BaseInput
          v-model="filters.username"
          label="用户名"
          placeholder="输入用户名搜索"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="sm" full>
        <BaseSelect
          v-model="filters.status"
          label="状态"
          :options="statusOptions"
          placeholder=""
        />
      </AdminFilterField>
    </AdminFilterBar>

    <AdminDataTable
      :columns="columns"
      :rows="users"
      :total="Number(pagination.total)"
      :loading="loading"
      row-key="id"
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
          {{ row.status === 1 ? '禁用' : '启用' }}
        </AdminRowAction>
      </template>

      <template #loading>
        <SkeletonCard v-for="i in 3" :key="i" rows="3" />
      </template>

      <template #empty>
        <EmptyState
          title="暂无用户"
          hint="没有找到匹配的用户，可以调整筛选条件后再查。"
        />
      </template>

      <template #pagination>
        <BasePagination
          :page-num="Number(pagination.pageNum)"
          :pages="Number(pages)"
          :total="Number(pagination.total)"
          :page-size="Number(pagination.pageSize)"
          @change="handlePageChange"
        />
      </template>
    </AdminDataTable>
  </div>
</template>

<style scoped>
.admin-users {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
