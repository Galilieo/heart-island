<script setup>
// 个人收藏页。680px 单列居中，与帖子详情同宽：页头 + 帖子卡直接排列。
// 在本页取消收藏后，该帖会即时从列表移除（communityStore.toggleFavorite 内处理）。
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import PostCard from '../components/community/PostCard.vue'
import { useCommunityStore } from '../stores/community'
import { useUserStore } from '../stores/user'
import { useAsyncAction } from '../composables/useAsyncAction'
import { useToast } from '../composables/useToast'

const router = useRouter()
const communityStore = useCommunityStore()
const userStore = useUserStore()
const toast = useToast()

const { favorites, topics } = storeToRefs(communityStore)

const { run: doFetch, loading } = useAsyncAction(
  () => communityStore.fetchFavorites(),
  { silent: true }
)

const { run: doFetchTopics } = useAsyncAction(
  () => communityStore.fetchTopics(),
  { silent: true }
)

async function handleLike(post) {
  const res = await communityStore.toggleLike(post)
  if (!res.ok) toast.error(res.message || '操作失败')
}

async function handleFavorite(post) {
  const res = await communityStore.toggleFavorite(post)
  if (res.ok) {
    if (!post.favorited) toast.info('已取消收藏')
  } else {
    toast.error(res.message || '操作失败')
  }
}

function openDetail(id) {
  router.push(`/community/post/${id}`)
}

onMounted(async () => {
  await doFetchTopics()
  await doFetch()
})
</script>

<template>
  <AppShell>
    <div class="container">
      <div class="fav-page">
        <header class="fav-head">
          <h1 class="fav-head__title">想再看看的帖子</h1>
          <p class="fav-head__sub">之前收藏的帖子，随时可以回来翻翻。</p>
          <p class="fav-head__count">共 {{ favorites.length }} 条</p>
        </header>

        <div class="fav-list">
          <template v-if="loading && !favorites.length">
            <SkeletonCard v-for="i in 3" :key="i" rows="3" />
          </template>

          <template v-else-if="favorites.length">
            <PostCard
              v-for="p in favorites"
              :key="p.id"
              :post="p"
              :topics="topics"
              :login-user-id="userStore.loginUser?.id"
              variant="card"
              @open="openDetail"
              @toggle-like="handleLike"
              @toggle-favorite="handleFavorite"
            />
          </template>

          <EmptyState
            v-else
            expression="love"
            title="还没有收藏的帖子"
            hint="看到想留着的帖子，点一下「收藏」就能存到这里。"
          >
            <BaseButton variant="primary" size="sm" @click="router.push('/posts')">
              去社区逛逛
            </BaseButton>
          </EmptyState>
        </div>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
/* 与帖子详情同宽的单列，收藏的帖子卡直接排列 */
.fav-page {
  width: 100%;
  max-width: 680px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.fav-head__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
  line-height: 1.3;
}

.fav-head__sub {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.fav-head__count {
  margin: 4px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.fav-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

@media (max-width: 640px) {
  .fav-head__title {
    font-size: var(--fs-2xl);
  }
}

@media (max-width: 480px) {
  .fav-page {
    padding-bottom: 24px;
  }
}
</style>
