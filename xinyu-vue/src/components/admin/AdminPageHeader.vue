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
  },
  // sand（默认，暖沙纸面）| abyss（深航控制台，仅概览页使用）
  tone: {
    type: String,
    default: 'sand',
    validator: (v) => ['sand', 'abyss'].includes(v)
  }
})
</script>

<template>
  <header class="admin-page-header" :class="`admin-page-header--${tone}`">
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
  background: linear-gradient(135deg, var(--hero-from), var(--hero-to));
}

/* 深航控制台：与「潮汐之窗」同一片深海夜色，仅概览页启用 */
.admin-page-header--abyss {
  background: linear-gradient(135deg, var(--abyss-deep), var(--sea-deep));
  box-shadow: 0 18px 40px rgba(22, 50, 58, 0.22);
}

.admin-page-header--abyss .admin-page-header__kicker {
  color: var(--gold);
}

.admin-page-header--abyss .admin-page-header__title {
  color: var(--ink-on-sea);
}

.admin-page-header--abyss .admin-page-header__desc {
  color: rgba(246, 241, 228, 0.78);
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
