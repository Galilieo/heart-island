<script setup>
// 后台筛选栏：一行排开，右侧是“查询 / 重置”。手机上字段两列排布，
// 需要独占一行的字段由 AdminFilterField 的 full 属性声明，按钮平分一行。
import AdminButton from './AdminButton.vue'

defineProps({
  loading: Boolean
})

const emit = defineEmits(['search', 'reset'])
</script>

<template>
  <div class="admin-filter-bar">
    <div class="admin-filter-bar__fields">
      <slot />
    </div>
    <div class="admin-filter-bar__actions">
      <AdminButton
        variant="primary"
        :disabled="loading"
        @click="emit('search')"
      >
        查询
      </AdminButton>
      <AdminButton :disabled="loading" @click="emit('reset')">
        重置
      </AdminButton>
    </div>
  </div>
</template>

<style scoped>
.admin-filter-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.admin-filter-bar__fields {
  display: flex;
  flex: 0 1 auto;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.admin-filter-bar__actions {
  display: flex;
  flex: none;
  gap: 8px;
}

@media (max-width: 768px) {
  .admin-filter-bar {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }

  .admin-filter-bar__fields {
    display: contents;
  }

  .admin-filter-bar__actions {
    display: grid;
    grid-column: 1 / -1;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 8px;
  }
}
</style>
