<script setup>
// 社区帖子列表页，与首页同一套手账版式：
//   - 页头：标题 + 说明，右侧主按钮「写一篇」打开发帖弹窗
//   - 侧栏（≥1100px）：话题卡 + 社区约定卡；窄屏排在页头之后、列表之前
//   - 列表：当前话题名 + 最新/最热、全部/我的范围按钮 + 帖子卡
// 点卡片跳详情页；抱抱/收藏在卡片上原地完成（走 communityStore 的乐观更新）。
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseDialog from '../components/ui/BaseDialog.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
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
    if (res.refreshFailed) toast.warn('帖子已保存，列表刷新失败，请刷新查看')
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
    <div class="container">
      <div class="post-layout">
        <header class="post-head">
          <div class="post-head__lead">
            <h1 class="post-head__title">大家最近怎么样</h1>
            <p class="post-head__sub">帖子都以「匿名岛民」展示，聊聊近况，也请尊重不同的感受。</p>
          </div>
          <BaseButton variant="primary" class="post-head__cta" @click="showPublish = true">
            写一篇
          </BaseButton>
        </header>

        <!-- 侧栏卡：话题 + 社区约定；窄屏时整体插到页头与列表之间，不做 sticky -->
        <aside class="post-rail">
          <section class="rail-card">
            <h3 class="rail-card__title">话题</h3>
            <TopicFilter
              vertical
              :topics="topics"
              :selected-topic-id="filters.topicId"
              @select="selectTopic"
            />
          </section>
          <section class="rail-card">
            <h3 class="rail-card__title">社区约定</h3>
            <ul class="post-rules">
              <li>所有人都以「匿名岛民」出现，不打听、不公开他人身份。</li>
              <li>可以不同意，但请好好说话。</li>
              <li>如果你或别人正处于危险中，请立即联系身边的人或当地急救。</li>
            </ul>
          </section>
        </aside>

        <section class="post-listing">
          <div class="post-toolbar">
            <h2 class="post-toolbar__topic">{{ selectedTopicName }}</h2>
            <div class="post-toolbar__groups">
              <div class="post-range" role="group" aria-label="排序方式">
                <button
                  type="button"
                  class="post-range__btn"
                  :class="{ 'is-on': filters.sort === 'latest' }"
                  @click="setSort('latest')"
                >最新</button>
                <button
                  type="button"
                  class="post-range__btn"
                  :class="{ 'is-on': filters.sort === 'hot' }"
                  @click="setSort('hot')"
                >最热</button>
              </div>
              <div class="post-range" role="group" aria-label="帖子范围">
                <button
                  type="button"
                  class="post-range__btn"
                  :class="{ 'is-on': !filters.mine }"
                  @click="setScope(false)"
                >全部</button>
                <button
                  type="button"
                  class="post-range__btn"
                  :class="{ 'is-on': filters.mine }"
                  @click="setScope(true)"
                >我的</button>
              </div>
            </div>
          </div>

          <TransitionGroup name="motion-list" tag="div" class="post-list motion-list">
            <template v-if="postsLoading && !posts.length">
              <SkeletonCard v-for="i in 3" :key="`post-skeleton-${i}`" rows="3" />
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
              v-else-if="!postsLoading"
              key="post-empty"
              expression="default"
              title="这里还没有帖子"
              hint="换个话题看看，或者写下第一篇。"
            >
              <BaseButton variant="soft" size="sm" @click="showPublish = true">
                写一篇
              </BaseButton>
            </EmptyState>
          </TransitionGroup>
        </section>
      </div>

      <BaseDialog
        v-model="showPublish"
        title="发布新帖子"
        width="md"
        :persistent="publishing"
      >
        <PostPublishForm
          ref="publishRef"
          :topics="topics"
          :default-topic-id="filters.topicId"
          :submitting="publishing"
          @submit="handlePublish"
        />
      </BaseDialog>
    </div>
  </AppShell>
</template>

<style scoped>
/* ── 版式：与首页相同的「主栏 + 侧栏」网格，侧栏不吸附、不限高 ── */
.post-layout {
  display: grid;
  /* 列宽来源同首页：主栏 680 + 侧栏 300 + 间距 40 ≈ 1020，再留两侧留白后取 1100 */
  grid-template-columns: minmax(0, 680px) 300px;
  grid-template-areas:
    "head rail"
    "list rail";
  justify-content: center;
  column-gap: 40px;
  row-gap: 20px;
  max-width: 1100px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.post-head { grid-area: head; min-width: 0; }

.post-listing { grid-area: list; min-width: 0; }

.post-rail {
  grid-area: rail;
  align-self: start;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.rail-card {
  padding: 18px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: var(--shadow-sm);
}

.rail-card__title {
  margin: 0 0 10px;
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
}

.post-rules {
  display: grid;
  gap: 6px;
  margin: 0;
  padding-left: 1.2em;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

/* ── 页头：标题 + 说明，右侧「写一篇」 ── */
.post-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 16px;
}

.post-head__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
  line-height: 1.3;
}

.post-head__sub {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

/* ── 列表工具条：当前话题名 + 排序 / 范围按钮 ── */
.post-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 10px 12px;
  padding-bottom: 10px;
  border-bottom: 1px dashed var(--line-strong);
}

.post-toolbar__topic {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-xl);
  font-weight: 400;
}

.post-toolbar__groups {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-range {
  display: flex;
  gap: 4px;
}

/* 范围按钮样式同首页 MoodFilterBar：无边框、选中品牌柔底 */
.post-range__btn {
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.post-range__btn:not(.is-on):hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.post-range__btn.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.post-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 16px;
}

/* ── <1100px：放不下两栏，单列，侧栏插到页头与列表之间 ── */
@media (max-width: 1099px) {
  .post-layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      "head"
      "rail"
      "list";
    max-width: 720px;
  }
}

/* 平板宽度下两张侧栏卡并排（同首页做法） */
@media (min-width: 641px) and (max-width: 1099px) {
  .post-rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

/* 触控目标补足到 44px（话题胶囊在 TopicFilter 内处理） */
@media (max-width: 768px) {
  .post-range__btn {
    min-height: 44px;
  }
}

@media (max-width: 640px) {
  .post-head {
    flex-direction: column;
    align-items: stretch;
  }

  .post-head__title {
    font-size: var(--fs-2xl);
  }

  .post-head__cta {
    width: 100%;
  }
}

@media (max-width: 480px) {
  .post-layout {
    row-gap: 16px;
    padding-bottom: 24px;
  }

  .post-toolbar {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
