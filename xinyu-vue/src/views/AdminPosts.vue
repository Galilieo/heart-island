<script setup>
import { ref, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
import { storeToRefs } from 'pinia'
import AdminPageTitle from '../components/admin/AdminPageTitle.vue'
import AdminFilterBar from '../components/admin/AdminFilterBar.vue'
import AdminFilterField from '../components/admin/AdminFilterField.vue'
import AdminDataTable from '../components/admin/AdminDataTable.vue'
import AdminStatus from '../components/admin/AdminStatus.vue'
import AdminRowAction from '../components/admin/AdminRowAction.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import BaseInput from '../components/ui/BaseInput.vue'
import BaseSelect from '../components/ui/BaseSelect.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import { useToast } from '../composables/useToast'
import { useAdminStore } from '../stores/admin'
import { HIDE_REASONS, contentStatusMeta, reasonLabel } from '../utils/communityReport'

const adminStore = useAdminStore()
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
  { label: '已隐藏', value: '2' },
  { label: '作者已删除', value: '0' }
]

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentDetail = ref(null)
const selectedHideReason = ref('ILLEGAL')
const savingStatus = ref(false)

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
    key: 'createTime',
    title: '发布时间',
    width: 140,
    mono: true,
    mobile: 'meta',
    mobileLabel: '',
    format: (row) => formatTime(row.createTime)
  },
  { key: 'actions', title: '操作', width: 92, align: 'right', mobile: 'hidden' }
]

onMounted(() => {
  adminStore.fetchPosts()
})

function formatTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatPendingReasons(reasons) {
  if (!reasons?.length) return ''
  return `（${reasons.map(reasonLabel).join('、')}）`
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

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  selectedHideReason.value = 'ILLEGAL'
  try {
    const res = await adminStore.fetchPostDetail(row.id)
    if (res.ok && res.data) {
      currentDetail.value = res.data
    } else {
      detailVisible.value = false
      toast.error(res.message || '加载帖子详情失败')
    }
  } finally {
    detailLoading.value = false
  }
}

function closeDetail() {
  if (savingStatus.value) return
  detailVisible.value = false
  currentDetail.value = null
}

async function handleStatusAction(nextStatus) {
  if (!currentDetail.value || savingStatus.value) return
  savingStatus.value = true
  try {
    const hideReason = nextStatus === 2 ? selectedHideReason.value : undefined
    const res = await adminStore.updatePostStatus(currentDetail.value.id, nextStatus, hideReason)
    if (res.ok) {
      toast.success(nextStatus === 2 ? '已隐藏帖子' : '已恢复显示')
      detailVisible.value = false
      currentDetail.value = null
    } else {
      toast.error(res.message || '操作失败')
      if (res.status === 409) {
        detailVisible.value = false
        currentDetail.value = null
      }
    }
  } finally {
    savingStatus.value = false
  }
}
</script>

<template>
  <div class="admin-posts">
    <AdminPageTitle
      title="帖子管理"
      desc="查看社区帖子，隐藏或恢复违规内容。作者自己删除的帖子只能查看。"
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
          :text="contentStatusMeta(row.status).text"
          :tone="contentStatusMeta(row.status).tone"
        />
      </template>

      <template #actions="{ row }">
        <AdminRowAction @click="openDetail(row)">
          详情
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

    <BaseDialog
      v-model="detailVisible"
      title="帖子详情"
      width="lg"
      :persistent="savingStatus"
      @close="closeDetail"
    >
      <SkeletonCard v-if="detailLoading" rows="4" />

      <div v-else-if="currentDetail" class="admin-posts__detail">
        <div class="admin-posts__detail-grid">
          <div>
            <span>帖子 ID</span>
            <strong>{{ currentDetail.id }}</strong>
          </div>
          <div>
            <span>用户 ID</span>
            <strong>{{ currentDetail.userId }}</strong>
          </div>
          <div>
            <span>话题</span>
            <strong>{{ currentDetail.topicName || currentDetail.topicId || '-' }}</strong>
          </div>
          <div>
            <span>状态</span>
            <BaseTag :tone="contentStatusMeta(currentDetail.status).tagTone">
              {{ contentStatusMeta(currentDetail.status).text }}
            </BaseTag>
          </div>
          <div>
            <span>发布时间</span>
            <strong>{{ formatTime(currentDetail.createTime) }}</strong>
          </div>
          <div>
            <span>抱抱 / 回复</span>
            <strong>{{ currentDetail.likeCount || 0 }} / {{ currentDetail.replyCount || 0 }}</strong>
          </div>
        </div>

        <section class="admin-posts__detail-section">
          <h3>帖子内容</h3>
          <p>{{ currentDetail.content || '-' }}</p>
        </section>

        <section class="admin-posts__detail-section">
          <h3>小芽回复</h3>
          <p>{{ currentDetail.aiReply || '-' }}</p>
        </section>

        <section class="admin-posts__detail-section">
          <h3>相关举报</h3>
          <p v-if="currentDetail.pendingReportCount > 0">
            {{ currentDetail.pendingReportCount }} 条待处理{{ formatPendingReasons(currentDetail.pendingReasons) }}
            ·
            <RouterLink to="/admin/reports" class="admin-posts__report-link">
              去举报处理
            </RouterLink>
          </p>
          <p v-else>-</p>
        </section>
      </div>

      <template #footer>
        <div v-if="currentDetail" class="admin-posts__footer">
          <div v-if="currentDetail.status === 1" class="admin-posts__footer-row">
            <BaseSelect
              v-model="selectedHideReason"
              label="隐藏原因"
              size="sm"
              :options="HIDE_REASONS"
            />
            <div class="admin-posts__footer-btns">
              <BaseButton variant="plain" :disabled="savingStatus" @click="closeDetail">
                关闭
              </BaseButton>
              <BaseButton
                variant="danger"
                :disabled="savingStatus"
                :loading="savingStatus"
                @click="handleStatusAction(2)"
              >
                隐藏
              </BaseButton>
            </div>
          </div>

          <div v-else-if="currentDetail.status === 2" class="admin-posts__footer-row">
            <span class="admin-posts__hidden-reason">
              隐藏原因：{{ reasonLabel(currentDetail.hideReason) }}（作者看得到）
            </span>
            <div class="admin-posts__footer-btns">
              <BaseButton variant="plain" :disabled="savingStatus" @click="closeDetail">
                关闭
              </BaseButton>
              <BaseButton
                variant="primary"
                :disabled="savingStatus"
                :loading="savingStatus"
                @click="handleStatusAction(1)"
              >
                恢复显示
              </BaseButton>
            </div>
          </div>

          <div v-else class="admin-posts__footer-row">
            <span class="admin-posts__readonly-tip">作者自己删除的帖子只能查看。</span>
            <BaseButton variant="plain" @click="closeDetail">关闭</BaseButton>
          </div>
        </div>
        <BaseButton v-else variant="plain" @click="closeDetail">关闭</BaseButton>
      </template>
    </BaseDialog>
  </div>
</template>

<style scoped>
.admin-posts {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.admin-posts__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-posts__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-posts__detail-grid > div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-soft);
}

.admin-posts__detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.admin-posts__detail-grid strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
  word-break: break-word;
}

.admin-posts__detail-section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

.admin-posts__detail-section h3 {
  margin: 0 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-posts__detail-section p {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-posts__report-link {
  color: var(--brand-deep);
  font-weight: 600;
  text-decoration: none;
}

.admin-posts__footer {
  width: 100%;
}

.admin-posts__footer-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-posts__footer-btns {
  display: flex;
  gap: 10px;
}

.admin-posts__hidden-reason,
.admin-posts__readonly-tip {
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

@media (max-width: 768px) {
  .admin-posts__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
