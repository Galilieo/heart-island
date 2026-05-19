<script setup>
defineProps({
  modelValue: { type: [String, Number, null], default: '' },
  options: { type: Array, default: () => [] }, // [{label, value}]
  placeholder: { type: String, default: '请选择' },
  label: String,
  disabled: Boolean,
  size: { type: String, default: 'md' }
})

defineEmits(['update:modelValue', 'change'])
</script>

<template>
  <label class="field" :class="{ 'is-disabled': disabled }">
    <span v-if="label" class="field__label">{{ label }}</span>
    <span class="field__control" :class="`field__control--${size}`">
      <select
        :value="modelValue"
        :disabled="disabled"
        @change="
          $emit('update:modelValue', $event.target.value);
          $emit('change', $event.target.value);
        "
      >
        <option value="" disabled v-if="placeholder && modelValue === ''">
          {{ placeholder }}
        </option>
        <option v-for="opt in options" :key="String(opt.value)" :value="opt.value">
          {{ opt.label }}
        </option>
      </select>
      <span class="field__caret" aria-hidden="true">▾</span>
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
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 14px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  position: relative;
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.field__control--sm { height: 34px; }
.field__control--md { height: 42px; }
.field__control--lg { height: 48px; }

.field__control:focus-within {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.field.is-disabled .field__control {
  background: var(--bg-soft);
  color: var(--ink-3);
}

.field select {
  flex: 1;
  appearance: none;
  -webkit-appearance: none;
  border: none;
  outline: none;
  background: transparent;
  color: var(--ink-1);
  font: inherit;
  cursor: pointer;
  padding-right: 4px;
}

.field__caret {
  color: var(--ink-3);
  pointer-events: none;
  font-size: 12px;
}
</style>
