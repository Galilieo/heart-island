<script setup>
// 新增和编辑共用单选入口；分组、选项和颜色均从同一份展示配置取得。
// 外观为横排小卡：30px 小芽表情 + 文字，5 列网格；≤640px 改 3 列。
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
          <Xiaoya :expression="moodExpression(item.value)" :animated="modelValue === item.value && !disabled" :size="30" />
          <span>{{ item.label }}</span>
        </button>
      </div>
    </fieldset>
  </div>
</template>

<style scoped>
.mood-picker { margin-bottom: 16px; }
.mood-picker__group { border: 0; padding: 0; margin: 0 0 8px; min-width: 0; }
.mood-picker__group:last-child { margin-bottom: 0; }
.mood-picker__options { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 8px; }

/* 每个 tone 类只声明三个 CSS 变量，选中/悬停规则共用一份，避免按 tone 重复写样式 */
.mood-chip--joy { --tone: var(--joy); --tone-soft: var(--joy-soft); --tone-ink: var(--joy-ink); }
.mood-chip--calm { --tone: var(--calm); --tone-soft: var(--calm-soft); --tone-ink: var(--calm-ink); }
.mood-chip--tension { --tone: var(--tension); --tone-soft: var(--tension-soft); --tone-ink: var(--tension-ink); }
.mood-chip--sad { --tone: var(--sad); --tone-soft: var(--sad-soft); --tone-ink: var(--sad-ink); }
.mood-chip--tired { --tone: var(--tired); --tone-soft: var(--tired-soft); --tone-ink: var(--tired-ink); }
.mood-chip--neutral { --tone: var(--brand); --tone-soft: var(--brand-soft); --tone-ink: var(--brand-deep); }

.mood-chip {
  display: inline-flex; align-items: center; gap: 6px;
  min-width: 0; padding: 4px 10px 4px 6px;
  border: 1px solid var(--line); border-radius: var(--radius-2);
  background: var(--bg-card); color: var(--ink-2);
  font: inherit; font-size: var(--fs-sm); text-align: left;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft), border-color var(--t-fast) var(--ease-soft);
}

/* 悬停只改边框为对应心情色，不改变底色，避免误以为已选中 */
.mood-chip:not(:disabled):hover { border-color: var(--tone); }

.mood-chip.is-active {
  border-color: var(--tone);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

/* fieldset[disabled] 会禁用整组选项，这里统一覆盖禁用态 */
.mood-chip:disabled { opacity: 0.55; cursor: not-allowed; }

@media (max-width: 640px) {
  .mood-picker__options { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}
</style>
