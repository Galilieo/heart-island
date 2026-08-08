<script setup>
// 开关（pill 滑块）。v-model 布尔值，label 为右侧说明文字。
defineProps({
  modelValue: Boolean,
  label: String,
  disabled: Boolean
})

defineEmits(['update:modelValue'])
</script>

<template>
  <button
    type="button"
    class="switch"
    :class="{ 'is-on': modelValue }"
    role="switch"
    :aria-checked="modelValue"
    :disabled="disabled"
    @click="$emit('update:modelValue', !modelValue)"
  >
    <span class="switch__track" aria-hidden="true"><span class="switch__knob" /></span>
    <span v-if="label" class="switch__label">{{ label }}</span>
  </button>
</template>

<style scoped>
.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px;
  border: none;
  background: transparent;
  cursor: pointer;
}

.switch:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.switch:focus-visible {
  outline: none;
}
.switch:focus-visible .switch__track {
  box-shadow: var(--shadow-focus);
}

.switch__track {
  position: relative;
  width: 36px;
  height: 20px;
  border-radius: var(--radius-pill);
  background: var(--line-strong);
  transition: background var(--t-fast) var(--ease-soft);
}

.switch.is-on .switch__track {
  background: linear-gradient(135deg, var(--sea), var(--brand));
}

.switch__knob {
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #fff;
  box-shadow: var(--shadow-sm);
  transition: transform var(--t-fast) var(--ease-bounce);
}

.switch.is-on .switch__knob {
  transform: translateX(16px);
}

.switch__label {
  font-size: var(--fs-sm);
  color: var(--ink-2);
  font-weight: 600;
}

.switch.is-on .switch__label {
  color: var(--brand-deep);
}
</style>
