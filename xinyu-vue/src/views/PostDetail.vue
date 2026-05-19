<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import PostCard from '../components/community/PostCard.vue'
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
  if (res.ok) editOpen.value = false
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
  <AppShell width="narrow">
    <div class="detail">
      <button type="button" class="detail__back" @click="goBack">
        <span aria-hidden="true">←</span> 返回社区
      </button>

      <template v-if="postLoading && !currentPost">
        <SkeletonCard avatar rows="4" />
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
.detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.detail__back {
  align-self: flex-start;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  color: var(--ink-2);
  border-radius: var(--radius-pill);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.detail__back:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
  border-color: var(--brand-soft);
}
</style>
