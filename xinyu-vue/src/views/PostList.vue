<script setup>
// 社区帖子列表页。
//   - 顶部 hero + 话题胶囊筛选（TopicFilter）
//   - 可折叠的发帖表单（PostPublishForm）
//   - 帖子卡列表 + 全部/我的切换
// 点卡片跳详情页；点赞/收藏在卡片上原地完成（走 communityStore 的乐观更新）。
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import Doodle from '../components/ui/Doodle.vue'
import TopicFilter from '../components/community/TopicFilter.vue'
import PostCard from '../components/community/PostCard.vue'
import PostPublishForm from '../components/community/PostPublishForm.vue'
import { useCommunityStore } from '../stores/community'
import { useUserStore } from '../stores/user'
import { useAsyncAction } from '../composables/useAsyncAction'
import { useToast } from '../composables/useToast'

const router = useRouter()
const communityStore = useCommunityStore()
const userStore = useUserStore()
const toast = useToast()

const { topics, posts, filters } = storeToRefs(communityStore)

const publishRef = ref(null)
const showPublish = ref(false)

const { run: doFetchPosts, loading: postsLoading } = useAsyncAction(
  () => communityStore.fetchPosts(),
  { silent: true }
)

const { run: doFetchTopics } = useAsyncAction(
  () => communityStore.fetchTopics(),
  { silent: true }
)

const { run: doPublish, loading: publishing } = useAsyncAction(
  (payload) => communityStore.publishPost(payload),
  { successMsg: '发布成功' }
)

const selectedTopicName = computed(() => {
  if (filters.value.topicId === null) return '全部话题'
  const t = topics.value.find((x) => x.id === filters.value.topicId)
  return t ? t.name : '当前话题'
})

async function selectTopic(topicId) {
  communityStore.setTopicFilter(topicId)
  await doFetchPosts()
}

async function setScope(mine) {
  communityStore.setMineFilter(mine)
  await doFetchPosts()
}

async function setSort(sort) {
  communityStore.setSort(sort)
  await doFetchPosts()
}

async function handlePublish(payload) {
  const res = await doPublish(payload)
  if (res.ok) {
    publishRef.value?.resetAfterSuccess()
    showPublish.value = false
  }
}

async function handleLike(post) {
  const res = await communityStore.toggleLike(post)
  if (!res.ok) toast.error(res.message || '操作失败')
}

async function handleFavorite(post) {
  const res = await communityStore.toggleFavorite(post)
  if (!res.ok) toast.error(res.message || '操作失败')
}

function openDetail(id) {
  router.push(`/community/post/${id}`)
}

onMounted(async () => {
  await doFetchTopics()
  await doFetchPosts()
})
</script>

<template>
  <AppShell>
    <div class="container post-page">
      <header class="post-page__hero">
        <div>
          <p class="post-page__kicker">匿名社区</p>
          <h1 class="post-page__title">情绪互助广场</h1>
          <p class="post-page__sub">
            在不暴露身份的前提下，看看大家如何安放自己的情绪。
          </p>
        </div>
        <div class="post-page__hero-doodles" aria-hidden="true">
          <Doodle name="leaf"  :size="48" color="rgba(155,193,188,0.6)"  class="pd-1" />
          <Doodle name="cloud" :size="60" color="rgba(111,169,164,0.45)" class="pd-2" />
          <Doodle name="star"  :size="32" color="rgba(217,140,122,0.55)" class="pd-3" />
        </div>
      </header>

      <BaseCard padding="md" class="post-page__filter">
        <header class="post-page__filter-head">
          <div>
            <p class="post-page__kicker post-page__kicker--inline">话题</p>
            <h3 class="post-page__filter-title">想看哪一个方向？</h3>
          </div>
          <BaseButton
            variant="primary"
            size="sm"
            @click="showPublish = !showPublish"
          >
            {{ showPublish ? '收起' : '+ 发布' }}
          </BaseButton>
        </header>
        <TopicFilter
          :topics="topics"
          :selected-topic-id="filters.topicId"
          @select="selectTopic"
        />
      </BaseCard>

      <transition name="publish">
        <PostPublishForm
          v-if="showPublish"
          ref="publishRef"
          :topics="topics"
          :default-topic-id="filters.topicId"
          :submitting="publishing"
          @submit="handlePublish"
        />
      </transition>

      <BaseCard padding="md">
        <header class="post-page__list-head">
          <div>
            <p class="post-page__kicker">帖子</p>
            <h2 class="post-page__list-title">{{ selectedTopicName }}</h2>
          </div>
          <div class="post-page__controls">
            <div class="post-page__scope">
              <button
                type="button"
                class="scope-chip"
                :class="{ 'is-active': filters.sort === 'latest' }"
                @click="setSort('latest')"
              >最新</button>
              <button
                type="button"
                class="scope-chip"
                :class="{ 'is-active': filters.sort === 'hot' }"
                @click="setSort('hot')"
              >最热</button>
            </div>
            <div class="post-page__scope">
              <button
                type="button"
                class="scope-chip"
                :class="{ 'is-active': !filters.mine }"
                @click="setScope(false)"
              >全部</button>
              <button
                type="button"
                class="scope-chip"
                :class="{ 'is-active': filters.mine }"
                @click="setScope(true)"
              >我的</button>
            </div>
          </div>
        </header>

        <div class="post-page__list">
          <template v-if="postsLoading && !posts.length">
            <SkeletonCard v-for="i in 3" :key="i" avatar rows="3" />
          </template>

          <template v-else-if="posts.length">
            <PostCard
              v-for="p in posts"
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
            doodle="cloud"
            title="这里还没有帖子"
            hint="换个话题试试，或者点上面的「发布」写下你想说的。"
          >
            <BaseButton variant="soft" size="sm" @click="showPublish = true">
              我来写第一条
            </BaseButton>
          </EmptyState>
        </div>
      </BaseCard>
    </div>
  </AppShell>
</template>

<style scoped>
.post-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.post-page__hero {
  position: relative;
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--brand-soft), var(--bg-soft));
  border-radius: var(--radius-4);
  overflow: hidden;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
}

.post-page__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.post-page__kicker--inline { margin-bottom: 2px; }

.post-page__title {
  margin: 8px 0 0;
  font-size: var(--fs-3xl);
}

.post-page__sub {
  margin: 10px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-md);
  line-height: 1.7;
  max-width: 460px;
}

.post-page__hero-doodles {
  position: relative;
  width: 220px;
  height: 100px;
}

.pd-1 { position: absolute; top: 10%;  left: 8%; }
.pd-2 { position: absolute; top: 36%;  left: 38%; }
.pd-3 { position: absolute; bottom: 10%; right: 12%; }

.post-page__filter-head,
.post-page__list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.post-page__filter-title {
  margin: 4px 0 0;
  font-size: var(--fs-lg);
}

.post-page__list-title {
  margin: 4px 0 0;
  font-size: var(--fs-xl);
}

.post-page__controls {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.post-page__scope {
  display: flex;
  gap: 6px;
  background: var(--bg-soft);
  padding: 4px;
  border-radius: var(--radius-pill);
}

.scope-chip {
  height: 28px;
  padding: 0 14px;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  border-radius: var(--radius-pill);
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.scope-chip.is-active {
  background: var(--bg-card);
  color: var(--brand-deep);
  box-shadow: var(--shadow-sm);
}

.post-page__list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.publish-enter-from,
.publish-leave-to {
  opacity: 0;
  transform: translateY(-8px);
  max-height: 0;
}

.publish-enter-to,
.publish-leave-from {
  opacity: 1;
  max-height: 800px;
}

.publish-enter-active,
.publish-leave-active {
  transition: opacity var(--t-base) var(--ease-soft),
    transform var(--t-base) var(--ease-soft),
    max-height var(--t-slow) var(--ease-soft);
  overflow: hidden;
}

@media (max-width: 768px) {
  .post-page__hero-doodles { display: none; }
  .post-page__hero {
    padding: 24px;
  }
  .post-page__title { font-size: var(--fs-2xl); }
}

@media (max-width: 480px) {
  .post-page__hero { padding: 20px; }
  .post-page__list-head {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
  .post-page__controls {
    justify-content: flex-start;
  }
  .post-page__filter-head {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>
