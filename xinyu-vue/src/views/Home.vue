<script setup>
// 心情主页。整页只做"组装 + 事件绑定"：
//   - 顶部：日期 + 问候 + 上次心情提示
//   - 左栏：MoodForm（写心情）
//   - 右栏：「这一周」MoodTrendPanel + 本月 MoodMonthCalendar
//   - 时间线：MoodFilterBar + MoodCard 列表 + 分页
//   - 中心：MoodDetailDialog（查看/编辑详情弹窗）
// 所有数据/请求逻辑都在 moodStore，本文件不直接调 api 或读 localStorage。
import { ref, onMounted, computed } from 'vue'
import { storeToRefs } from 'pinia'
import AppShell from '../components/layout/AppShell.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import BasePagination from '../components/ui/BasePagination.vue'
import EmptyState from '../components/ui/EmptyState.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import MoodForm from '../components/mood/MoodForm.vue'
import MoodCard from '../components/mood/MoodCard.vue'
import MoodFilterBar from '../components/mood/MoodFilterBar.vue'
import MoodTrendPanel from '../components/mood/MoodTrendPanel.vue'
import MoodMonthCalendar from '../components/mood/MoodMonthCalendar.vue'
import MoodDetailDialog from '../components/mood/MoodDetailDialog.vue'
import { useMoodStore } from '../stores/mood'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useConfirm } from '../composables/useConfirm'
import { useAsyncAction } from '../composables/useAsyncAction'
import { useMoodPolling } from '../composables/useMoodPolling.js'

const moodStore = useMoodStore()
const userStore = useUserStore()
const { now } = useMoodPolling(moodStore, userStore)
const toast = useToast()
const { confirm } = useConfirm()

const { list, total, pages, selected, filters, pagination, trend, month } =
  storeToRefs(moodStore)

const moodType = ref('')
const content = ref('')
const needAiReply = ref(true)
const detailLoadingId = ref(null)
const deletingId = ref(null)
const detailOpen = ref(false)

const greeting = computed(() => {
  const h = new Date().getHours()
  const name = userStore.displayName
  if (h < 6) return `深夜了，${name}`
  if (h < 12) return `早安，${name}`
  if (h < 18) return `午后好，${name}`
  return `晚上好，${name}`
})

// 顶部日期行，如「11月11日 · 周三」；星期按本地时区取，和列表时间一致
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

const dateLabel = computed(() => {
  const today = new Date()
  return `${today.getMonth() + 1}月${today.getDate()}日 · ${WEEKDAYS[today.getDay()]}`
})

// 月历「今天」用本地日期字符串；now 由轮询每秒递增，跨过午夜后会自动换到新日期。
const todayKey = computed(() => {
  const date = new Date(now.value || Date.now())
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
})

const todaySubtitle = computed(() => {
  const last = trend.value?.lastMoodType
  if (last) return `上一次你写下的是「${last}」。`
  return '今天过得怎么样？想说的都可以记下来。'
})

const { run: doFetchList, loading: listLoading } = useAsyncAction(
  () => moodStore.fetchList(),
  { silent: true, concurrent: true }
)

const { run: doFetchTrend } = useAsyncAction(
  () => moodStore.fetchTrend(),
  { silent: true }
)

const { run: doAdd, loading: adding } = useAsyncAction(
  () => moodStore.add({ moodType: moodType.value, content: content.value.trim(), needAiReply: needAiReply.value }),
  { errorMsg: '暂时没能保存，请稍后再试' }
)

const { run: doUpdate, loading: updating } = useAsyncAction(
  (payload) => moodStore.update(payload.id, { moodType: payload.moodType, content: payload.content, needAiReply: payload.needAiReply }),
  {}
)

async function handleAdd() {
  if (!moodType.value) return toast.warn('先选一个心情类型')
  if (!content.value.trim()) return toast.warn('写一点点也好')
  const res = await doAdd()
  if (res.ok) {
    moodType.value = ''
    content.value = ''
    showSavedResult(res)
  }
}

function showSavedResult(res) {
  if (res.refreshFailed) toast.warn('已保存，列表刷新失败，请刷新查看')
  else toast.success('心情已保存，回复状态可在记录中查看')
}

async function handleRefresh() {
  const detailId = moodStore.selected?.id
  const operations = [moodStore.fetchList()]
  if (detailId) operations.push(moodStore.openDetail(detailId))
  const results = await Promise.allSettled(operations)
  const failed = results.some(result => result.status === 'rejected' || !result.value?.ok)
  if (failed) toast.warn('部分状态暂未能刷新，请稍后再试')
  moodStore.refreshObservation()
}

const { run: handleRetry } = useAsyncAction(id => moodStore.retry(id), { successMsg: '已更新回复状态' })

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
  moodStore.setPage(p)
  await doFetchList()
}

async function openDetail(id) {
  detailLoadingId.value = id
  try {
    const res = await moodStore.openDetail(id)
    if (res.ok) detailOpen.value = true
    else if (!res.stale) toast.error(res.message || '加载详情失败')
  } catch (error) {
    toast.error(error.userMessage || '加载详情失败')
  } finally {
    if (detailLoadingId.value === id) detailLoadingId.value = null
  }
}

function closeDetail() {
  detailOpen.value = false
  moodStore.closeDetail()
}

async function handleUpdate(payload) {
  const res = await doUpdate(payload)
  if (res.ok) {
    detailOpen.value = false
    moodStore.closeDetail()
    showSavedResult(res)
  }
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
  try {
    const res = await moodStore.remove(id)
    if (res.ok) {
      if (!selected.value) detailOpen.value = false
      if (res.refreshFailed) toast.warn('已删除，列表刷新失败，请刷新查看')
      else toast.success('已删除')
    } else toast.error(res.message || '删除失败')
  } catch (error) {
    toast.error(error.userMessage || '删除失败')
  } finally {
    deletingId.value = null
  }
}

onMounted(() => {
  doFetchList()
  doFetchTrend()
  // 月历只影响侧栏展示，失败由 store 静默处理，不阻塞列表与趋势加载。
  moodStore.fetchMonth(month.value.year, month.value.month)
})
</script>

<template>
  <AppShell>
    <div class="container">
      <div class="home__layout">
        <header class="home__hello">
          <p class="home__date">{{ dateLabel }}</p>
          <h1 class="home__greeting">{{ greeting }}</h1>
          <p class="home__sub">{{ todaySubtitle }}</p>
        </header>

        <section class="home__compose">
          <MoodForm
            v-model:mood-type="moodType"
            v-model:content="content"
            v-model:need-ai-reply="needAiReply"
            :adding="adding"
            @submit="handleAdd"
          />
        </section>

        <!-- 侧栏卡容器：依次放「这一周」与本月月历；窄屏时整体插到书写卡与时间线之间 -->
        <aside class="home__rail">
          <section class="rail-card">
            <MoodTrendPanel :trend="trend" />
          </section>
          <section class="rail-card">
            <MoodMonthCalendar
              :year="month.year"
              :month="month.month"
              :days="month.days"
              :today="todayKey"
            />
          </section>
        </aside>

        <section class="home__timeline">
          <header class="home__timeline-head">
            <div class="home__timeline-heading">
              <h2 class="home__timeline-title">最近的记录</h2>
              <span v-if="total" class="home__timeline-count">共 {{ total }} 条</span>
            </div>
            <BaseButton variant="link" size="sm" :loading="listLoading" @click="handleRefresh">刷新</BaseButton>
          </header>

          <MoodFilterBar
            v-model:mood-type="filters.moodType"
            v-model:start-date="filters.startDate"
            v-model:end-date="filters.endDate"
            class="home__filter"
            @apply="handleApplyFilter"
            @reset="handleResetFilter"
          />

          <TransitionGroup name="motion-list" tag="div" class="home__list motion-list">
            <template v-if="listLoading && !list.length">
              <SkeletonCard v-for="i in 3" :key="`mood-skeleton-${i}`" rows="3" />
            </template>

            <template v-else-if="list.length">
              <MoodCard
                v-for="m in list"
                :key="m.id"
                :mood="m"
                :now="now"
                :retrying="moodStore.pendingIds.has(String(m.id))"
                :detail-loading="detailLoadingId === m.id"
                :deleting="deletingId === m.id"
                @detail="openDetail"
                @delete="handleDelete"
                @retry="handleRetry"
                @refresh="handleRefresh"
              />
            </template>

            <EmptyState
              v-else-if="!listLoading"
              key="mood-empty"
              expression="default"
              title="还没有心情记录"
              hint="写下心情并保存后，就能在这里回看。"
            />
          </TransitionGroup>

          <div v-if="pages > 1" class="home__pgn">
            <BasePagination
              :page-num="pagination.pageNum"
              :pages="pages"
              :total="total"
              @change="handlePageChange"
            />
          </div>
        </section>
      </div>

      <MoodDetailDialog
        v-model="detailOpen"
        :mood="selected"
        :updating="updating"
        :now="now"
        :retrying="selected && moodStore.pendingIds.has(String(selected.id))"
        @retry="handleRetry"
        @refresh="handleRefresh"
        @update="handleUpdate"
        @close="closeDetail"
      />
    </div>
  </AppShell>
</template>

<style scoped>
/* ── 版式：宽屏「主栏 + 侧栏」，窄屏按 hello → compose → rail → timeline 单列 ── */
.home__layout {
  display: grid;
  /* 列宽来源：主栏 680 + 侧栏 300 + 间距 40 ≈ 1020，再留两侧留白后取 1100。
     侧栏从 compose 行开始跨两行，首卡与书写卡顶边对齐（design-lab 同款视觉）。 */
  grid-template-columns: minmax(0, 680px) 300px;
  grid-template-areas:
    "hello hello"
    "compose rail"
    "timeline rail";
  justify-content: center;
  column-gap: 40px;
  row-gap: 20px;
  max-width: 1100px;
  margin: 0 auto;
  padding-bottom: 40px;
}

.home__hello { grid-area: hello; }
.home__compose { grid-area: compose; min-width: 0; }
.home__timeline { grid-area: timeline; min-width: 0; }

/* 侧栏不做 sticky：卡片总高度可能超过一屏，贴顶会让底部卡片卡在视口外。
   网格把侧栏放在 compose 行起，首卡自然与书写卡顶边对齐，无需额外 padding 偏移。 */
.home__rail {
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

/* ── 问候块 ── */
.home__date {
  color: var(--brand-deep);
  font-size: var(--fs-sm);
}

.home__greeting {
  margin-top: 4px;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
  line-height: 1.3;
}

.home__sub {
  margin-top: 4px;
  color: var(--ink-2);
}

/* ── 时间线 ── */
.home__timeline-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.home__timeline-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.home__timeline-title {
  font-family: var(--font-display);
  font-size: var(--fs-xl);
  font-weight: 400;
}

.home__timeline-count {
  color: var(--ink-3);
  font-size: var(--fs-sm);
  white-space: nowrap;
}

.home__filter {
  margin-top: 8px;
}

.home__list {
  display: flex;
  flex-direction: column;
  margin-top: 8px;
}

.home__pgn {
  margin-top: 16px;
  display: flex;
  justify-content: center;
}

/* ── <1100px：网格放不下两栏，改为单列并把侧栏卡插到书写卡与时间线之间 ── */
@media (max-width: 1099px) {
  .home__layout {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas:
      "hello"
      "compose"
      "rail"
      "timeline";
    max-width: 720px;
  }
}

/* 平板宽度下两张侧栏卡并排，避免月历在整栏宽度里格子被拉得过大 */
@media (min-width: 641px) and (max-width: 1099px) {
  .home__rail {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}

@media (max-width: 640px) {
  .home__greeting {
    font-size: var(--fs-2xl);
  }
}

@media (max-width: 480px) {
  .home__layout {
    row-gap: 16px;
    padding-bottom: 24px;
  }
}
</style>
