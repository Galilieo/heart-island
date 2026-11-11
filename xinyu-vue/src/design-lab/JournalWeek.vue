<script setup>
// 手账风格的一周摘要：主旋律表情 + 一句话 + 按比例分色的长条。
import Xiaoya from '../components/ui/Xiaoya.vue'
import { moodExpression } from '../utils/mood'
import { MOCK_WEEK, MOCK_WEEK_TOTAL, toneStyle } from './mock'

defineProps({
  // 放在已有“这一周”标题的窄侧栏里时，句子省略主语并缩小表情
  compact: Boolean
})

const topMood = MOCK_WEEK[0]

function segmentStyle(item) {
  return {
    ...toneStyle(item.moodType),
    width: `${(item.count / MOCK_WEEK_TOTAL) * 100}%`
  }
}
</script>

<template>
  <section class="week">
    <Xiaoya :expression="moodExpression(topMood.moodType)" :size="compact ? 44 : 56" />
    <div class="week__body">
      <p v-if="compact" class="week__text">
        共 <b>{{ MOCK_WEEK_TOTAL }}</b> 次，「{{ topMood.moodType }}」最多
      </p>
      <p v-else class="week__text">
        这一周记了 <b>{{ MOCK_WEEK_TOTAL }}</b> 次，「{{ topMood.moodType }}」最多
      </p>
      <div class="week__bar" aria-hidden="true">
        <span v-for="item in MOCK_WEEK" :key="item.moodType" :style="segmentStyle(item)" />
      </div>
      <ul class="week__legend">
        <li v-for="item in MOCK_WEEK" :key="item.moodType" :style="toneStyle(item.moodType)">
          {{ item.moodType }} {{ item.count }}
        </li>
      </ul>
    </div>
  </section>
</template>

<style scoped>
.week {
  display: flex;
  align-items: center;
  gap: 16px;
}

.week__body { flex: 1; min-width: 0; }

.week__text b {
  font-family: var(--font-display);
  font-size: var(--fs-lg);
}

.week__bar {
  display: flex;
  height: 10px;
  margin-top: 10px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
}

.week__bar span { background: var(--tone); }

.week__legend {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  color: var(--ink-2);
  font-size: var(--fs-xs);
}

.week__legend li::before {
  content: "";
  display: inline-block;
  width: 8px;
  height: 8px;
  margin-right: 5px;
  border-radius: 50%;
  background: var(--tone);
}
</style>
