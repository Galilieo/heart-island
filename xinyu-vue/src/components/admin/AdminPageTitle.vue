<script setup>
// 后台页面标题行：标题 20px + 右侧一行说明；页面级按钮通过 actions 插槽放在最右侧。
// 后台标题固定用系统黑体（全局 h1 默认文楷），手机上说明换到标题下方。
defineProps({
  title: { type: String, required: true },
  desc: { type: String, default: '' }
})
</script>

<template>
  <div class="admin-page-title">
    <div class="admin-page-title__text">
      <h1>{{ title }}</h1>
      <p v-if="desc">{{ desc }}</p>
    </div>
    <div v-if="$slots.actions" class="admin-page-title__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<style scoped>
.admin-page-title {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.admin-page-title__text {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}

.admin-page-title h1 {
  margin: 0;
  color: var(--ink-1);
  font-family: var(--font-sans);
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 0;
  white-space: nowrap;
}

.admin-page-title p {
  margin: 0;
  color: var(--ink-2);
  font-size: 13px;
}

.admin-page-title__actions {
  margin-left: auto;
  flex: none;
}

@media (max-width: 768px) {
  .admin-page-title {
    flex-wrap: wrap;
    row-gap: 4px;
  }

  .admin-page-title__text {
    flex: 1 1 auto;
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .admin-page-title h1 {
    font-size: 18px;
  }

  .admin-page-title p {
    font-size: 12px;
  }
}
</style>
