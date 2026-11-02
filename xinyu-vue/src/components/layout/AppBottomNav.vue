<script setup>
import { useRoute, RouterLink } from 'vue-router'
import Doodle from '../ui/Doodle.vue'
import { navigationItems, isNavigationActive } from './navigation'

const route = useRoute()
</script>

<template>
  <!-- 避开路由过渡 transform 创建的包含块，始终固定在视口底边。 -->
  <Teleport to="body">
    <nav class="bottom-nav" aria-label="手机主导航">
      <RouterLink
        v-for="item in navigationItems"
        :key="item.to"
        :to="item.to"
        class="bottom-nav__item"
        :class="{ 'is-active': isNavigationActive(item, route.path) }"
        :aria-current="isNavigationActive(item, route.path) ? 'page' : undefined"
      >
        <Doodle :name="item.icon" :size="24" :stroke-width="3" />
        <span>{{ item.label }}</span>
      </RouterLink>
    </nav>
  </Teleport>
</template>

<style scoped>
.bottom-nav {
  display: none;
}

@media (max-width: 768px) {
  .bottom-nav {
    position: fixed;
    inset: auto 0 0;
    z-index: var(--z-header);
    display: grid;
    grid-template-columns: repeat(4, minmax(0, 1fr));
    min-height: calc(64px + env(safe-area-inset-bottom, 0px));
    padding: 4px 12px calc(4px + env(safe-area-inset-bottom, 0px));
    background: var(--bg-card);
    border-top: 1px solid var(--line);
    box-shadow: 0 -4px 18px rgba(42, 86, 85, 0.06);
  }

  .bottom-nav__item {
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    min-height: 54px;
    gap: 2px;
    border-radius: var(--radius-2);
    color: var(--ink-2);
    font-size: var(--fs-xs);
    font-weight: 600;
  }

  .bottom-nav__item.is-active {
    color: var(--brand-deep);
    background: var(--brand-soft);
  }

  .bottom-nav__item:focus-visible {
    outline: 2px solid var(--brand-deep);
    outline-offset: -2px;
  }
}
</style>
