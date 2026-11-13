<script setup>
// 侧栏「这一周」摘要：主旋律表情 + 按次数比例的配色长条 + 图例。
// 外层卡片容器（背景、边框、内边距）由首页侧栏提供，本组件只负责卡内内容。
import { computed } from 'vue'
import { RouterLink } from 'vue-router'
import Xiaoya from '../ui/Xiaoya.vue'
import { moodExpression, moodTone } from '../../utils/mood'

const props = defineProps({
  trend: {
    type: Object,
    required: true
  }
})

const hasData = computed(() => (props.trend?.total || 0) > 0)

const total = computed(() => props.trend?.total || 0)

// 只统计出现过的心情，按次数从多到少；长条宽度按各类占总次数的比例计算。
const segments = computed(() => {
  const counts = props.trend?.moodCounts || []
  return counts
    .filter(item => (item.count || 0) > 0)
    .sort((left, right) => right.count - left.count)
})

// 主旋律以接口返回的 mainMoodType 为准，缺失时退回次数最多的一项。
const topMoodType = computed(() => props.trend?.mainMoodType || segments.value[0]?.moodType || '')

function segmentWidth(count) {
  if (!total.value) {
    return '0%'
  }
  return `${(count / total.value) * 100}%`
}

// 长条是视觉表达，汇总信息通过 aria-label 提供给读屏；图例文字本身可读。
const barLabel = computed(() => {
  const parts = segments.value.map(item => `${item.moodType} ${item.count} 次`)
  return `最近 7 天共 ${total.value} 次记录：${parts.join('，')}`
})
</script>

<template>
  <section class="week">
    <h3 class="week__title">这一周</h3>

    <template v-if="hasData">
      <div class="week__summary">
        <Xiaoya :expression="moodExpression(topMoodType)" :size="44" />
        <p class="week__text">
          共 <b>{{ total }}</b> 次，「{{ topMoodType }}」最多
        </p>
      </div>

      <div class="week__bar" role="img" :aria-label="barLabel">
        <span
          v-for="item in segments"
          :key="item.moodType"
          class="week__bar-segment"
          :class="`tone--${moodTone(item.moodType)}`"
          :style="{ width: segmentWidth(item.count) }"
        />
      </div>

      <ul class="week__legend">
        <li
          v-for="item in segments"
          :key="item.moodType"
          class="week__legend-item"
          :class="`tone--${moodTone(item.moodType)}`"
        >
          <span class="week__legend-dot" aria-hidden="true" />
          <span>{{ item.moodType }}</span>
          <span class="week__legend-count">{{ item.count }}</span>
        </li>
      </ul>
    </template>

    <p v-else class="week__empty">
      最近 7 天还没有记录。记下心情后，可以在这里查看统计。
    </p>

    <RouterLink to="/review" class="week__report-link">看周报 ›</RouterLink>
  </section>
</template>

<style scoped>
.week__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
}

.week__summary {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.week__text {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.week__text b {
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
  color: var(--brand-deep);
}

.week__bar {
  display: flex;
  height: 10px;
  margin-top: 12px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
}

.week__bar-segment {
  height: 100%;
  background: var(--tone);
}

.week__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin: 10px 0 0;
  padding: 0;
  list-style: none;
  color: var(--ink-2);
  font-size: var(--fs-xs);
}

.week__legend-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.week__legend-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tone);
}

.week__legend-count {
  color: var(--ink-3);
  font-variant-numeric: tabular-nums;
}

.week__report-link {
  display: inline-flex;
  margin-top: 12px;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 600;
  text-decoration: none;
}

.week__report-link:hover {
  text-decoration: underline;
}

.week__empty {
  margin: 12px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

/* 心情分组 → tokens.css 的配色变量；长条与图例色点统一读 var(--tone)。 */
.tone--joy { --tone: var(--joy); }
.tone--calm { --tone: var(--calm); }
.tone--tension { --tone: var(--tension); }
.tone--sad { --tone: var(--sad); }
.tone--tired { --tone: var(--tired); }
.tone--neutral { --tone: var(--brand); }
</style>
