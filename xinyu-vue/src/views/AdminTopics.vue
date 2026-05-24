<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
import { useToast } from '../composables/useToast'
import { useConfirm } from '../composables/useConfirm'

const adminStore = useAdminStore()
const toast = useToast()
const { confirm } = useConfirm()

const { topics, topicsLoading, topicFilters } = storeToRefs(adminStore)

const dialogVisible = ref(false)

const form = reactive({
  id: null,
  name: '',
  description: '',
  sort: 0
})

const isEditing = computed(() => !!form.id)

const statusOptions = [
  { label: '全部状态', value: '' },
  { label: '启用', value: '1' },
  { label: '禁用', value: '0' }
]

onMounted(() => {
  adminStore.fetchTopics()
})

function resetForm() {
  form.id = null
  form.name = ''
  form.description = ''
  form.sort = 0
}

function openCreateDialog() {
  resetForm()
  dialogVisible.value = true
}

function closeDialog() {
  dialogVisible.value = false
  resetForm()
}

function editTopic(topic) {
  form.id = topic.id
  form.name = topic.name || ''
  form.description = topic.description || ''
  form.sort = topic.sort || 0
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.name.trim()) {
    toast.error('话题名称不能为空')
    return
  }

  const data = {
    name: form.name.trim(),
    description: form.description.trim(),
    sort: Number(form.sort) || 0
  }

  const res = isEditing.value
    ? await adminStore.updateTopic(form.id, data)
    : await adminStore.addTopic(data)

  if (res.ok) {
    toast.success(isEditing.value ? '修改成功' : '新增成功')
    closeDialog()
  } else {
    toast.error(res.message || '操作失败')
  }
}

async function handleToggleStatus(topic) {
  const nextStatus = topic.status === 1 ? 0 : 1
  const actionText = nextStatus === 1 ? '启用' : '禁用'

  const ok = await confirm({
    title: `${actionText}话题`,
    content:
      nextStatus === 0 && (topic.postCount || 0) > 0
        ? `这个话题下已有 ${topic.postCount} 条帖子，禁用后用户不能再选择它发帖，确定要禁用吗？`
        : `确定要${actionText}这个话题吗？`,
    confirmText: actionText,
    cancelText: '取消',
    tone: nextStatus === 0 ? 'danger' : 'default'
  })

  if (!ok) return

  const res = await adminStore.updateTopicStatus(topic.id, nextStatus)

  if (res.ok) {
    toast.success(`${actionText}成功`)
  } else {
    toast.error(res.message || `${actionText}失败`)
  }
}

function handleSearch() {
  adminStore.searchTopics()
}

function handleReset() {
  adminStore.resetTopicFilters()
}

function statusText(status) {
  return status === 1 ? '启用' : '禁用'
}

function statusVariant(status) {
  return status === 1 ? 'success' : 'danger'
}

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <AppShell width="wide">
    <div class="container admin-topics">
      <AdminPageHeader
        title="话题管理"
        desc="维护社区话题分类，控制用户发布帖子时可选择的内容方向。"
      />

      <AdminTabs />

      <BaseCard padding="lg">
        <div class="admin-topics__toolbar">
          <BaseInput
            v-model="topicFilters.keyword"
            label="话题名称"
            placeholder="输入话题名称搜索"
            @enter="handleSearch"
          />

          <BaseSelect
            v-model="topicFilters.status"
            label="状态"
            :options="statusOptions"
            placeholder=""
          />

          <AdminFilterActions
            :loading="topicsLoading"
            @search="handleSearch"
            @reset="handleReset"
          >
            <BaseButton variant="soft" :disabled="topicsLoading" @click="openCreateDialog">
              新增话题
            </BaseButton>
          </AdminFilterActions>
        </div>

        <div v-if="topicsLoading && !topics.length" class="admin-topics__loading">
          <SkeletonCard v-for="i in 3" :key="i" rows="3" />
        </div>

        <div v-else-if="topics.length" class="admin-topics__table-wrap">
          <table class="admin-topics__table">
            <thead>
              <tr>
                <th>ID</th>
                <th>话题名称</th>
                <th>话题说明</th>
                <th>排序</th>
                <th>帖子数</th>
                <th>状态</th>
                <th>创建时间</th>
                <th>操作</th>
              </tr>
            </thead>

            <tbody>
              <tr v-for="topic in topics" :key="topic.id">
                <td>{{ topic.id }}</td>
                <td>{{ topic.name }}</td>
                <td>{{ topic.description || '-' }}</td>
                <td class="admin-topics__sort">{{ topic.sort }}</td>
                <td>{{ topic.postCount ?? 0 }}</td>
                <td>
                  <BaseTag :variant="statusVariant(topic.status)">
                    {{ statusText(topic.status) }}
                  </BaseTag>
                </td>
                <td>{{ formatTime(topic.createTime) }}</td>
                <td>
                  <div class="admin-topics__row-actions">
                    <BaseButton size="sm" variant="soft" @click="editTopic(topic)">
                      编辑
                    </BaseButton>
                    <BaseButton
                      size="sm"
                      :variant="topic.status === 1 ? 'danger' : 'soft'"
                      @click="handleToggleStatus(topic)"
                    >
                      {{ topic.status === 1 ? '禁用' : '启用' }}
                    </BaseButton>
                  </div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <EmptyState
          v-else
          title="暂无话题"
          hint="当前没有查询到社区话题。"
        />
      </BaseCard>

      <BaseDialog
        v-model="dialogVisible"
        :title="isEditing ? '编辑话题' : '新增话题'"
        width="md"
        @close="resetForm"
      >
        <div class="admin-topics__dialog-form">
          <BaseInput
            v-model="form.name"
            label="话题名称"
            placeholder="例如：学习压力"
          />

          <BaseInput
            v-model="form.description"
            label="话题说明"
            placeholder="简单说明这个话题适合讨论什么"
          />

          <BaseInput
            v-model="form.sort"
            type="number"
            label="排序"
            placeholder="例如：0，数字越小越靠前"
          />
        </div>

        <template #footer>
          <BaseButton variant="plain" @click="closeDialog">
            取消
          </BaseButton>
          <BaseButton variant="primary" @click="handleSubmit">
            {{ isEditing ? '保存修改' : '新增话题' }}
          </BaseButton>
        </template>
      </BaseDialog>
    </div>
  </AppShell>
</template>

<style scoped>
.admin-topics {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.admin-topics__toolbar {
  display: grid;
  grid-template-columns: minmax(260px, 1fr) 180px auto;
  gap: 12px;
  align-items: end;
  margin-bottom: 18px;
}

.admin-topics__row-actions {
  display: flex;
  gap: 10px;
}

.admin-topics__dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-topics__loading {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-topics__table-wrap {
  overflow-x: auto;
}

.admin-topics__table {
  width: 100%;
  min-width: 900px;
  border-collapse: collapse;
}

.admin-topics__table th,
.admin-topics__table td {
  padding: 14px 12px;
  border-bottom: 1px solid var(--line);
  text-align: left;
  font-size: var(--fs-sm);
}

.admin-topics__table th {
  color: var(--ink-3);
  font-weight: 700;
  background: var(--bg-soft);
}

.admin-topics__table td {
  color: var(--ink-1);
}

.admin-topics__sort {
  color: var(--brand-deep);
  font-weight: 700;
}

@media (min-width: 769px) and (max-width: 1100px) {
  .admin-topics__toolbar {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  :deep(.admin-filter-actions) {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .admin-topics__toolbar {
    grid-template-columns: 1fr;
  }

  :deep(.admin-filter-actions) {
    width: 100%;
  }
}
</style>
