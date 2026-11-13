<script setup>
// 后台紧凑按钮：筛选栏和页面标题行共用，34px 高、平底色（与前台 BaseButton 区分）。
// 只负责后台外观，不改变任何业务行为。
defineProps({
  variant: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'primary'].includes(value)
  },
  disabled: Boolean
})

const emit = defineEmits(['click'])
</script>

<template>
  <button
    type="button"
    class="admin-button"
    :class="`admin-button--${variant}`"
    :disabled="disabled"
    @click="emit('click')"
  >
    <slot />
  </button>
</template>

<style scoped>
.admin-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--ink-2);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
  transition: background var(--t-fast) var(--ease-soft),
    border-color var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.admin-button:not(:disabled):hover {
  border-color: var(--brand);
  color: var(--brand-deep);
}

.admin-button--primary {
  border-color: var(--brand-deep);
  background: var(--brand-deep);
  color: var(--bg-card);
}

.admin-button--primary:not(:disabled):hover {
  border-color: var(--sea-deep);
  background: var(--sea-deep);
  color: var(--bg-card);
}

.admin-button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.admin-button:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .admin-button {
    height: 36px;
  }
}
</style>
