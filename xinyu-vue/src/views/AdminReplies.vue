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

onMounted(() => {
  adminStore.fetchReplies()
})

function statusText(status) {
  return status === 1 ? '正常展示' : '已隐藏'
}

function statusVariant(status) {
  return status === 1 ? 'success' : 'danger'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function shortContent(content) {
  if (!content) return '-'
  return content.length > 48 ? `${content.slice(0, 48)}...` : content
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
  <AppShell width="wide">
    <div class="container admin-replies">
      <AdminPageHeader
        title="回复管理"
        desc="查看社区回复，隐藏不合适的互动内容。"
      />

      <AdminTabs />

      <BaseCard padding="lg">
        <div class="admin-replies__toolbar">
          <BaseInput
            v-model="replyFilters.keyword"
            label="回复内容"
            placeholder="输入关键词搜索回复内容"
            @enter="handleSearch"
          />

          <BaseInput
            v-model="replyFilters.postId"
            label="帖子 ID"
            placeholder="按帖子 ID 筛选"
            @enter="handleSearch"
          />

          <BaseInput
            v-model="replyFilters.userId"
            label="用户 ID"
            placeholder="按用户 ID 筛选"
            @enter="handleSearch"
          />

          <BaseSelect
            v-model="replyFilters.status"
            label="状态"
            :options="statusOptions"
            placeholder=""
          />

          <AdminFilterActions
            :loading="repliesLoading"
            @search="handleSearch"
            @reset="handleReset"
          />
        </div>

        <div v-if="repliesLoading && !replies.length" class="admin-replies__loading">
          <SkeletonCard v-for="i in 3" :key="i" rows="3" />
        </div>

        <div v-else-if="replies.length" class="admin-replies__table-wrap">
          <table class="admin-replies__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>帖子 ID</th>
                <th>用户 ID</th>
                <th>回复内容</th>
                <th>匿名名</th>
                <th>状态</th>
                <th>发布时间</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="reply in replies" :key="reply.id">
                <td>{{ reply.id }}</td>
                <td>{{ reply.postId }}</td>
                <td>{{ reply.userId }}</td>
                <td>{{ shortContent(reply.content) }}</td>
                <td>{{ reply.anonymousName || '-' }}</td>
                <td>
                  <BaseTag :variant="statusVariant(reply.status)">
                    {{ statusText(reply.status) }}
                  </BaseTag>
                </td>
                <td>{{ formatTime(reply.createTime) }}</td>
                <td>
                  <BaseButton
                    size="sm"
                    :variant="reply.status === 1 ? 'danger' : 'soft'"
                    @click="handleToggleStatus(reply)"
                  >
                    {{ reply.status === 1 ? '隐藏' : '恢复' }}
                  </BaseButton>
                </td>
              </tr>
            </tbody>
          </table>

          <BasePagination
            class="admin-replies__pagination"
            :page-num="replyPagination.pageNum"
            :pages="replyPages"
            :total="replyPagination.total"
            :page-size="replyPagination.pageSize"
            @change="handlePageChange"
          />
        </div>

        <EmptyState
          v-else
          title="暂无回复"
          hint="当前没有查询到社区回复。"
        />
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
.admin-replies {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-replies__toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1.5fr) minmax(150px, 0.8fr) minmax(170px, 0.9fr) 180px auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
}

.admin-replies__loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-replies__table-wrap {
  overflow-x: auto;
}

.admin-replies__table {
  width: 100%;
  min-width: 1040px;
  border-collapse: collapse;
}

.admin-replies__table th,
.admin-replies__table td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  font-size: var(--fs-sm);
}

.admin-replies__table th {
  color: var(--ink-3);
  font-weight: 700;
  background: var(--bg-soft);
}

.admin-replies__table td {
  color: var(--ink-1);
}

.admin-replies__pagination {
  margin-top: 18px;
}

@media (max-width: 768px) {
  .admin-replies__toolbar {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1100px) {
  .admin-replies__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.admin-filter-actions) {
    grid-column: 1 / -1;
  }
}
</style>
