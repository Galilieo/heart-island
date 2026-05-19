<script setup>
// 心情主页。整页只做"组装 + 事件绑定"：
//   - 左栏：MoodForm（写心情）
//   - 右栏：MoodTrendPanel（7 天趋势）+ MoodFilterBar + MoodCard 列表 + 分页
//   - 中心：MoodDetailDialog（查看/编辑详情弹窗）
// 所有数据/请求逻辑都在 moodStore，本文件不直接调 api 或读 localStorage。
import { ref, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseCard from '../components/ui/BaseCard.vue'
import BaseTag from '../components/ui/BaseTag.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import Doodle from '../components/ui/Doodle.vue'
import MoodForm from '../components/mood/MoodForm.vue'
import MoodCard from '../components/mood/MoodCard.vue'
import MoodFilterBar from '../components/mood/MoodFilterBar.vue'
import MoodTrendPanel from '../components/mood/MoodTrendPanel.vue'
import MoodDetailDialog from '../components/mood/MoodDetailDialog.vue'
import { useMoodStore } from '../stores/mood'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useConfirm } from '../composables/useConfirm'
import { useAsyncAction } from '../composables/useAsyncAction'

const moodStore = useMoodStore()
const userStore = useUserStore()
const toast = useToast()
const { confirm } = useConfirm()

const { list, total, pages, selected, filters, pagination, trend } =
  storeToRefs(moodStore)

const moodType = ref('')
const content = ref('')
const detailLoadingId = ref(null)
const deletingId = ref(null)
const detailOpen = ref(false)

const greeting = computed(() => {
  const h = new Date().getHours()
  const name = userStore.loginUser?.nickname || userStore.loginUser?.username || ''
  if (h < 6) return `深夜了，${name}`
  if (h < 12) return `早安，${name}`
  if (h < 18) return `午后好，${name}`
  return `晚上好，${name}`
})

const todaySubtitle = computed(() => {
  const last = trend.value?.lastMoodType
  if (last) return `上一次你写下的是「${last}」，今天感觉怎么样？`
  return '把心里漂浮的那一点点感受，慢慢落下来。'
})

const { run: doFetchList, loading: listLoading } = useAsyncAction(
  () => moodStore.fetchList(),
  { silent: true }
)

const { run: doFetchTrend } = useAsyncAction(
  () => moodStore.fetchTrend(),
  { silent: true }
)

const { run: doAdd, loading: adding } = useAsyncAction(
  () => moodStore.add({ moodType: moodType.value, content: content.value.trim() }),
  { successMsg: '记录成功，心屿回复已生成', errorMsg: '新增失败，请检查后端或 AI 服务' }
)

const { run: doUpdate, loading: updating } = useAsyncAction(
  (payload) => moodStore.update(payload.id, { moodType: payload.moodType, content: payload.content }),
  { successMsg: '已保存修改' }
)

async function handleAdd() {
  if (!moodType.value) return toast.warn('先选一个心情类型')
  if (!content.value.trim()) return toast.warn('写一点点也好')
  const res = await doAdd()
  if (res.ok) {
    moodType.value = ''
    content.value = ''
  }
}

async function handleApplyFilter() {
  if (filters.value.startDate && filters.value.endDate && filters.value.startDate > filters.value.endDate) {
    return toast.warn('开始日期不能晚于结束日期')
  }
  pagination.value.pageNum = 1
  await doFetchList()
}

async function handleResetFilter() {
  moodStore.resetFilters()
  await doFetchList()
}

async function handlePageChange(p) {
  pagination.value.pageNum = p
  await doFetchList()
}

async function openDetail(id) {
  detailLoadingId.value = id
  const res = await moodStore.openDetail(id)
  detailLoadingId.value = null
  if (res.ok) {
    detailOpen.value = true
  } else {
    toast.error(res.message || '加载详情失败')
  }
}

function closeDetail() {
  detailOpen.value = false
  moodStore.closeDetail()
}

async function handleUpdate(payload) {
  const res = await doUpdate(payload)
  if (res.ok) detailOpen.value = false
}

async function handleDelete(id) {
  const ok = await confirm({
    title: '删除这条心情？',
    content: '删除后将无法恢复。',
    confirmText: '删除',
    tone: 'danger'
  })
  if (!ok) return
  deletingId.value = id
  const res = await moodStore.remove(id)
  deletingId.value = null
  if (res.ok) toast.success('已删除')
  else toast.error(res.message || '删除失败')
}

onMounted(() => {
  doFetchList()
  doFetchTrend()
})
</script>

<template>
  <AppShell>
    <div class="container home">
      <header class="home__hero">
        <div class="home__hero-text">
          <p class="home__kicker">心屿 · 心情日记</p>
          <h1 class="home__greet">
            {{ greeting }}<span class="home__greet-dot">·</span>
          </h1>
          <p class="home__sub">{{ todaySubtitle }}</p>
        </div>
        <div class="home__hero-doodles" aria-hidden="true">
          <Doodle name="cloud" :size="64" color="rgba(111,169,164,0.4)" class="hd-1" />
          <Doodle name="star"  :size="36" color="rgba(217,140,122,0.5)" class="hd-2" />
          <Doodle name="leaf"  :size="44" color="rgba(155,193,188,0.55)" class="hd-3" />
        </div>
      </header>

      <div class="home__main">
        <section class="home__left">
          <MoodForm
            v-model:mood-type="moodType"
            v-model:content="content"
            :adding="adding"
            @submit="handleAdd"
          />
        </section>

        <section class="home__right">
          <MoodTrendPanel :trend="trend" />

          <BaseCard padding="md" class="home__list">
            <header class="home__list-head">
              <div>
                <p class="home__list-kicker">记录册</p>
                <h2 class="home__list-title">最近的心情</h2>
              </div>
              <BaseTag v-if="total" tone="brand" variant="soft" size="md">
                共 {{ total }} 条
              </BaseTag>
            </header>

            <MoodFilterBar
              v-model:mood-type="filters.moodType"
              v-model:start-date="filters.startDate"
              v-model:end-date="filters.endDate"
              @apply="handleApplyFilter"
              @reset="handleResetFilter"
            />

            <div class="home__list-body">
              <template v-if="listLoading && !list.length">
                <SkeletonCard v-for="i in 3" :key="i" rows="3" />
              </template>

              <template v-else-if="list.length">
                <MoodCard
                  v-for="m in list"
                  :key="m.id"
                  :mood="m"
                  :detail-loading="detailLoadingId === m.id"
                  :deleting="deletingId === m.id"
                  @detail="openDetail"
                  @delete="handleDelete"
                />
              </template>

              <EmptyState
                v-else
                doodle="cloud"
                title="还没有心情记录"
                hint="左边写下一点感受，这里就会出现你的第一段记录。"
              />
            </div>

            <div v-if="pages > 1" class="home__pgn">
              <BasePagination
                :page-num="pagination.pageNum"
                :pages="pages"
                :total="total"
                @change="handlePageChange"
              />
            </div>
          </BaseCard>
        </section>
      </div>

      <MoodDetailDialog
        v-model="detailOpen"
        :mood="selected"
        :updating="updating"
        @update="handleUpdate"
        @close="closeDetail"
      />
    </div>
  </AppShell>
</template>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.home__hero {
  position: relative;
  padding: 28px 32px;
  background: linear-gradient(135deg, var(--brand-soft), var(--accent-soft));
  border-radius: var(--radius-4);
  overflow: hidden;
}

.home__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.home__greet {
  margin: 8px 0 0;
  font-size: var(--fs-3xl);
}

.home__greet-dot {
  margin-left: 4px;
  color: var(--accent);
  font-family: var(--font-display);
}

.home__sub {
  margin: 10px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-md);
  line-height: 1.7;
  max-width: 540px;
}

.home__hero-doodles {
  position: absolute;
  top: 0;
  right: 0;
  width: 280px;
  height: 100%;
  pointer-events: none;
}

.hd-1 { position: absolute; top: 10%; right: 60%; }
.hd-2 { position: absolute; top: 40%; right: 26%; }
.hd-3 { position: absolute; top: 18%; right: 10%; transform: rotate(15deg); }

.home__main {
  display: grid;
  grid-template-columns: 380px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.home__right {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.home__list-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.home__list-kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.home__list-title {
  margin: 4px 0 0;
  font-size: var(--fs-2xl);
}

.home__list-body {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.home__pgn {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}

@media (max-width: 980px) {
  .home__main {
    grid-template-columns: 1fr;
  }

  .home__hero-doodles { display: none; }
}
</style>
