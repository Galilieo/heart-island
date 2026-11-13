<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminFilterBar from '../components/admin/AdminFilterBar.vue'
import AdminFilterField from '../components/admin/AdminFilterField.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import AdminButton from '../components/admin/AdminButton.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseButton from '../components/ui/BaseButton.vue'
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

// 固定列宽合计 690px（说明列自动伸缩），小于 1280 宽度下的内容区，不需要横向滚动。
const columns = [
  { key: 'id', title: 'ID', width: 64, mono: true, mobile: 'eyebrow', mobilePrefix: '#' },
  { key: 'name', title: '话题名称', width: 150, mobile: 'eyebrow' },
  {
    key: 'description',
    title: '话题说明',
    mobile: 'body',
    format: (row) => row.description || '-',
    tooltip: (row) => row.description || ''
  },
  { key: 'sort', title: '排序', width: 64, align: 'right', mobile: 'meta', mobileLabel: '排序' },
  {
    key: 'postCount',
    title: '帖子数',
    width: 72,
    align: 'right',
    mobile: 'meta',
    mobileLabel: '帖子',
    format: (row) => row.postCount ?? 0
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
  { key: 'actions', title: '操作', width: 120, align: 'right', mobile: 'hidden' }
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

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
</script>

<template>
  <div class="admin-topics">
    <AdminPageTitle
      title="话题管理"
      desc="新增或编辑话题，设置发帖时可选的话题和排列顺序。"
    >
      <template #actions>
        <AdminButton
          variant="primary"
          :disabled="topicsLoading"
          @click="openCreateDialog"
        >
          新增话题
        </AdminButton>
      </template>
    </AdminPageTitle>

    <AdminFilterBar
      :loading="topicsLoading"
      @search="handleSearch"
      @reset="handleReset"
    >
      <AdminFilterField size="wide" full>
        <BaseInput
          v-model="topicFilters.keyword"
          label="话题名称"
          placeholder="输入话题名称搜索"
          @enter="handleSearch"
        />
      </AdminFilterField>

      <AdminFilterField size="sm" full>
        <BaseSelect
          v-model="topicFilters.status"
          label="状态"
          :options="statusOptions"
          placeholder=""
        />
      </AdminFilterField>
    </AdminFilterBar>

    <AdminDataTable
      :columns="columns"
      :rows="topics"
      :total="Number(topics.length)"
      :loading="topicsLoading"
      row-key="id"
    >
      <template #cell-status="{ row }">
        <AdminStatus
          :text="statusText(row.status)"
          :tone="row.status === 1 ? 'ok' : 'bad'"
        />
      </template>

      <template #actions="{ row }">
        <AdminRowAction @click="editTopic(row)">编辑</AdminRowAction>
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
          title="暂无话题"
          hint="没有找到匹配的话题，可以调整筛选条件，或新增一个话题。"
        />
      </template>
    </AdminDataTable>

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
</template>

<style scoped>
.admin-topics {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-topics__dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}
</style>
