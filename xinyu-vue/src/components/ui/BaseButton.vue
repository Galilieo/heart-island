<script setup>
// 通用按钮。支持 6 种 variant（primary/soft/ghost/danger/plain/link）
// 和 3 种 size（sm/md/lg），loading 时显示自旋指示器并禁用点击。
defineProps({
  variant: {
    type: String,
    default: 'primary',
    validator: (v) => ['primary', 'soft', 'ghost', 'danger', 'plain', 'link'].includes(v)
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  },
  type: {
    type: String,
    default: 'button'
  },
  loading: Boolean,
  disabled: Boolean,
  block: Boolean
})
</script>

<template>
  <button
    :type="type"
    class="btn"
    :class="[`btn--${variant}`, `btn--${size}`, { 'btn--block': block, 'is-loading': loading }]"
    :disabled="disabled || loading"
  >
    <span v-if="loading" class="btn__spinner" aria-hidden="true" />
    <span v-else-if="$slots.icon" class="btn__icon"><slot name="icon" /></span>
    <span class="btn__text"><slot /></span>
  </button>
</template>

<style scoped>
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 40px;
  padding: 0 18px;
  border: 1px solid transparent;
  border-radius: var(--radius-2);
  background: transparent;
  color: var(--ink-1);
  font-size: var(--fs-md);
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft),
    transform var(--t-fast) var(--ease-bounce),
    box-shadow var(--t-fast) var(--ease-soft),
    border-color var(--t-fast) var(--ease-soft);
}

.btn:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.btn:not(:disabled):active {
  transform: translateY(1px);
}

.btn--sm {
  height: 32px;
  padding: 0 12px;
  font-size: var(--fs-sm);
  border-radius: var(--radius-2);
}

.btn--lg {
  height: 48px;
  padding: 0 24px;
  font-size: var(--fs-lg);
  border-radius: var(--radius-3);
}

.btn--block { width: 100%; }

/* primary */
.btn--primary {
  background: linear-gradient(135deg, var(--brand) 0%, #84bab6 100%);
  color: #fff;
  box-shadow: 0 10px 22px rgba(111, 169, 164, 0.28);
}
.btn--primary:not(:disabled):hover {
  background: linear-gradient(135deg, var(--brand-deep), var(--brand));
  box-shadow: 0 12px 28px rgba(77, 133, 128, 0.32);
}

/* soft */
.btn--soft {
  background: var(--brand-soft);
  color: var(--brand-deep);
}
.btn--soft:not(:disabled):hover {
  background: #d6e8e5;
}

/* ghost */
.btn--ghost {
  background: transparent;
  color: var(--brand-deep);
  border-color: var(--line);
}
.btn--ghost:not(:disabled):hover {
  background: var(--brand-soft);
  border-color: var(--brand-soft);
}

/* danger */
.btn--danger {
  background: var(--danger);
  color: #fff;
  box-shadow: 0 8px 20px rgba(198, 106, 106, 0.26);
}
.btn--danger:not(:disabled):hover { background: #b15555; }

/* plain (neutral) */
.btn--plain {
  background: var(--bg-soft);
  color: var(--ink-2);
}
.btn--plain:not(:disabled):hover {
  background: var(--line);
  color: var(--ink-1);
}

/* link */
.btn--link {
  height: auto;
  padding: 4px 6px;
  background: transparent;
  color: var(--brand-deep);
  font-weight: 500;
  box-shadow: none;
}
.btn--link:not(:disabled):hover {
  color: var(--brand);
  background: transparent;
}

/* spinner */
.btn__spinner {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  border: 2px solid currentColor;
  border-right-color: transparent;
  animation: btn-spin 720ms linear infinite;
}

@keyframes btn-spin {
  to { transform: rotate(360deg); }
}

.btn__icon {
  display: inline-flex;
  align-items: center;
}
</style>
