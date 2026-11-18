<script setup>
// 回顾页右侧统计栏：周报展示周历格与心情次数条，近 30 天展示次数条与「按周看」圆点。
import { computed } from 'vue'
import WeekGrid from './WeekGrid.vue'
import MoodCountBars from './MoodCountBars.vue'
import { moodTone } from '../../utils/mood'
import { formatRangeShort, groupDaysIntoWeeks } from '../../utils/review'

const props = defineProps({
  mode: { type: String, required: true },
  weekStats: { type: Object, default: null },
  recent30Stats: { type: Object, default: null },
  todayKey: { type: String, required: true }
})

const activeStats = computed(() => (props.mode === 'week' ? props.weekStats : props.recent30Stats))
const hasRecords = computed(() => (activeStats.value?.total || 0) > 0)
const moodCounts = computed(() => activeStats.value?.moodCounts || [])

const weekGroups = computed(() =>
  props.recent30Stats ? groupDaysIntoWeeks(props.recent30Stats.days, props.todayKey) : []
)

const weekRangeShort = computed(() =>
  props.weekStats ? formatRangeShort(props.weekStats.rangeStart, props.weekStats.rangeEnd) : ''
)
const recentRangeShort = computed(() =>
  props.recent30Stats
    ? formatRangeShort(props.recent30Stats.rangeStart, props.recent30Stats.rangeEnd)
    : ''
)

// 「按周看」把每组的心情摊平成圆点；只做展示，不据此判断哪种最多。
function groupMoods(week) {
  return week.days.flatMap((day) => day.moods || [])
}
</script>

<template>
  <aside class="review__side">
    <template v-if="mode === 'week' && weekStats">
      <section class="review-card">
        <div class="review-card__head">
          <h2>这一周</h2>
          <span>{{ weekRangeShort }}</span>
        </div>
        <div class="review-stat">
          <div>
            <strong>{{ weekStats.total }}<small>条</small></strong>
            <span>记录</span>
          </div>
          <div>
            <strong>{{ weekStats.daysWithRecords }}<small>/ 7 天</small></strong>
            <span>有记录</span>
          </div>
        </div>
        <WeekGrid :start-key="weekStats.rangeStart" :days="weekStats.days" :today-key="todayKey" />
      </section>

      <section class="review-card">
        <div class="review-card__head">
          <h2>心情出现的次数</h2>
        </div>
        <MoodCountBars v-if="moodCounts.length" :items="moodCounts" :limit="4" />
        <p v-else class="review-card__empty">没有记录</p>
      </section>
    </template>

    <template v-else-if="mode === 'recent30' && recent30Stats">
      <section class="review-card">
        <div class="review-card__head">
          <h2>这 30 天</h2>
          <span>{{ recentRangeShort }}</span>
        </div>
        <div class="review-stat">
          <div>
            <strong>{{ recent30Stats.total }}<small>条</small></strong>
            <span>记录</span>
          </div>
          <div>
            <strong>{{ recent30Stats.daysWithRecords }}<small>/ 30 天</small></strong>
            <span>有记录</span>
          </div>
        </div>
        <MoodCountBars v-if="moodCounts.length" :items="moodCounts" :limit="4" />
        <p v-else class="review-card__empty">没有记录</p>
      </section>

      <section v-if="hasRecords" class="review-card">
        <div class="review-card__head">
          <h2>按周看</h2>
        </div>
        <div class="review-weeks">
          <div v-for="week in weekGroups" :key="week.startKey" class="review-weeks__row">
            <strong class="review-weeks__label">{{ week.label }}</strong>
            <span class="review-weeks__dots" aria-hidden="true">
              <i
                v-for="(mood, index) in groupMoods(week)"
                :key="index"
                :class="`tone--${moodTone(mood)}`"
              />
            </span>
            <span class="review-weeks__count">{{ week.total }} 条</span>
          </div>
        </div>
      </section>
    </template>
  </aside>
</template>

<style scoped>
.review__side {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.review-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: 18px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.review-card h2 {
  margin: 0;
  color: var(--ink-2);
  font-family: var(--font-sans);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0;
}

.review-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.review-card__head span {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.review-card__empty {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.review-stat {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-bottom: 14px;
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 12px;
}

.review-stat > div {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 12px;
}

.review-stat > div + div {
  border-left: 1px solid var(--line);
}

.review-stat strong {
  color: var(--ink-1);
  font-size: 22px;
  font-weight: 700;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.review-stat strong small {
  margin-left: 3px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.review-stat span {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

/* 近 30 天「按周看」：一周一行，心情圆点摊平展示。 */
.review-weeks {
  display: flex;
  flex-direction: column;
}

.review-weeks__row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 0;
  border-bottom: 1px solid var(--line);
  font-size: var(--fs-sm);
}

.review-weeks__row:last-child {
  border-bottom: none;
}

.review-weeks__label {
  flex: none;
  width: 118px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 600;
}

.review-weeks__dots {
  display: flex;
  flex: 1;
  flex-wrap: wrap;
  gap: 3px;
  min-width: 0;
}

.review-weeks__dots i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tone);
}

.review-weeks__count {
  flex: none;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  font-variant-numeric: tabular-nums;
}

.tone--joy {
  --tone: var(--joy);
}
.tone--calm {
  --tone: var(--calm);
}
.tone--tension {
  --tone: var(--tension);
}
.tone--sad {
  --tone: var(--sad);
}
.tone--tired {
  --tone: var(--tired);
}
.tone--neutral {
  --tone: var(--brand);
}
</style>
