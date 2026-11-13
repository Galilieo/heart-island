<script setup>
// 后台状态标签：圆点 + 文字。正常、成功用成功色；隐藏、禁用、失败用危险色。
// 替换原来误用 BaseTag variant 传色的写法（第 5 步 3.2 节修正）。
defineProps({
  text: { type: String, required: true },
  tone: {
    type: String,
    default: 'ok',
    validator: (value) => ['ok', 'bad'].includes(value)
  }
})
</script>

<template>
  <span class="admin-status" :class="`admin-status--${tone}`">
    <span class="admin-status__dot" aria-hidden="true" />
    {{ text }}
  </span>
</template>

<style scoped>
.admin-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--success-ink);
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.admin-status--bad {
  color: var(--danger-ink);
}

.admin-status__dot {
  width: 7px;
  height: 7px;
  flex: none;
  border-radius: 50%;
  background: currentColor;
}
</style>
