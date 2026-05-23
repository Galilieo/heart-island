<script setup>
import { onMounted, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import AdminTabs from '../components/admin/AdminTabs.vue'
import AdminPageHeader from '../components/admin/AdminPageHeader.vue'
import AdminFilterActions from '../components/admin/AdminFilterActions.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
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

onMounted(() => {
  adminStore.fetchAiReplies()
})

function statusText(status) {
  return status === 1 ? '成功' : '失败'
}

function statusVariant(status) {
  return status === 1 ? 'success' : 'danger'
}

function targetTypeText(targetType) {
  const map = {
    MOOD_RECORD: '心情记录',
    POST: '帖子',
    SUMMARY: '总结'
  }

  return map[targetType] || targetType || '-'
}

function shortText(value, length = 40) {
  if (!value) return '-'

  const text = String(value)

  if (text.length <= length) {
    return text
  }

  return `${text.slice(0, length)}...`
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
  <AppShell width="wide">
    <div class="container admin-ai-replies">
      <AdminPageHeader
        title="AI 回复记录"
        desc="查看 AI 调用日志，排查回复生成状态、失败原因和关联业务记录。"
      />

      <AdminTabs />

      <BaseCard padding="lg">
        <div class="admin-ai-replies__toolbar">
          <BaseInput
            v-model="aiReplyFilters.userId"
            label="用户ID"
            placeholder="输入用户ID"
            @enter="handleSearch"
          />

          <BaseSelect
            v-model="aiReplyFilters.targetType"
            label="关联类型"
            :options="targetTypeOptions"
            placeholder=""
          />

          <BaseSelect
            v-model="aiReplyFilters.status"
            label="状态"
            :options="statusOptions"
            placeholder=""
          />

          <BaseInput
            v-model="aiReplyFilters.keyword"
            label="关键词"
            placeholder="搜索回复、错误或提示词"
            @enter="handleSearch"
          />

          <AdminFilterActions
            :loading="aiRepliesLoading"
            @search="handleSearch"
            @reset="handleReset"
          />
        </div>

        <div v-if="aiRepliesLoading && !aiReplies.length" class="admin-ai-replies__loading">
          <SkeletonCard v-for="i in 3" :key="i" rows="3" />
        </div>

        <div v-else-if="aiReplies.length" class="admin-ai-replies__table-wrap">
          <table class="admin-ai-replies__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>用户ID</th>
                <th>关联类型</th>
                <th>关联ID</th>
                <th>模型</th>
                <th>状态</th>
                <th>AI回复</th>
                <th>错误信息</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="item in aiReplies" :key="item.id">
                <td>{{ item.id }}</td>
                <td>{{ item.userId }}</td>
                <td>{{ targetTypeText(item.targetType) }}</td>
                <td>{{ item.targetId }}</td>
                <td>{{ item.modelName || '-' }}</td>
                <td>
                  <BaseTag :variant="statusVariant(item.status)">
                    {{ statusText(item.status) }}
                  </BaseTag>
                </td>
                <td :title="item.replyContent">{{ shortText(item.replyContent, 36) }}</td>
                <td :title="item.errorMessage">{{ shortText(item.errorMessage, 28) }}</td>
                <td>{{ formatTime(item.createTime) }}</td>
                <td>
                  <BaseButton size="sm" variant="soft" @click="openDetail(item)">
                    详情
                  </BaseButton>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <EmptyState
          v-else
          title="暂无 AI 回复记录"
          hint="当前没有查询到 AI 调用日志。"
        />

        <div v-if="aiRepliesTotal > 0" class="admin-ai-replies__pager">
          <span>共 {{ aiRepliesTotal }} 条</span>
          <BaseButton
            variant="plain"
            size="sm"
            :disabled="aiReplyFilters.pageNum <= 1 || aiRepliesLoading"
            @click="handlePageChange(aiReplyFilters.pageNum - 1)"
          >
            上一页
          </BaseButton>
          <span>{{ aiReplyFilters.pageNum }} / {{ aiReplyPages }}</span>
          <BaseButton
            variant="plain"
            size="sm"
            :disabled="aiReplyFilters.pageNum >= aiReplyPages || aiRepliesLoading"
            @click="handlePageChange(aiReplyFilters.pageNum + 1)"
          >
            下一页
          </BaseButton>
        </div>
      </BaseCard>

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
              <BaseTag :variant="statusVariant(currentRecord.status)">
                {{ statusText(currentRecord.status) }}
              </BaseTag>
            </div>
            <div>
              <span>创建时间</span>
              <strong>{{ formatTime(currentRecord.createTime) }}</strong>
            </div>
          </div>

          <section class="admin-ai-replies__detail-section">
            <h3>Prompt</h3>
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
  </AppShell>
</template>

<style scoped>
.admin-ai-replies {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-ai-replies__toolbar {
  display: grid;
  grid-template-columns: 160px 180px 160px minmax(260px, 1fr) auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
}

.admin-ai-replies__loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-ai-replies__table-wrap {
  overflow-x: auto;
}

.admin-ai-replies__table {
  width: 100%;
  min-width: 1180px;
  border-collapse: collapse;
}

.admin-ai-replies__table th,
.admin-ai-replies__table td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  font-size: var(--fs-sm);
  vertical-align: top;
}

.admin-ai-replies__table th {
  color: var(--ink-3);
  font-weight: 700;
  background: var(--bg-soft);
}

.admin-ai-replies__table td {
  color: var(--ink-1);
}

.admin-ai-replies__pager {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 18px;
  color: var(--ink-3);
  font-size: var(--fs-sm);
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
  color: var(--ink-3);
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

@media (min-width: 641px) and (max-width: 1100px) {
  .admin-ai-replies__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.admin-filter-actions) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 640px) {
  .admin-ai-replies__toolbar {
    grid-template-columns: 1fr;
  }

  :deep(.admin-filter-actions) {
    width: 100%;
  }

  .admin-ai-replies__pager {
    flex-wrap: wrap;
  }

  .admin-ai-replies__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
