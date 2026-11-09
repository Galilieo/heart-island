<script setup>
// 新增和编辑共用单选入口；分组、选项和颜色均从同一份展示配置取得。
import { MOOD_GROUPS, moodTone, moodExpression } from '../../utils/mood'
import Xiaoya from '../ui/Xiaoya.vue'

defineProps({
  modelValue: { type: String, default: '' },
  disabled: Boolean
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div class="mood-picker" role="group" aria-label="选择心情类型">
    <fieldset v-for="group in MOOD_GROUPS" :key="group.id" class="mood-picker__group" :aria-label="group.ariaLabel" :disabled="disabled">
      <div class="mood-picker__options">
        <button
          v-for="item in group.options"
          :key="item.value"
          type="button"
          class="mood-chip"
          :class="[`mood-chip--${moodTone(item.value)}`, { 'is-active': modelValue === item.value }]"
          :aria-pressed="modelValue === item.value"
          :disabled="disabled"
          @click="$emit('update:modelValue', item.value)"
        >
          <Xiaoya :expression="moodExpression(item.value)" :animated="modelValue === item.value && !disabled" :size="48" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </fieldset>
  </div>
</template>

<style scoped>
.mood-picker { margin-bottom: 16px; }
.mood-picker__group { border: 0; padding: 0; margin: 0 0 16px; min-width: 0; }
.mood-picker__group:last-child { margin-bottom: 0; }
.mood-picker__options { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }
@media (max-width: 480px) {
  .mood-picker__options { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
.mood-chip {
  display: inline-flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  min-width: 0; min-height: 80px; padding: 6px 4px 8px;
  border: 1px solid var(--line); border-radius: var(--radius-2);
  background: var(--bg-card); color: var(--ink-2);
  font: inherit; font-size: var(--fs-sm); font-weight: 600;
  cursor: pointer; transition: background var(--t-fast) var(--ease-soft), border-color var(--t-fast) var(--ease-soft);
}
.mood-chip:not(:disabled):hover { background: var(--sea-soft); border-color: var(--sea-soft); }
.mood-chip:disabled { opacity: 0.55; cursor: not-allowed; }
.mood-chip.is-active { border-color: currentColor; font-weight: 700; }
.mood-chip--joy.is-active { background: var(--joy-soft); color: var(--joy-ink); }
.mood-chip--calm.is-active { background: var(--calm-soft); color: var(--calm-ink); }
.mood-chip--warn.is-active { background: var(--warn-soft); color: var(--warn-ink); }
.mood-chip--sad.is-active { background: var(--sad-soft); color: var(--sad-ink); }
.mood-chip--tired.is-active { background: var(--tired-soft); color: var(--tired-ink); }
.mood-chip--neutral.is-active { background: var(--brand-soft); color: var(--brand-deep); }
</style>
