<script setup>
// 回顾页：周报/近 30 天统计、小芽来信（12 种状态）、引用来源卡片、过往周报与任务轮询协调。
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../components/layout/AppShell.vue'
import SkeletonCard from '../components/ui/SkeletonCard.vue'
import ReviewLetter from '../components/review/ReviewLetter.vue'
import ReviewSourceCards from '../components/review/ReviewSourceCards.vue'
import ReviewHistory from '../components/review/ReviewHistory.vue'
import ReviewStatsSidebar from '../components/review/ReviewStatsSidebar.vue'
import { useReviewStore } from '../stores/review'
import { useUserStore } from '../stores/user'
import { useToast } from '../composables/useToast'
import { useReviewPolling } from '../composables/useReviewPolling'
import {
  canGoNextWeek,
  formatRangeLong,
  mondayOf,
  resolveReviewViewState,
  shiftWeek,
  toDateKey
} from '../utils/review'

const route = useRoute()
const router = useRouter()
const reviewStore = useReviewStore()
const userStore = useUserStore()
const toast = useToast()

const {
  weekStats,
  recent30Stats,
  weekLoading,
  recent30Loading,
  submitting,
  pollingTimedOut,
  historyItems,
  historyPage,
  historyHasMore,
  historyLoading
} = storeToRefs(reviewStore)

const todayKey = toDateKey(new Date())
const currentMonday = mondayOf(todayKey)

const mode = ref('week')
const weekStart = ref('')
const loadError = ref('')

const activeStats = computed(() =>
  mode.value === 'week' ? weekStats.value : recent30Stats.value
)
const activeLoading = computed(() =>
  mode.value === 'week' ? weekLoading.value : recent30Loading.value
)
const isCurrentWeek = computed(
  () => Boolean(weekStart.value) && weekStart.value === currentMonday
)
const isLastWeek = computed(() => weekStart.value === shiftWeek(currentMonday, -1))
const canNextWeek = computed(
  () => Boolean(weekStart.value) && canGoNextWeek(weekStart.value, todayKey)
)

const viewState = computed(() =>
  resolveReviewViewState(activeStats.value, {
    mode: mode.value,
    isCurrentWeek: mode.value === 'week' && isCurrentWeek.value,
    pollingTimedOut: pollingTimedOut.value
  })
)

const visibleSources = computed(() => {
  if (viewState.value === 'crisis' || !activeStats.value?.report?.body) {
    return []
  }
  return activeStats.value.report.sources || []
})

const pageTitle = computed(() => {
  if (mode.value === 'recent30') {
    return '近 30 天'
  }
  return isLastWeek.value ? '上周回顾' : '周回顾'
})

const subtitle = computed(() => {
  if (!activeStats.value) {
    return ''
  }
  if (mode.value === 'recent30') {
    return `${formatRangeLong(activeStats.value.rangeStart, activeStats.value.rangeEnd)}，按今天往前数 30 天`
  }
  const range = formatRangeLong(activeStats.value.rangeStart, activeStats.value.rangeEnd)
  return isCurrentWeek.value ? `${range} · 本周还在进行中，周日之后可以生成` : range
})

const { stopPolling } = useReviewPolling({
  mode,
  weekStart,
  activeStats,
  pollingTimedOut,
  reviewStore,
  userStore,
  onComplete: (pollingMode, pollingWeekStart) => {
    if (pollingMode === 'week') {
      loadWeek(pollingWeekStart)
      reviewStore.fetchHistory(1).catch(() => {})
    } else {
      loadRecent30()
    }
  },
  onNetworkError: () => {
    toast.error('查询状态失败，请稍后刷新看看')
  }
})

function parseQueryStart(value) {
  return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : ''
}

onMounted(() => {
  const initialStart = parseQueryStart(route.query.start)
  loadWeek(initialStart)
  reviewStore.fetchHistory(1).catch(() => {})
})

watch(
  () => route.query.start,
  (nextStart, prevStart) => {
    if (nextStart === prevStart || mode.value !== 'week') {
      return
    }
    const parsed = parseQueryStart(nextStart)
    if (!parsed) {
      stopPolling()
      loadWeek('')
      return
    }
    if (parsed !== weekStart.value) {
      stopPolling()
      loadWeek(parsed)
    }
  }
)

async function loadWeek(start = '') {
  loadError.value = ''
  reviewStore.setPollingTimedOut(false)
  const res = await reviewStore.fetchWeek(start)
  if (!res.ok) {
    loadError.value = res.message || '统计加载失败'
    toast.error(loadError.value)
    return
  }
  weekStart.value = res.data.rangeStart
}

async function loadRecent30() {
  loadError.value = ''
  reviewStore.setPollingTimedOut(false)
  const res = await reviewStore.fetchRecent30()
  if (!res.ok) {
    loadError.value = res.message || '统计加载失败'
    toast.error(loadError.value)
  }
}

function navigateToWeek(start) {
  if (!start || start === weekStart.value) {
    return
  }
  stopPolling()
  loadWeek(start)
  router.replace({ query: { ...route.query, start } })
}

function switchMode(next) {
  if (mode.value === next) {
    return
  }
  stopPolling()
  reviewStore.setPollingTimedOut(false)
  mode.value = next
  loadError.value = ''
  if (next === 'recent30' && !recent30Stats.value) {
    loadRecent30()
  }
}

function goPreviousWeek() {
  if (!weekStart.value) {
    return
  }
  navigateToWeek(shiftWeek(weekStart.value, -1))
}

function goNextWeek() {
  if (!canNextWeek.value) {
    return
  }
  navigateToWeek(shiftWeek(weekStart.value, 1))
}

function retryLoad() {
  if (mode.value === 'week') {
    loadWeek(weekStart.value)
    return
  }
  loadRecent30()
}

async function handleGenerate(regenerate = false) {
  const res =
    mode.value === 'week'
      ? await reviewStore.generateWeek(weekStart.value, { regenerate })
      : await reviewStore.generateRecent30()

  if (!res.ok) {
    if (!res.skipped && !res.stale) {
      toast.error(res.userMessage || '生成失败，请稍后再试')
    }
    return
  }

  if (mode.value === 'week' && !res.stale) {
    reviewStore.fetchHistory(1).catch(() => {})
  }
}

async function handleJobRetry() {
  const jobId = activeStats.value?.failed?.jobId
  if (!jobId) {
    return
  }
  const res = await reviewStore.retryJob(jobId, mode.value, weekStart.value)
  if (!res.ok && !res.skipped && !res.stale) {
    toast.error(res.userMessage || '再试一次没成功')
  }
}

function handleTimeoutRefresh() {
  retryLoad()
}

function handleOpenRecord(recordId) {
  if (!recordId) {
    return
  }
  router.push({ path: '/home', query: { record: String(recordId) } })
}

function handleHistoryMore() {
  if (historyLoading.value || !historyHasMore.value) {
    return
  }
  reviewStore.fetchHistory(historyPage.value + 1).catch(() => {})
}
</script>

<template>
  <AppShell>
    <div class="container">
      <div class="review">
        <header class="review__head">
          <h1>{{ pageTitle }}</h1>
          <p v-if="subtitle">{{ subtitle }}</p>
        </header>

        <div class="review__row">
          <div class="review__seg" role="tablist" aria-label="回顾范围">
            <button
              type="button"
              role="tab"
              :class="{ 'is-active': mode === 'week' }"
              :aria-selected="mode === 'week'"
              @click="switchMode('week')"
            >
              周报
            </button>
            <button
              type="button"
              role="tab"
              :class="{ 'is-active': mode === 'recent30' }"
              :aria-selected="mode === 'recent30'"
              @click="switchMode('recent30')"
            >
              近 30 天
            </button>
          </div>

          <div v-if="mode === 'week'" class="review__weeknav">
            <button
              type="button"
              class="review__weeknav-btn"
              :disabled="weekLoading || !weekStart"
              @click="goPreviousWeek"
            >
              ‹ 上一周
            </button>
            <button
              type="button"
              class="review__weeknav-btn"
              :disabled="weekLoading || !canNextWeek"
              @click="goNextWeek"
            >
              下一周 ›
            </button>
          </div>
        </div>

        <div v-if="activeLoading && !activeStats" class="review__layout">
          <article class="review-stub">
            <SkeletonCard rows="3" />
          </article>
          <aside class="review__side">
            <SkeletonCard rows="2" />
            <SkeletonCard rows="2" />
          </aside>
        </div>

        <div v-else-if="loadError && !activeStats" class="review__layout">
          <article class="review-card review-error">
            <p>{{ loadError }}</p>
            <button type="button" class="review-btn review-btn--secondary" @click="retryLoad">
              重试
            </button>
          </article>
          <aside class="review__side" />
        </div>

        <div v-else class="review__layout">
          <section class="review__main">
            <ReviewLetter
              :view-state="viewState"
              :mode="mode"
              :page-data="activeStats"
              :submitting="submitting"
              @generate="handleGenerate(false)"
              @regenerate="handleGenerate(true)"
              @retry="handleJobRetry"
              @refresh="handleTimeoutRefresh"
              @open-record="handleOpenRecord"
            />

            <ReviewSourceCards :sources="visibleSources" />

            <ReviewHistory
              v-if="mode === 'week'"
              :items="historyItems"
              :active-start="weekStart"
              :has-more="historyHasMore"
              :loading="historyLoading"
              @select="navigateToWeek"
              @load-more="handleHistoryMore"
            />
          </section>

          <ReviewStatsSidebar
            :mode="mode"
            :week-stats="weekStats"
            :recent30-stats="recent30Stats"
            :today-key="todayKey"
          />
        </div>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.review {
  display: flex;
  flex-direction: column;
  gap: 18px;
  width: 100%;
  max-width: 1000px;
  margin: 0 auto;
}

.review__head h1 {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
}

.review__head p {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-md);
}

.review__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.review__seg {
  display: inline-flex;
  gap: 4px;
  padding: 4px;
  border: 1px solid var(--line);
  border-radius: 14px;
  background: var(--bg-card);
}

.review__seg button {
  padding: 7px 14px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: var(--ink-2);
  font-family: inherit;
  font-size: var(--fs-sm);
  font-weight: 700;
}

.review__seg button.is-active {
  background: var(--brand-deep);
  color: var(--bg-card);
}

.review__seg button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.review__weeknav {
  display: flex;
  gap: 8px;
}

.review__weeknav-btn {
  padding: 4px 6px;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-family: inherit;
  font-size: var(--fs-sm);
  font-weight: 600;
}

.review__weeknav-btn:not(:disabled):hover {
  color: var(--brand-deep);
}

.review__weeknav-btn:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.review__weeknav-btn:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.review__layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 20px;
  align-items: start;
}

.review__main {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-width: 0;
}

.review__side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

/* 空白信纸：虚线边 + 横线，只承载“还没生成”状态。 */
/* 空白信纸：横线只画在上下留白带里，内容所在区域不画线，避免横线穿过文字和按钮。 */
.review-stub {
  --stub-line-band: 60px;
  position: relative;
  display: flex;
  align-items: center;
  min-height: 320px;
  padding: var(--stub-line-band) 28px;
  overflow: hidden;
  border: 1px dashed var(--line-strong);
  border-radius: 22px;
  background-color: var(--bg-card);
}

.review-stub::before,
.review-stub::after {
  content: '';
  position: absolute;
  right: 0;
  left: 0;
  height: var(--stub-line-band);
  background-image: repeating-linear-gradient(
    transparent 0 33px,
    var(--paper-line) 33px 34px
  );
  pointer-events: none;
}

.review-stub::before {
  top: 0;
}

.review-stub::after {
  bottom: 0;
}

.review-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 38px;
  padding: 0 16px;
  border: 1px solid transparent;
  border-radius: 12px;
  font-family: inherit;
  font-size: var(--fs-sm);
  font-weight: 700;
  white-space: nowrap;
  cursor: pointer;
}

.review-btn--secondary {
  border-color: var(--line-strong);
  background: var(--bg-card);
  color: var(--ink-2);
}

.review-btn:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.review-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.review-error {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 12px;
  color: var(--ink-2);
}

.review-error p {
  margin: 0;
}

@media (max-width: 900px) {
  .review__layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .review-stub {
    --stub-line-band: 44px;
    min-height: 220px;
    padding: var(--stub-line-band) 18px;
  }
}

@media (max-width: 768px) {
  .review__head h1 {
    font-size: var(--fs-2xl);
  }
}
</style>
