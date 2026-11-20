<script setup>
// 帖子详情页。680px 单列居中，与首页主栏同宽：
//   - PostCard（detail 变体，含抱抱/收藏；楼主额外见编辑/删除）
//   - XiaoyaNote（小芽批注 + AI 生成提示小字）
//   - PostReplyForm（发新回复）
//   - PostReplyList（已有回复列表 + 楼主自己回复的 inline 编辑/删除）
//   - PostEditDialog（楼主编辑帖子时打开）
// 路由参数 :id 变化时会自动重新拉详情和回复。
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import PostCard from '../components/community/PostCard.vue'
import CommunitySupportDialog from '../components/community/CommunitySupportDialog.vue'
import XiaoyaNote from '../components/ui/XiaoyaNote.vue'
import PostReplyForm from '../components/community/PostReplyForm.vue'
import PostReplyList from '../components/community/PostReplyList.vue'
import PostEditDialog from '../components/community/PostEditDialog.vue'
import ReportDialog from '../components/community/ReportDialog.vue'
import { useCommunityStore } from '../stores/community'
import { useAsyncAction } from '../composables/useAsyncAction'
import { useToast } from '../composables/useToast'
import { communitySupportText } from '../utils/communityRisk'
import { useConfirm } from '../composables/useConfirm'
import { isSupportReply } from '../utils/mood'

const route = useRoute()
const router = useRouter()
const communityStore = useCommunityStore()
const toast = useToast()
const supportText = ref(null)
const { confirm } = useConfirm()

const { currentPost, replies, topics, reportedTargets } = storeToRefs(communityStore)

const editOpen = ref(false)
const reportOpen = ref(false)
const reportTarget = ref(null)
const reportSubmitting = ref(false)
const reportError = ref('')
const updatingReplyId = ref(null)
const deletingReplyId = ref(null)

const replyFormRef = ref(null)

const { run: doFetch, loading: postLoading } = useAsyncAction(
  () => communityStore.fetchPostDetail(route.params.id),
  { errorMsg: '帖子加载失败' }
)

const { run: doFetchReplies, loading: repliesLoading } = useAsyncAction(
  () => communityStore.fetchReplies(route.params.id),
  { silent: true }
)

const { run: doFetchTopics } = useAsyncAction(() => communityStore.fetchTopics(), { silent: true })

const { run: doSubmitReply, loading: replySubmitting } = useAsyncAction(
  (content) => communityStore.publishReply({ postId: route.params.id, content }),
  { successMsg: '回复成功' }
)

const { run: doEditReply } = useAsyncAction(
  (payload) => communityStore.updateReply(payload, route.params.id),
  { successMsg: '修改成功', errorMsg: '修改失败' }
)

const { run: doUpdatePost, loading: postUpdating } = useAsyncAction(
  (payload) => communityStore.updatePost(payload),
  { successMsg: '帖子已更新' }
)

async function handleSubmitReply(content) {
  const res = await doSubmitReply(content)
  if (res.ok) {
    replyFormRef.value?.reset()
    showReplySupport(res)
    await doFetch()
  }
}

async function handleEditReply({ id, content }, done) {
  if (updatingReplyId.value !== null) return

  updatingReplyId.value = id
  try {
    const res = await doEditReply({ id, content })
    if (res.ok) {
      done?.()
      showReplySupport(res)
    }
  } finally {
    updatingReplyId.value = null
  }
}

function showReplySupport(result) {
  if (result.refreshFailed) {
    toast.warn('回复已保存，列表刷新失败，请刷新查看')
  }
  supportText.value = communitySupportText(result)
}

async function handleDeleteReply(reply) {
  const ok = await confirm({
    title: '删除这条回复？',
    confirmText: '删除',
    tone: 'danger'
  })
  if (!ok) return
  deletingReplyId.value = reply.id
  const res = await communityStore.removeReply(reply.id, route.params.id)
  deletingReplyId.value = null
  if (res.ok) {
    toast.success('已删除')
    await doFetch()
  } else {
    toast.error(res.message || '删除失败')
  }
}

async function handleLike() {
  if (!currentPost.value) return
  const res = await communityStore.toggleLike(currentPost.value)
  if (!res.ok) toast.error(res.message || '操作失败')
}

async function handleFavorite() {
  if (!currentPost.value) return
  const res = await communityStore.toggleFavorite(currentPost.value)
  if (!res.ok) toast.error(res.message || '操作失败')
}

async function handleSavePost(payload) {
  const res = await doUpdatePost(payload)
  if (res.ok) {
    editOpen.value = false
    if (res.refreshFailed) toast.warn('帖子已保存，页面刷新失败，请刷新查看')
    // 额度不足时帖子已保存但没重新生成小芽回复；后端会把提示延长，成功 toast 后补一句。
    if (res.message && res.message !== '修改成功') toast.info(res.message)
    supportText.value = communitySupportText(res, payload.needAiReply)
  }
}

async function handleDeletePost() {
  if (!currentPost.value) return
  const ok = await confirm({
    title: '删除这条帖子？',
    content: '删除后将无法恢复，回复也会一并消失。',
    confirmText: '删除',
    tone: 'danger'
  })
  if (!ok) return
  const res = await communityStore.removePost(currentPost.value.id)
  if (res.ok) {
    toast.success('已删除')
    router.push('/posts')
  } else {
    toast.error(res.message || '删除失败')
  }
}

function openReport(targetType, item) {
  reportError.value = ''
  reportTarget.value = {
    targetType,
    targetId: item.id,
    excerpt: item.content
  }
  reportOpen.value = true
}

async function handleSubmitReport(payload) {
  reportSubmitting.value = true
  reportError.value = ''
  try {
    const res = await communityStore.submitReport(payload)
    if (res.ok) {
      reportOpen.value = false
      toast.success(res.message || '已收到举报，会尽快处理')
    } else {
      reportError.value = res.message || '提交举报失败'
    }
  } finally {
    reportSubmitting.value = false
  }
}

function goBack() {
  router.push('/posts')
}

async function loadPostPage() {
  if (!route.params.id) return
  await Promise.all([
    doFetch(),
    doFetchReplies(),
    communityStore.fetchReportedTargets(route.params.id).catch(() => null)
  ])
}

watch(
  () => route.params.id,
  async () => {
    await loadPostPage()
  }
)

onMounted(async () => {
  await Promise.all([loadPostPage(), doFetchTopics()])
})
</script>

<template>
  <AppShell>
    <div class="container detail">
      <button type="button" class="detail__back" @click="goBack">
        <span aria-hidden="true">←</span> 返回社区
      </button>

      <template v-if="postLoading && !currentPost">
        <SkeletonCard rows="4" />
      </template>

      <template v-else-if="currentPost">
        <PostCard
          :post="currentPost"
          :topics="topics"
          :reported-status="reportedTargets[`POST:${currentPost.id}`] || ''"
          variant="detail"
          @toggle-like="handleLike"
          @toggle-favorite="handleFavorite"
          @edit="editOpen = true"
          @delete="handleDeletePost"
          @report="openReport('POST', $event)"
        />

        <div v-if="currentPost.aiReply" class="detail__note">
          <XiaoyaNote :text="currentPost.aiReply" :support="isSupportReply(currentPost)" />
          <p v-if="!isSupportReply(currentPost)" class="detail__note-hint">
            这条回复由 AI 自动生成，不代表平台立场
          </p>
        </div>

        <p v-if="currentPost.hidden" class="detail__hidden-reply-tip">
          这条帖子已被隐藏，暂时不能继续回复。
        </p>

        <PostReplyForm
          v-else
          ref="replyFormRef"
          :submitting="replySubmitting"
          @submit="handleSubmitReply"
        />

        <PostReplyList
          :replies="replies"
          :loading="repliesLoading"
          :reported-targets="reportedTargets"
          :post-hidden="Boolean(currentPost.hidden)"
          :updating-id="updatingReplyId"
          :deleting-id="deletingReplyId"
          @edit-submit="handleEditReply"
          @delete="handleDeleteReply"
          @report="openReport('REPLY', $event)"
        />
      </template>

      <EmptyState v-else doodle="cloud" title="帖子不存在或已删除">
        <BaseButton variant="primary" @click="goBack">回到社区</BaseButton>
      </EmptyState>

      <PostEditDialog
        v-model="editOpen"
        :post="currentPost"
        :topics="topics"
        :saving="postUpdating"
        @save="handleSavePost"
      />

      <ReportDialog
        v-model="reportOpen"
        :target="reportTarget"
        :submitting="reportSubmitting"
        :server-error="reportError"
        @submit="handleSubmitReport"
      />
    </div>
    <CommunitySupportDialog :text="supportText" @close="supportText = null" />
  </AppShell>
</template>

<style scoped>
/* 与首页主栏同宽的单列；卡片、批注、回复区依次向下排 */
.detail {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 返回入口用文字链接样式，不占卡片视觉 */
.detail__back {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  padding: 0 8px;
  border: none;
  background: transparent;
  color: var(--brand-deep);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease-soft);
}

.detail__back:hover {
  color: var(--ink-1);
}

.detail__note-hint {
  margin: 6px 0 0 16px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.detail__hidden-reply-tip {
  margin: 0;
  padding: 12px 14px;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-2);
  color: var(--ink-3);
  font-size: var(--fs-sm);
  text-align: center;
}
</style>
