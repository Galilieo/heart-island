<script setup>
// 多行输入框。带 maxlength 时右下角会自动显示字数计数。
// variant 只影响外观：default 为通用输入框；paper 为手账横线纸，仅供首页书写卡使用。
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: String,
  label: String,
  hint: String,
  error: String,
  disabled: Boolean,
  rows: { type: [Number, String], default: 4 },
  maxlength: [Number, String],
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'paper'].includes(value)
  }
})

defineEmits(['update:modelValue'])
</script>

<template>
  <label class="field" :class="{ 'field--paper': variant === 'paper', 'is-error': !!error, 'is-disabled': disabled }">
    <span v-if="label" class="field__label">{{ label }}</span>
    <span class="field__control">
      <textarea
        :value="modelValue"
        :placeholder="placeholder"
        :rows="rows"
        :maxlength="maxlength"
        :disabled="disabled"
        @input="$emit('update:modelValue', $event.target.value)"
      />
    </span>
    <span v-if="error" class="field__msg field__msg--error">{{ error }}</span>
    <span v-else class="field__msg-row">
      <span v-if="hint" class="field__msg">{{ hint }}</span>
      <span v-if="maxlength" class="field__count">{{ modelValue.length }}/{{ maxlength }}</span>
    </span>
  </label>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.field__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-2);
  letter-spacing: 0.5px;
}

.field__control {
  display: block;
  padding: 10px 14px;
  background: var(--bg-card);
  border: 1px solid var(--line-control);
  border-radius: var(--radius-2);
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.field__control:focus-within {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.field:not(.is-error):not(.is-disabled) .field__control:not(:focus-within):hover {
  border-color: var(--line-strong);
}

.field.is-error .field__control {
  border-color: var(--danger);
  box-shadow: var(--shadow-error);
}

.field.is-disabled .field__control {
  background: var(--bg-soft);
  color: var(--ink-3);
  cursor: not-allowed;
}

.field.is-disabled textarea {
  cursor: not-allowed;
}

.field textarea {
  width: 100%;
  border: none;
  outline: none;
  resize: vertical;
  background: transparent;
  color: var(--ink-1);
  font: inherit;
  line-height: 1.6;
  min-height: 80px;
}

.field textarea::placeholder {
  color: var(--ink-3);
}

.field__msg-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 4px;
}

.field__msg {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.field__msg--error {
  color: var(--danger);
  font-size: var(--fs-xs);
  padding-left: 4px;
}

.field__count {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

/* ── paper：手账横线纸。去掉外框，只留底边线；背景横线与 32px 行高对齐，
      文字写在横线上，光标聚焦时底边线变品牌色 ── */
.field--paper .field__control {
  padding: 0;
  background: transparent;
  border: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
}

.field--paper .field__control:focus-within {
  border-color: var(--brand);
  box-shadow: none;
}

.field--paper textarea {
  padding: 4px 4px 0;
  background-image: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 31px,
    var(--line) 31px,
    var(--line) 32px
  );
  font-size: var(--fs-base);
  line-height: 32px;
}

/* 保存中禁用时保留横线纸外观，不换成默认输入框的灰底 */
.field--paper.is-disabled .field__control {
  background: transparent;
}
</style>
