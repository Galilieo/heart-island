<script setup>
// 一周七格周历：日期数字在格内，心情圆点在下；没有记录用虚线空心点。
// 只表示有没有记录和心情种类，不表示情绪强弱。
import { computed } from 'vue'
import { buildWeekCells } from '../../utils/review'
import { moodTone } from '../../utils/mood'

const props = defineProps({
  startKey: { type: String, required: true },
  days: { type: Array, default: () => [] },
  todayKey: { type: String, default: '' }
})

const weekdayHeads = ['一', '二', '三', '四', '五', '六', '日']
const cells = computed(() => buildWeekCells(props.startKey, props.days))

function cellLabel(cell) {
  const moodText = cell.moods.length ? cell.moods.join('、') : '没有记录'
  return `${cell.date} 周${cell.weekday}，${cell.moods.length ? `${cell.moods.length} 条：${moodText}` : moodText}`
}
</script>

<template>
  <div class="week-grid" role="list">
    <span v-for="weekday in weekdayHeads" :key="weekday" class="week-grid__head">
      {{ weekday }}
    </span>

    <span
      v-for="cell in cells"
      :key="cell.date"
      class="week-grid__cell"
      :class="{
        'is-today': cell.date === todayKey,
        'is-empty': !cell.moods.length
      }"
      role="listitem"
      :title="cellLabel(cell)"
      :aria-label="cellLabel(cell)"
    >
      <b>{{ cell.day }}</b>
      <span class="week-grid__dots" aria-hidden="true">
        <i
          v-for="(mood, index) in cell.moods.slice(0, 3)"
          :key="index"
          :class="`tone--${moodTone(mood)}`"
        />
        <em v-if="cell.moods.length > 3">+{{ cell.moods.length - 3 }}</em>
        <i v-if="!cell.moods.length" class="is-empty" />
      </span>
    </span>
  </div>
</template>

<style scoped>
.week-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 5px;
}

.week-grid__head {
  padding-bottom: 2px;
  color: var(--ink-3);
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}

.week-grid__cell {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  aspect-ratio: 1;
  border-radius: 10px;
  background: var(--bg-soft);
}

.week-grid__cell b {
  color: var(--ink-1);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  line-height: 1;
}

.week-grid__cell.is-empty b {
  color: var(--ink-3);
}

.week-grid__cell.is-today {
  box-shadow: inset 0 0 0 1.5px var(--brand);
}

.week-grid__dots {
  display: flex;
  align-items: center;
  gap: 2px;
  height: 7px;
}

.week-grid__dots i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--tone);
}

.week-grid__dots i.is-empty {
  box-sizing: border-box;
  background: transparent;
  border: 1px dashed var(--line-strong);
}

.week-grid__dots em {
  color: var(--ink-3);
  font-size: 9px;
  font-style: normal;
  line-height: 1;
}

.tone--joy { --tone: var(--joy); }
.tone--calm { --tone: var(--calm); }
.tone--tension { --tone: var(--tension); }
.tone--sad { --tone: var(--sad); }
.tone--tired { --tone: var(--tired); }
.tone--neutral { --tone: var(--brand); }
</style>
