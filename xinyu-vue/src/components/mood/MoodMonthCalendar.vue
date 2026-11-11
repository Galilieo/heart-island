<script setup>
// 本月心情月历：周一开头的 7 列网格；有记录的日子按心情配色，今天加品牌色内圈。
// 数据由 store 按月查询后聚合为「yyyy-mm-dd → 心情」，本组件只负责展示。
import { computed } from 'vue'
import { moodTone } from '../../utils/mood'

const props = defineProps({
  // 年份与月份（1～12）；跨年时由调用方直接传入新值
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  // { 'yyyy-mm-dd': moodType }，同一天只保留当天最后一条记录的心情
  days: { type: Object, default: () => ({}) },
  // 今天的 yyyy-mm-dd（本地时区），用于高亮和淡化未来日期
  today: { type: String, default: '' }
})

const WEEKDAY_HEADERS = ['一', '二', '三', '四', '五', '六', '日']

const monthLabel = computed(() => `${props.month} 月`)

function pad(value) {
  return String(value).padStart(2, '0')
}

function dateKey(day) {
  return `${props.year}-${pad(props.month)}-${pad(day)}`
}

// 周一开头：JS 的 getDay() 周日为 0，换成周一为 0 后补前置空格。
const leadingBlanks = computed(() => (new Date(props.year, props.month - 1, 1).getDay() + 6) % 7)

const blankCells = computed(() => {
  return Array.from({ length: leadingBlanks.value }, (_, index) => ({ key: `blank-${index}` }))
})

const dayCells = computed(() => {
  const daysInMonth = new Date(props.year, props.month, 0).getDate()
  return Array.from({ length: daysInMonth }, (_, index) => {
    const day = index + 1
    const date = dateKey(day)
    return {
      key: `day-${day}`,
      day,
      date,
      moodType: props.days[date] || '',
      isToday: date === props.today,
      isFuture: Boolean(props.today) && date > props.today
    }
  })
})

function dayLabel(cell) {
  return `${cell.day} 日：${cell.moodType || '无记录'}`
}
</script>

<template>
  <section class="month">
    <h3 class="month__title">{{ monthLabel }}</h3>

    <div class="month__grid" role="group" aria-label="本月心情日历">
      <span v-for="name in WEEKDAY_HEADERS" :key="name" class="month__head">{{ name }}</span>
      <span v-for="blank in blankCells" :key="blank.key" class="month__blank" aria-hidden="true" />
      <span
        v-for="cell in dayCells"
        :key="cell.key"
        class="month__cell"
        :class="[
          cell.moodType ? `tone--${moodTone(cell.moodType)}` : '',
          { 'has-mood': cell.moodType, 'is-today': cell.isToday, 'is-future': cell.isFuture }
        ]"
        :title="dayLabel(cell)"
        :aria-label="dayLabel(cell)"
      >{{ cell.day }}</span>
    </div>
  </section>
</template>

<style scoped>
.month__title {
  margin: 0 0 12px;
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  font-weight: 400;
}

/* 宽屏侧栏内本来就窄（约 264px）；单片单列时限制最大宽度，
   避免 7 列圆格在整栏宽度下被拉成过大的日历。 */
.month {
  max-width: 340px;
  margin: 0 auto;
}

.month__grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 4px;
  text-align: center;
  font-size: var(--fs-xs);
}

.month__head {
  color: var(--ink-3);
}

.month__cell {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border-radius: 50%;
  color: var(--ink-2);
}

.month__cell.has-mood {
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

.month__cell.is-today {
  box-shadow: inset 0 0 0 2px var(--brand);
}

/* 未来日期不淡化有记录的日子，无记录时用更浅的线条色；有记录的日子本就不会是未来。 */
.month__cell.is-future {
  color: var(--line-strong);
}

/* 心情分组 → tokens.css 的柔底与文字色；格子背景与文字统一读 var(--tone-soft) / var(--tone-ink)。 */
.tone--joy { --tone-soft: var(--joy-soft); --tone-ink: var(--joy-ink); }
.tone--calm { --tone-soft: var(--calm-soft); --tone-ink: var(--calm-ink); }
.tone--tension { --tone-soft: var(--tension-soft); --tone-ink: var(--tension-ink); }
.tone--sad { --tone-soft: var(--sad-soft); --tone-ink: var(--sad-ink); }
.tone--tired { --tone-soft: var(--tired-soft); --tone-ink: var(--tired-ink); }
.tone--neutral { --tone-soft: var(--brand-soft); --tone-ink: var(--brand-deep); }
</style>
