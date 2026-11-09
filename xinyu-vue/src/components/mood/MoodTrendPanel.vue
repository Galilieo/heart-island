<script setup>
// 7 天情绪潮汐。头部是大数字总量，中部按心情维度做竖向「潮汐刻度」
// （胶囊柱高按当前最大计数等比缩放），底部一行手账式摘要：主旋律 / 最近一次。
import { computed, ref } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTag from '../ui/BaseTag.vue'
import { moodTone, formatTime, MOOD_OPTIONS } from '../../utils/mood'

const props = defineProps({
  trend: {
    type: Object,
    required: true
  }
})

// ≤480px 时潮汐刻度默认收起为摘要（总量 + 主旋律行），展开后才看柱图。
// 纯展示层局部状态，不影响任何数据语义。
const tideOpen = ref(false)

const maxCount = computed(() => {
  const counts = props.trend?.moodCounts || []
  return Math.max(1, ...counts.map((c) => c.count || 0))
})

// 把 moodCounts 按 MOOD_OPTIONS 顺序排
const orderedCounts = computed(() => {
  const map = new Map((props.trend?.moodCounts || []).map((c) => [c.moodType, c.count]))
  return MOOD_OPTIONS.map((opt) => ({
    moodType: opt.value,
    count: map.get(opt.value) || 0
  }))
})

const hasData = computed(() => (props.trend?.total || 0) > 0)

function barHeight(count) {
  if (!count) return '4px'
  return `${Math.max(10, (count / maxCount.value) * 100)}%`
}
</script>

<template>
  <BaseCard padding="md" tone="cream" class="trend">
    <header class="trend__head">
      <div>
        <p class="trend__kicker">最近 7 天</p>
        <h3 class="trend__title">最近的心情统计</h3>
      </div>
      <div v-if="hasData" class="trend__total">
        <strong>{{ trend.total }}</strong>
        <span>次记录</span>
      </div>
    </header>

    <template v-if="hasData">
      <button
        type="button"
        class="trend__toggle"
        :aria-expanded="tideOpen"
        @click="tideOpen = !tideOpen"
      >
        <span>各类心情的记录次数</span>
        <span class="trend__toggle-caret" aria-hidden="true">
          {{ tideOpen ? '收起 ▴' : '展开 ▾' }}
        </span>
      </button>

      <div
        class="trend__tide"
        :class="{ 'is-open': tideOpen }"
        role="img"
        :aria-label="`最近 7 天共 ${trend.total} 次记录：` + orderedCounts.map((r) => `${r.moodType} ${r.count} 次`).join('，')"
      >
        <div v-for="row in orderedCounts" :key="row.moodType" class="trend__col">
          <span class="trend__col-count">{{ row.count }}</span>
          <span class="trend__col-track">
            <span
              class="trend__col-bar"
              :class="[`trend__col-bar--${moodTone(row.moodType)}`, { 'is-zero': !row.count }]"
              :style="{ height: barHeight(row.count) }"
            />
          </span>
          <span class="trend__col-label">{{ row.moodType }}</span>
        </div>
      </div>

      <p class="trend__meta">
        <template v-if="trend.mainMoodType">
          <span class="trend__meta-label">记得最多</span>
          <BaseTag :tone="moodTone(trend.mainMoodType)" variant="soft" size="md">
            {{ trend.mainMoodType }}
          </BaseTag>
        </template>
        <template v-if="trend.lastMoodType">
          <span class="trend__meta-label">最近一次</span>
          <BaseTag :tone="moodTone(trend.lastMoodType)" variant="outline" size="md">
            {{ trend.lastMoodType }}
          </BaseTag>
          <span v-if="trend.lastRecordTime" class="trend__meta-time">
            {{ formatTime(trend.lastRecordTime) }}
          </span>
        </template>
      </p>
    </template>

    <p v-else class="trend__empty">
      最近 7 天还没有记录。记下心情后，可以在这里查看统计。
    </p>
  </BaseCard>
</template>

<style scoped>
.trend__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.trend__kicker {
  margin: 0;
  color: var(--sea-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.trend__title {
  margin: 4px 0 0;
  font-size: var(--fs-xl);
}

.trend__total {
  display: flex;
  align-items: baseline;
  gap: 4px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.trend__total strong {
  font-family: var(--font-display);
  font-size: 34px;
  line-height: 1;
  color: var(--sea-deep);
}

/* ── 潮汐刻度折叠开关：仅 ≤480px 出现 ── */
.trend__toggle {
  display: none;
}

/* ── 潮汐刻度：竖向胶囊柱 ── */
.trend__tide {
  margin-top: 18px;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 16px 6px;
}

.trend__col {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.trend__col-count {
  font-size: var(--fs-xs);
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.trend__col-track {
  height: 72px;
  width: 16px;
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  box-shadow: inset 0 0 0 1px var(--line);
  display: flex;
  align-items: flex-end;
  overflow: hidden;
}

.trend__col-bar {
  width: 100%;
  border-radius: var(--radius-pill);
  transition: height var(--t-slow) var(--ease-soft);
}

.trend__col-bar--joy     { background: var(--joy); }
.trend__col-bar--calm    { background: var(--calm); }
.trend__col-bar--warn    { background: var(--warn); }
.trend__col-bar--sad     { background: var(--sad); }
.trend__col-bar--tired   { background: var(--tired); }
.trend__col-bar--neutral { background: var(--ink-3); }
.trend__col-bar.is-zero  { opacity: 0.25; }

.trend__col-label {
  font-size: var(--fs-xs);
  color: var(--ink-2);
  line-height: 1;
}

/* ── 手账式摘要行 ── */
.trend__meta {
  margin: 16px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--line-strong);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px 8px;
}

.trend__meta-label {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  letter-spacing: 1px;
}

.trend__meta-label:not(:first-child) {
  margin-left: 10px;
}

.trend__meta-time {
  color: var(--ink-3);
  font-size: 11px;
}

.trend__empty {
  margin: 18px 0 0;
  padding: 18px;
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  background: var(--bg-card);
  border-radius: var(--radius-2);
  border: 1px dashed var(--line-strong);
}

@media (max-width: 480px) {
  .trend__toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 40px;
    margin-top: 14px;
    padding: 0 12px;
    border: 1px dashed var(--line-strong);
    border-radius: var(--radius-2);
    background: var(--bg-card);
    color: var(--ink-2);
    font-size: var(--fs-xs);
    font-weight: 700;
    letter-spacing: 1px;
    cursor: pointer;
  }

  .trend__toggle-caret {
    color: var(--ink-3);
    font-weight: 600;
  }

  .trend__tide {
    display: none;
  }

  .trend__tide.is-open {
    display: grid;
    gap: 14px 4px;
  }

  .trend__col-track { width: 14px; height: 64px; }
  .trend__col-label { font-size: 11px; }
}
</style>
