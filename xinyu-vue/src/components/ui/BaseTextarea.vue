<script setup>
defineProps({
  modelValue: { type: String, default: '' },
  placeholder: String,
  label: String,
  hint: String,
  error: String,
  disabled: Boolean,
  rows: { type: [Number, String], default: 4 },
  maxlength: [Number, String]
})

defineEmits(['update:modelValue'])
</script>

<template>
  <label class="field" :class="{ 'is-error': !!error, 'is-disabled': disabled }">
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
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.field__control:focus-within {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.field.is-error .field__control {
  border-color: var(--danger);
  box-shadow: 0 0 0 3px rgba(198, 106, 106, 0.16);
}

.field.is-disabled .field__control {
  background: var(--bg-soft);
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
</style>
