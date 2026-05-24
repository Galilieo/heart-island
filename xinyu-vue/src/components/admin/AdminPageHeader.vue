<script setup>
defineProps({
  kicker: {
    type: String,
    default: '后台管理'
  },
  title: {
    type: String,
    required: true
  },
  desc: {
    type: String,
    default: ''
  }
})
</script>

<template>
  <header class="admin-page-header">
    <div>
      <p class="admin-page-header__kicker">{{ kicker }}</p>
      <h1 class="admin-page-header__title">{{ title }}</h1>
      <p v-if="desc" class="admin-page-header__desc">
        {{ desc }}
      </p>
    </div>
  </header>
</template>

<style scoped>
.admin-page-header {
  padding: 28px 32px;
  border-radius: var(--radius-4);
  background: linear-gradient(135deg, var(--brand-soft), var(--accent-soft));
}

.admin-page-header__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.admin-page-header__title {
  margin: 8px 0 0;
  font-size: var(--fs-3xl);
}

.admin-page-header__desc {
  margin: 10px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-md);
}

@media (max-width: 480px) {
  .admin-page-header {
    padding: 24px;
  }

  .admin-page-header__title {
    font-size: var(--fs-2xl);
  }
}
</style>
