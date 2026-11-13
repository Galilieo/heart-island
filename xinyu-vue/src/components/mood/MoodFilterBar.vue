<script setup>
// 心情列表筛选栏：常驻的时间范围切换 + 可折叠的「更多筛选」（心情与起止日期）。
// 自身不发请求，只通过 v-model + @apply/@reset 把意图给父级。
import { computed, nextTick, ref } from 'vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseDateInput from '../ui/BaseDateInput.vue'
import BaseButton from '../ui/BaseButton.vue'
import { MOOD_OPTIONS } from '../../utils/mood'

const props = defineProps({
  moodType: { type: String, default: '' },
  startDate: { type: String, default: '' },
  endDate: { type: String, default: '' }
})

const emit = defineEmits([
  'update:moodType',
  'update:startDate',
  'update:endDate',
  'apply',
  'reset'
])

const moodSelectOptions = [
  { label: '全部心情', value: '' },
  ...MOOD_OPTIONS
]

const hasFilter = computed(
  () => props.moodType || props.startDate || props.endDate
)

// 常驻范围按钮：全部沿用现有 reset 语义，其余按天数换算起止日期
const RANGE_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'today', label: '今天', days: 1 },
  { value: 'week', label: '近 7 天', days: 7 },
  { value: 'month', label: '近 30 天', days: 30 }
]

// 「更多筛选」默认收起；进入页面时已有心情或日期筛选则展开
const moreOpen = ref(Boolean(props.moodType || props.startDate || props.endDate))

const startDateInputRef = ref(null)
const endDateInputRef = ref(null)

function handleDatePickerOpen(activePicker) {
  if (activePicker === 'start') endDateInputRef.value?.closePicker()
  if (activePicker === 'end') startDateInputRef.value?.closePicker()
}

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

// 最近 days 天（含今天）的起始日期；选中态比对与按钮筛选共用同一算法
function recentStartDate(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days + 1)
  return formatDate(start)
}

// 当前高亮的范围由起止日期反推；手填日期不匹配任何预设时不高亮
const activeRange = computed(() => {
  const hasNoDateRange = !props.startDate && !props.endDate
  if (hasNoDateRange) return 'all'

  const today = formatDate(new Date())
  if (props.endDate !== today) return ''
  if (props.startDate === today) return 'today'
  if (props.startDate === recentStartDate(7)) return 'week'
  if (props.startDate === recentStartDate(30)) return 'month'
  return ''
})

// 折叠区里还有预设范围以外的筛选（心情或自定义日期）时，在按钮上点一个小圆点提示
const hasExtraFilter = computed(
  () => Boolean(props.moodType) || activeRange.value === ''
)

async function applyRecent(days) {
  emit('update:startDate', recentStartDate(days))
  emit('update:endDate', formatDate(new Date()))
  await nextTick()
  emit('apply')
}

async function applyToday() {
  const today = formatDate(new Date())
  emit('update:startDate', today)
  emit('update:endDate', today)
  await nextTick()
  emit('apply')
}

async function selectRange(range) {
  if (range.value === 'all') {
    emit('reset')
    return
  }

  if (range.value === 'today') {
    await applyToday()
    return
  }

  await applyRecent(range.days)
}
</script>

<template>
  <div class="filter-bar">
    <div class="filter-bar__ranges" role="group" aria-label="记录时间范围">
      <button
        v-for="range in RANGE_OPTIONS"
        :key="range.value"
        type="button"
        class="filter-bar__range"
        :class="{ 'is-on': activeRange === range.value }"
        @click="selectRange(range)"
      >{{ range.label }}</button>
    </div>

    <button
      type="button"
      class="filter-bar__more"
      aria-controls="mood-filter-more"
      :aria-expanded="moreOpen"
      @click="moreOpen = !moreOpen"
    >
      <span>更多筛选</span>
      <span v-if="hasExtraFilter" class="filter-bar__dot" aria-hidden="true" />
      <span class="filter-bar__caret" aria-hidden="true">{{ moreOpen ? '收起 ▴' : '展开 ▾' }}</span>
    </button>

    <div v-show="moreOpen" id="mood-filter-more" class="filter-bar__body">
      <div class="filter-bar__field filter-bar__field--mood">
        <BaseSelect
          :model-value="moodType"
          :options="moodSelectOptions"
          placeholder="全部心情"
          size="sm"
          @update:model-value="(v) => emit('update:moodType', v)"
        />
      </div>

      <div class="filter-bar__dates">
        <BaseDateInput
          ref="startDateInputRef"
          :model-value="startDate"
          class="filter-bar__date"
          placeholder="开始日期"
          size="sm"
          @open="handleDatePickerOpen('start')"
          @update:model-value="(v) => emit('update:startDate', v)"
        />
        <span class="filter-bar__sep">至</span>
        <BaseDateInput
          ref="endDateInputRef"
          :model-value="endDate"
          class="filter-bar__date"
          placeholder="结束日期"
          picker-align="end"
          size="sm"
          @open="handleDatePickerOpen('end')"
          @update:model-value="(v) => emit('update:endDate', v)"
        />
      </div>

      <div class="filter-bar__actions">
        <BaseButton variant="primary" size="sm" @click="emit('apply')">筛选</BaseButton>
        <BaseButton
          variant="plain"
          size="sm"
          :disabled="!hasFilter"
          @click="emit('reset')"
        >重置</BaseButton>
      </div>
    </div>
  </div>
</template>

<style scoped>
.filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
}

/* ── 常驻范围切换：圆角分段按钮 ── */
.filter-bar__ranges {
  display: flex;
  gap: 4px;
}

.filter-bar__range {
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.filter-bar__range:not(.is-on):hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.filter-bar__range.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.filter-bar__more {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin-left: auto;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.filter-bar__more:hover {
  border-color: var(--line-strong);
  color: var(--ink-1);
}

/* 折叠区内另有心情 / 自定义日期筛选时的提示点 */
.filter-bar__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
}

.filter-bar__caret {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.filter-bar__body {
  display: flex;
  flex: 1 1 100%;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-soft);
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-3);
}

.filter-bar__field--mood {
  flex: 0 1 180px;
}

.filter-bar__dates {
  flex: 1 1 330px;
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.filter-bar__date {
  flex: 1;
  min-width: 112px;
}

.filter-bar__date :deep(.date-field__control) {
  padding-right: 10px;
}

.filter-bar__sep {
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.filter-bar__actions {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  margin-left: auto;
}

@media (max-width: 1024px) {
  .filter-bar__field--mood { flex: 0 0 170px; }
  .filter-bar__dates { flex: 1 1 calc(100% - 182px); }
}

@media (max-width: 768px) {
  .filter-bar__field--mood,
  .filter-bar__dates {
    flex: 1 1 100%;
  }

  .filter-bar__dates {
    align-items: stretch;
  }

  .filter-bar__date {
    min-width: 0;
  }

  .filter-bar__actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}

@media (max-width: 480px) {
  .filter-bar__dates {
    flex-direction: column;
    gap: 8px;
  }

  .filter-bar__sep {
    align-self: center;
    line-height: 1;
  }
}
</style>
