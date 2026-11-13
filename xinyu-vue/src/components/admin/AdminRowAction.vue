<script setup>
// 后台表格行内操作：电脑是可点文字按钮（查看/编辑用品牌色，隐藏/禁用用危险色）；
// 手机上由表格卡片模式渲染成 30px 胶囊按钮，下线类操作用危险柔底。
defineProps({
  tone: {
    type: String,
    default: 'default',
    validator: (value) => ['default', 'danger'].includes(value)
  }
})

const emit = defineEmits(['click'])
</script>

<template>
  <button
    type="button"
    class="admin-row-action"
    :class="`admin-row-action--${tone}`"
    @click="emit('click')"
  >
    <slot />
  </button>
</template>

<style scoped>
.admin-row-action {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--brand-deep);
  font-family: inherit;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.admin-row-action + .admin-row-action {
  margin-left: 12px;
}

.admin-row-action:hover {
  text-decoration: underline;
}

.admin-row-action--danger {
  color: var(--danger-ink);
}

.admin-row-action:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

@media (max-width: 768px) {
  .admin-row-action {
    display: inline-flex;
    align-items: center;
    height: 30px;
    padding: 0 12px;
    border: 1px solid var(--line-strong);
    border-radius: 8px;
    color: var(--ink-2);
    font-size: 12px;
  }

  .admin-row-action + .admin-row-action {
    margin-left: 0;
  }

  .admin-row-action--danger {
    border-color: var(--danger-soft);
    background: var(--danger-soft);
    color: var(--danger-ink);
  }

  .admin-row-action:hover {
    text-decoration: none;
  }
}
</style>
