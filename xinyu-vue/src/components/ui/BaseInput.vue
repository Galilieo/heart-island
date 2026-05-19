<script setup>
defineProps({
  modelValue: { type: [String, Number], default: '' },
  type: { type: String, default: 'text' },
  placeholder: String,
  label: String,
  hint: String,
  error: String,
  disabled: Boolean,
  size: { type: String, default: 'md' }
})

defineEmits(['update:modelValue', 'enter'])
</script>

<template>
  <label class="field" :class="{ 'is-error': !!error, 'is-disabled': disabled }">
    <span v-if="label" class="field__label">{{ label }}</span>
    <span class="field__control" :class="`field__control--${size}`">
      <slot name="prefix" />
      <input
        :type="type"
        :value="modelValue"
        :placeholder="placeholder"
        :disabled="disabled"
        @input="$emit('update:modelValue', $event.target.value)"
        @keyup.enter="$emit('enter')"
      />
      <slot name="suffix" />
    </span>
    <span v-if="error" class="field__msg field__msg--error">{{ error }}</span>
    <span v-else-if="hint" class="field__msg">{{ hint }}</span>
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
  gap: 8px;
  padding: 0 14px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.field__control--sm { height: 34px; }
.field__control--md { height: 42px; }
.field__control--lg { height: 48px; font-size: var(--fs-lg); }

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
  color: var(--ink-3);
}

.field input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--ink-1);
  font-size: inherit;
}

.field input::placeholder {
  color: var(--ink-3);
}

.field__msg {
  font-size: var(--fs-xs);
  color: var(--ink-3);
  padding-left: 4px;
}

.field__msg--error {
  color: var(--danger);
}
</style>
