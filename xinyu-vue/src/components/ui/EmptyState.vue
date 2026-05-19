<script setup>
// 空状态占位。一张涂鸦 + 标题 + 提示 + 可选操作按钮（通过 default slot 传）。
import Doodle from './Doodle.vue'

defineProps({
  title: { type: String, default: '这里还是空的' },
  hint: String,
  doodle: { type: String, default: 'cloud' },
  size: { type: String, default: 'md' }
})
</script>

<template>
  <div class="empty" :class="`empty--${size}`">
    <Doodle :name="doodle" :size="size === 'sm' ? 56 : 80" color="var(--ink-3)" />
    <p class="empty__title">{{ title }}</p>
    <p v-if="hint" class="empty__hint">{{ hint }}</p>
    <div v-if="$slots.default" class="empty__action"><slot /></div>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  text-align: center;
  color: var(--ink-3);
}

.empty--sm { padding: 28px 16px; }
.empty--md { padding: 48px 24px; }
.empty--lg { padding: 72px 24px; }

.empty__title {
  font-size: var(--fs-md);
  color: var(--ink-2);
  margin-top: 4px;
  font-weight: 600;
}

.empty__hint {
  font-size: var(--fs-sm);
  color: var(--ink-3);
  max-width: 360px;
  line-height: 1.7;
}

.empty__action {
  margin-top: 8px;
}
</style>
