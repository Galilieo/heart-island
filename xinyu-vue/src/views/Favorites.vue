<script setup>
// 个人收藏页。展示当前用户收藏过的帖子，点卡片跳详情。
// 在本页取消收藏后，该帖会即时从列表移除（communityStore.toggleFavorite 内处理）。
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import Doodle from '../components/ui/Doodle.vue'
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
    <div class="container fav-page">
      <header class="fav-page__hero">
        <div>
          <p class="fav-page__kicker">我的收藏</p>
          <h1 class="fav-page__title">想再看看的帖子</h1>
          <p class="fav-page__sub">
            之前收藏的帖子，随时可以回来翻翻。
          </p>
        </div>
        <div class="fav-page__hero-doodles" aria-hidden="true">
          <Doodle name="star"  :size="52" color="rgba(243,185,95,0.55)"  class="fd-1" />
          <Doodle name="heart" :size="40" color="rgba(217,140,122,0.55)" class="fd-2" />
          <Doodle name="cloud" :size="58" color="rgba(111,169,164,0.45)" class="fd-3" />
        </div>
      </header>

      <BaseCard padding="md" class="fav-page__board">
        <header class="fav-page__list-head">
          <div>
            <p class="fav-page__kicker">收藏夹</p>
            <h2 class="fav-page__list-title">共 {{ favorites.length }} 条</h2>
          </div>
        </header>

        <div class="fav-page__list">
          <template v-if="loading && !favorites.length">
            <SkeletonCard v-for="i in 3" :key="i" avatar rows="3" />
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
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
.fav-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.fav-page__hero {
  position: relative;
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--hero-fav-from), var(--hero-fav-to));
  border-radius: var(--radius-4);
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.fav-page__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.fav-page__title {
  margin: 8px 0 0;
  font-size: var(--fs-3xl);
}

.fav-page__sub {
  margin: 10px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-md);
  line-height: 1.7;
  max-width: 460px;
}

.fav-page__hero-doodles {
  position: relative;
  width: 200px;
  height: 100px;
}

.fd-1 { position: absolute; top: 8%;   left: 12%; }
.fd-2 { position: absolute; top: 44%;  left: 46%; }
.fd-3 { position: absolute; bottom: 6%; right: 10%; }

.fav-page__list-head {
  margin-bottom: 14px;
}

.fav-page__list-title {
  margin: 4px 0 0;
  font-size: var(--fs-xl);
}

.fav-page__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

/* ── 桌面：收藏墙双列卡格，限宽居中让少量内容也有稳定节奏；空状态通栏 ── */
@media (min-width: 769px) {
  .fav-page__board {
    width: min(980px, 100%);
    align-self: center;
  }

  .fav-page__list {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 14px;
    align-items: start;
  }

  .fav-page__list > .empty {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .fav-page__hero-doodles { display: none; }
  .fav-page__hero { padding: 24px; }
  .fav-page__title { font-size: var(--fs-2xl); }
}

@media (max-width: 480px) {
  .fav-page__hero { padding: 20px; }
}
</style>
