<script setup>
// 心情列表筛选栏：心情类型 + 起止日期。
// 自身不发请求，只通过 v-model + @apply/@reset 把意图给父级。
import { computed } from 'vue'
import BaseSelect from '../ui/BaseSelect.vue'
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

function onStart(e) {
  emit('update:startDate', e.target.value)
}
function onEnd(e) {
  emit('update:endDate', e.target.value)
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
      <input
        type="date"
        class="filter-bar__date"
        :value="startDate"
        @input="onStart"
      />
      <span class="filter-bar__sep">至</span>
      <input
        type="date"
        class="filter-bar__date"
        :value="endDate"
        @input="onEnd"
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
</template>

<style scoped>
.filter-bar {
  display: grid;
  grid-template-columns: 180px 1fr auto;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  background: var(--bg-soft);
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-3);
}

.filter-bar__dates {
  display: flex;
  align-items: center;
  gap: 8px;
}

.filter-bar__date {
  height: 34px;
  flex: 1;
  min-width: 120px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
  color: var(--ink-1);
  font: inherit;
}

.filter-bar__date:focus {
  outline: none;
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.filter-bar__sep {
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.filter-bar__actions {
  display: flex;
  gap: 8px;
}

@media (max-width: 720px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }
  .filter-bar__actions { justify-content: flex-end; }
}
</style>
