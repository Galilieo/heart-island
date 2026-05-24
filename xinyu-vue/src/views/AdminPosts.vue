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

onMounted(() => {
  adminStore.fetchPosts()
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
  return content.length > 40 ? `${content.slice(0, 40)}...` : content
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
  <AppShell width="wide">
    <div class="container admin-posts">
      <AdminPageHeader
        title="帖子管理"
        desc="查看社区帖子，后续可以在这里隐藏或恢复内容。"
      />

      <AdminTabs />

      <BaseCard padding="lg">
        <div class="admin-posts__toolbar">
          <BaseInput
            v-model="postsFilters.keyword"
            label="帖子内容"
            placeholder="输入关键词搜索帖子内容"
            @enter="handleSearch"
          />

          <BaseInput
            v-model="postsFilters.postId"
            label="帖子 ID"
            placeholder="按帖子 ID 筛选"
            @enter="handleSearch"
          />

          <BaseInput
            v-model="postsFilters.userId"
            label="用户 ID"
            placeholder="按用户 ID 筛选"
            @enter="handleSearch"
          />

          <BaseSelect
            v-model="postsFilters.status"
            label="状态"
            :options="statusOptions"
            placeholder=""
          />

          <AdminFilterActions
            :loading="postsLoading"
            @search="handleSearch"
            @reset="handleReset"
          />
        </div>

        <div v-if="postsLoading && !posts.length" class="admin-posts__loading">
          <SkeletonCard v-for="i in 3" :key="i" rows="3" />
        </div>

        <div v-else-if="posts.length" class="admin-posts__table-wrap">
          <table class="admin-posts__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户 ID</th>
                <th>帖子情绪</th>
                <th>帖子内容</th>
                <th>匿名名</th>
                <th>回复数</th>
                <th>点赞数</th>
                <th>状态</th>
                <th>发布时间</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="post in posts" :key="post.id">
                <td>{{ post.id }}</td>
                <td>{{ post.userId }}</td>
                <td>{{ post.moodType || '-' }}</td>
                <td>{{ shortContent(post.content) }}</td>
                <td>{{ post.anonymousName || '-' }}</td>
                <td>{{ post.replyCount || 0 }}</td>
                <td>{{ post.likeCount || 0 }}</td>
                <td>
                  <BaseTag :variant="statusVariant(post.status)">
                    {{ statusText(post.status) }}
                  </BaseTag>
                </td>
                <td>{{ formatTime(post.createTime) }}</td>
                <td>
                  <BaseButton
                    size="sm"
                    :variant="post.status === 1 ? 'danger' : 'soft'"
                    @click="handleToggleStatus(post)"
                  >
                    {{ post.status === 1 ? '隐藏' : '恢复' }}
                  </BaseButton>
                </td>
              </tr>
            </tbody>
          </table>

          <BasePagination
            class="admin-posts__pagination"
            :page-num="postPagination.pageNum"
            :pages="postPages"
            :total="postPagination.total"
            :page-size="postPagination.pageSize"
            @change="handlePageChange"
          />
        </div>

        <EmptyState
          v-else
          title="暂无帖子"
          hint="当前没有查询到社区帖子。"
        />
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
.admin-posts {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-posts__toolbar {
  display: grid;
  grid-template-columns: minmax(240px, 1.6fr) minmax(150px, 0.8fr) minmax(170px, 0.9fr) 180px auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
}

.admin-posts__loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-posts__table-wrap {
  overflow-x: auto;
}

.admin-posts__table {
  width: 100%;
  min-width: 1040px;
  border-collapse: collapse;
}

.admin-posts__table th,
.admin-posts__table td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  font-size: var(--fs-sm);
}

.admin-posts__table th {
  color: var(--ink-3);
  font-weight: 700;
  background: var(--bg-soft);
}

.admin-posts__table td {
  color: var(--ink-1);
}

.admin-posts__pagination {
  margin-top: 18px;
}

@media (max-width: 768px) {
  .admin-posts__toolbar {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1100px) {
  .admin-posts__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.admin-filter-actions) {
    grid-column: 1 / -1;
  }
}
</style>
