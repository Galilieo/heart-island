<script setup>
// 心情列表筛选栏：心情类型 + 起止日期。
// 自身不发请求，只通过 v-model + @apply/@reset 把意图给父级。
import { computed, nextTick } from 'vue'
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

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

async function applyRecent(days) {
  const end = new Date()
  const start = new Date()
  start.setDate(end.getDate() - days + 1)
  emit('update:startDate', formatDate(start))
  emit('update:endDate', formatDate(end))
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
</script>

<template>
  <div class="filter-bar">
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
        :model-value="startDate"
        class="filter-bar__date"
        placeholder="开始日期"
        size="sm"
        @update:model-value="(v) => emit('update:startDate', v)"
      />
      <span class="filter-bar__sep">至</span>
      <BaseDateInput
        :model-value="endDate"
        class="filter-bar__date"
        placeholder="结束日期"
        size="sm"
        @update:model-value="(v) => emit('update:endDate', v)"
      />
    </div>

    <div class="filter-bar__quick">
      <button type="button" class="motion-press" @click="applyToday">今天</button>
      <button type="button" class="motion-press" @click="applyRecent(7)">近7天</button>
      <button type="button" class="motion-press" @click="applyRecent(30)">近30天</button>
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
</template>

<style scoped>
.filter-bar {
  display: flex;
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

.filter-bar__quick {
  flex: 0 0 auto;
  display: flex;
  gap: 6px;
  padding: 3px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.62);
}

.filter-bar__quick button {
  height: 28px;
  padding: 0 10px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  cursor: pointer;
  font: inherit;
  font-size: var(--fs-xs);
  font-weight: 700;
  white-space: nowrap;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft),
    transform var(--t-fast) var(--ease-soft);
}

.filter-bar__quick button:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.filter-bar__actions {
  flex: 0 0 auto;
  display: flex;
  gap: 8px;
  margin-left: auto;
}

@media (max-width: 1024px) {
  .filter-bar__field--mood { flex-basis: 170px; }
  .filter-bar__dates { flex-basis: 360px; }
  .filter-bar__quick {
    order: 3;
  }
  .filter-bar__actions {
    order: 4;
    justify-content: flex-end;
  }
}

@media (max-width: 768px) {
  .filter-bar { align-items: stretch; }
  .filter-bar__field--mood,
  .filter-bar__dates,
  .filter-bar__quick,
  .filter-bar__actions {
    flex: 1 1 100%;
  }
  .filter-bar__dates {
    align-items: stretch;
  }
  .filter-bar__date {
    min-width: 0;
  }
  .filter-bar__quick {
    justify-content: space-between;
    border-radius: var(--radius-2);
  }
  .filter-bar__quick button {
    flex: 1;
  }
  .filter-bar__actions {
    margin-left: 0;
    justify-content: flex-end;
  }
}
</style>
