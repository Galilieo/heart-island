<script setup>
import { RouterLink } from 'vue-router'
</script>

<template>
  <nav class="admin-tabs">
    <RouterLink to="/admin" class="admin-tabs__item">
      概览
    </RouterLink>
    <RouterLink to="/admin/users" class="admin-tabs__item">
      用户管理
    </RouterLink>
    <RouterLink to="/admin/posts" class="admin-tabs__item">
      帖子管理
    </RouterLink>
    <RouterLink to="/admin/replies" class="admin-tabs__item">
      回复管理
    </RouterLink>
    <RouterLink to="/admin/topics" class="admin-tabs__item">
      话题管理
    </RouterLink>
    <RouterLink to="/admin/ai-replies" class="admin-tabs__item">
      AI记录
    </RouterLink>
  </nav>
</template>

<style scoped>
.admin-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  max-width: 100%;
  width: fit-content;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
}

.admin-tabs__item {
  padding: 8px 14px;
  border-radius: var(--radius-2);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 700;
  text-decoration: none;
  white-space: nowrap;
}

.admin-tabs__item:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.admin-tabs__item.router-link-active {
  background: var(--brand);
  color: #fff;
}
</style>
