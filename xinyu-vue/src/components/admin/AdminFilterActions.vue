<script setup>
import BaseButton from '../ui/BaseButton.vue'

defineProps({
  loading: Boolean,
  disabled: Boolean,
  searchText: {
    type: String,
    default: '搜索'
  },
  resetText: {
    type: String,
    default: '重置'
  }
})

const emit = defineEmits(['search', 'reset'])
</script>

<template>
  <div class="admin-filter-actions">
    <BaseButton variant="primary" :loading="loading" @click="emit('search')">
      {{ searchText }}
    </BaseButton>
    <BaseButton variant="plain" :disabled="disabled || loading" @click="emit('reset')">
      {{ resetText }}
    </BaseButton>
    <slot />
  </div>
</template>

<style scoped>
.admin-filter-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
</style>
