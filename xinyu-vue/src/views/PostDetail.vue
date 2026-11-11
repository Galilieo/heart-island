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
import XiaoyaNote from '../components/ui/XiaoyaNote.vue'
import PostReplyForm from '../components/community/PostReplyForm.vue'
import PostReplyList from '../components/community/PostReplyList.vue'
import PostEditDialog from '../components/community/PostEditDialog.vue'
import { useCommunityStore } from '../stores/community'
import { useUserStore } from '../stores/user'
import { useAsyncAction } from '../composables/useAsyncAction'
import { useToast } from '../composables/useToast'
import { useConfirm } from '../composables/useConfirm'

const route = useRoute()
const router = useRouter()
const communityStore = useCommunityStore()
const userStore = useUserStore()
const toast = useToast()
const { confirm } = useConfirm()

const { currentPost, replies, topics } = storeToRefs(communityStore)

const editOpen = ref(false)
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

const { run: doFetchTopics } = useAsyncAction(
  () => communityStore.fetchTopics(),
  { silent: true }
)

const { run: doSubmitReply, loading: replySubmitting } = useAsyncAction(
  (content) => communityStore.publishReply({ postId: Number(route.params.id), content }),
  { successMsg: '回复成功' }
)

const { run: doUpdatePost, loading: postUpdating } = useAsyncAction(
  (payload) => communityStore.updatePost(payload),
  { successMsg: '帖子已更新' }
)

async function handleSubmitReply(content) {
  const res = await doSubmitReply(content)
  if (res.ok) {
    replyFormRef.value?.reset()
    await doFetch()
  }
}

async function handleEditReply({ id, content }, done) {
  updatingReplyId.value = id
  const res = await communityStore.updateReply({ id, content }, route.params.id)
  updatingReplyId.value = null
  if (res.ok) {
    toast.success('修改成功')
    done?.()
  } else {
    toast.error(res.message || '修改失败')
  }
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

function goBack() {
  router.push('/posts')
}

watch(
  () => route.params.id,
  async () => {
    if (route.params.id) {
      await Promise.all([doFetch(), doFetchReplies()])
    }
  }
)

onMounted(async () => {
  await Promise.all([doFetch(), doFetchReplies(), doFetchTopics()])
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
          :login-user-id="userStore.loginUser?.id"
          variant="detail"
          @toggle-like="handleLike"
          @toggle-favorite="handleFavorite"
          @edit="editOpen = true"
          @delete="handleDeletePost"
        />

        <!-- AI 批注统一用小芽「页边手写」样式；提示语来自原 PostAiReply，不能丢 -->
        <div v-if="currentPost.aiReply" class="detail__note">
          <XiaoyaNote :text="currentPost.aiReply" />
          <p class="detail__note-hint">这条回复由 AI 自动生成，不代表平台立场</p>
        </div>

        <PostReplyForm
          ref="replyFormRef"
          :submitting="replySubmitting"
          @submit="handleSubmitReply"
        />

        <PostReplyList
          :replies="replies"
          :loading="repliesLoading"
          :login-user-id="userStore.loginUser?.id"
          :updating-id="updatingReplyId"
          :deleting-id="deletingReplyId"
          @edit-submit="handleEditReply"
          @delete="handleDeleteReply"
        />
      </template>

      <EmptyState
        v-else
        doodle="cloud"
        title="帖子不存在或已删除"
      >
        <BaseButton variant="primary" @click="goBack">回到社区</BaseButton>
      </EmptyState>

      <PostEditDialog
        v-model="editOpen"
        :post="currentPost"
        :topics="topics"
        :saving="postUpdating"
        @save="handleSavePost"
      />
    </div>
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
</style>
