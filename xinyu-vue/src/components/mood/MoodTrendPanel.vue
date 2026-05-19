<script setup>
// 7 天情绪小图。顶部 3 个统计块（总数 / 主旋律 / 最近一次），
// 下方按心情维度展示横向条形，宽度按当前最大计数等比缩放。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTag from '../ui/BaseTag.vue'
import Doodle from '../ui/Doodle.vue'
import { moodTone, formatTime, MOOD_OPTIONS } from '../../utils/mood'

const props = defineProps({
  trend: {
    type: Object,
    required: true
  }
})

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
</script>

<template>
  <BaseCard padding="md" tone="cream" class="trend">
    <header class="trend__head">
      <div>
        <p class="trend__kicker">最近 7 天</p>
        <h3 class="trend__title">情绪小记</h3>
      </div>
      <Doodle name="wave" :size="40" color="var(--brand)" />
    </header>

    <div v-if="hasData" class="trend__stats">
      <div class="trend__stat">
        <span class="trend__stat-label">记录</span>
        <strong>{{ trend.total }} <small>次</small></strong>
      </div>
      <div class="trend__stat">
        <span class="trend__stat-label">主旋律</span>
        <BaseTag
          v-if="trend.mainMoodType"
          :tone="moodTone(trend.mainMoodType)"
          variant="soft"
          size="md"
        >{{ trend.mainMoodType }}</BaseTag>
        <strong v-else>—</strong>
      </div>
      <div class="trend__stat">
        <span class="trend__stat-label">最近一次</span>
        <BaseTag
          v-if="trend.lastMoodType"
          :tone="moodTone(trend.lastMoodType)"
          variant="outline"
          size="md"
        >{{ trend.lastMoodType }}</BaseTag>
        <strong v-else>—</strong>
        <span v-if="trend.lastRecordTime" class="trend__stat-sub">
          {{ formatTime(trend.lastRecordTime) }}
        </span>
      </div>
    </div>

    <div v-if="hasData" class="trend__bars">
      <div
        v-for="row in orderedCounts"
        :key="row.moodType"
        class="trend__row"
      >
        <span class="trend__row-label">{{ row.moodType }}</span>
        <div class="trend__bar-track">
          <div
            class="trend__bar"
            :class="`trend__bar--${moodTone(row.moodType)}`"
            :style="{ width: `${(row.count / maxCount) * 100}%` }"
          />
        </div>
        <span class="trend__row-count">{{ row.count }}</span>
      </div>
    </div>

    <p v-else class="trend__empty">
      还没有记录，写下第一段心情，这里就会有你的小图。
    </p>
  </BaseCard>
</template>

<style scoped>
.trend__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.trend__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.trend__title {
  margin: 4px 0 0;
  font-size: var(--fs-xl);
}

.trend__stats {
  margin-top: 16px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.trend__stat {
  padding: 12px;
  background: var(--bg-card);
  border-radius: var(--radius-2);
  border: 1px solid var(--line);
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.trend__stat-label {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  letter-spacing: 1px;
}

.trend__stat strong {
  font-size: var(--fs-xl);
  color: var(--ink-1);
  font-family: var(--font-display);
  display: inline-flex;
  align-items: baseline;
  gap: 2px;
}

.trend__stat strong small {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  font-weight: 400;
  margin-left: 2px;
}

.trend__stat-sub {
  font-size: 11px;
  color: var(--ink-3);
}

.trend__bars {
  margin-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.trend__row {
  display: grid;
  grid-template-columns: 48px 1fr 32px;
  gap: 10px;
  align-items: center;
}

.trend__row-label {
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.trend__bar-track {
  height: 8px;
  background: var(--bg-card);
  border-radius: var(--radius-pill);
  overflow: hidden;
}

.trend__bar {
  height: 100%;
  border-radius: var(--radius-pill);
  transition: width var(--t-slow) var(--ease-soft);
}

.trend__bar--joy   { background: var(--joy); }
.trend__bar--calm  { background: var(--calm); }
.trend__bar--warn  { background: var(--warn); }
.trend__bar--sad   { background: var(--sad); }
.trend__bar--tired { background: var(--tired); }
.trend__bar--neutral { background: var(--ink-3); }

.trend__row-count {
  text-align: right;
  font-size: var(--fs-sm);
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
}

.trend__empty {
  margin-top: 18px;
  padding: 18px;
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  background: var(--bg-card);
  border-radius: var(--radius-2);
  border: 1px dashed var(--line-strong);
}

@media (max-width: 720px) {
  .trend__stats { grid-template-columns: 1fr 1fr; }
}
</style>
