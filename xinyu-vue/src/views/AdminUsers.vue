<script setup>
import { onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import AdminTabs from '../components/admin/AdminTabs.vue'
import AdminPageHeader from '../components/admin/AdminPageHeader.vue'
import AdminFilterActions from '../components/admin/AdminFilterActions.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import BaseTag from '../components/ui/BaseTag.vue'
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
  <AppShell width="wide">
    <div class="container admin-users">
      <AdminPageHeader
        title="用户管理"
        desc="查看用户列表，后续可以在这里启用或禁用账号。"
      />

      <AdminTabs />

      <BaseCard padding="lg">
        <div class="admin-users__toolbar">
          <BaseInput
            v-model="filters.username"
            label="用户名"
            placeholder="输入用户名搜索"
            @enter="handleSearch"
          />
          <BaseSelect
            v-model="filters.status"
            label="状态"
            :options="statusOptions"
            placeholder=""
          />
          <AdminFilterActions
            :loading="loading"
            @search="handleSearch"
            @reset="handleReset"
          />
        </div>

        <div v-if="loading && !users.length" class="admin-users__loading">
          <SkeletonCard v-for="i in 3" :key="i" rows="3" />
        </div>

        <div v-else-if="users.length" class="admin-users__table-wrap">
          <table class="admin-users__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户名</th>
                <th>昵称</th>
                <th>角色</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="user in users" :key="user.id">
                <td>{{ user.id }}</td>
                <td>{{ user.username }}</td>
                <td>{{ user.nickname || '-' }}</td>
                <td>{{ roleText(user.role) }}</td>
                <td>
                  <BaseTag :variant="user.status === 1 ? 'success' : 'danger'">
                    {{ statusText(user.status) }}
                  </BaseTag>
                </td>
                <td>{{ formatTime(user.createTime) }}</td>
                <td>
                  <BaseButton
                    size="sm"
                    :variant="user.status === 1 ? 'danger' : 'soft'"
                    @click="handleToggleStatus(user)"
                  >
                    {{ user.status === 1 ? '禁用' : '启用' }}
                  </BaseButton>
                </td>
              </tr>
            </tbody>
          </table>

          <BasePagination
            class="admin-users__pagination"
            :page-num="pagination.pageNum"
            :pages="pages"
            :total="pagination.total"
            :page-size="pagination.pageSize"
            @change="handlePageChange"
          />
        </div>

        <EmptyState
          v-else
          title="暂无用户"
          hint="当前没有查询到用户数据。"
        />
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
.admin-users {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-users__toolbar {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 180px auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
}

.admin-users__loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-users__table-wrap {
  overflow-x: auto;
}

.admin-users__table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
}

.admin-users__table th,
.admin-users__table td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  font-size: var(--fs-sm);
}

.admin-users__table th {
  color: var(--ink-3);
  font-weight: 700;
  background: var(--bg-soft);
}

.admin-users__table td {
  color: var(--ink-1);
}

.admin-users__pagination {
  margin-top: 18px;
}

@media (max-width: 768px) {
  .admin-users__toolbar {
    grid-template-columns: 1fr;
  }

}
</style>
