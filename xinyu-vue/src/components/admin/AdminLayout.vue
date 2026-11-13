<script setup>
// 后台独立外框：电脑固定在左侧的导航、顶部细栏、内容区；≤1024px 时导航改为
// 顶部横向滑动条（手机和中等宽度共用），手机端隐藏面包屑。权限仍由路由守卫负责。
import { computed } from 'vue'
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { useTheme } from '../../composables/useTheme'
import { adminNavigationGroups, isAdminNavigationActive } from './adminNavigation'
import AdminIcon from './AdminIcon.vue'
import lockupUrl from '../../assets/brand/xinyu-lockup.svg'
import lockupDarkUrl from '../../assets/brand/xinyu-lockup-dark.svg'

const route = useRoute()
const userStore = useUserStore()
const { theme } = useTheme()

const logoUrl = computed(() =>
  theme.value === 'night' ? lockupDarkUrl : lockupUrl
)

const adminDisplayName = computed(() => userStore.displayName)
const adminInitial = computed(() => adminDisplayName.value.slice(0, 1))
const currentTitle = computed(() => route.meta.title || '概览')

function isActive(item) {
  return isAdminNavigationActive(item, route.path)
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <div class="admin-brand">
        <RouterLink to="/admin" class="admin-brand__logo">
          <img :src="logoUrl" alt="心屿" />
        </RouterLink>
        <span class="admin-brand__tag">管理后台</span>
        <RouterLink to="/home" class="admin-brand__back">
          <AdminIcon name="back" />
          返回心屿
        </RouterLink>
      </div>

      <nav class="admin-nav" aria-label="后台功能导航">
        <template v-for="group in adminNavigationGroups" :key="group.label">
          <p class="admin-nav__section">{{ group.label }}</p>
          <RouterLink
            v-for="item in group.items"
            :key="item.to"
            :to="item.to"
            class="admin-nav__item"
            :class="{ 'is-active': isActive(item) }"
            :aria-current="isActive(item) ? 'page' : undefined"
          >
            <AdminIcon :name="item.icon" />
            <span>{{ item.label }}</span>
          </RouterLink>
        </template>
      </nav>

      <RouterLink to="/home" class="admin-sidebar__back">
        <AdminIcon name="back" />
        返回心屿
      </RouterLink>
    </aside>

    <div class="admin-main">
      <header class="admin-topbar">
        <span class="admin-crumb">
          后台 /
          <b>{{ currentTitle }}</b>
        </span>
        <span class="admin-who">
          <span class="admin-who__avatar" aria-hidden="true">{{ adminInitial }}</span>
          {{ adminDisplayName }}
        </span>
      </header>

      <main class="admin-content">
        <RouterView />
      </main>

      <!-- 备案信息随后台右侧内容区一起滚动，不横跨侧栏，侧栏保持整屏高度。 -->
      <footer class="admin-filing" aria-label="网站备案信息">
        <a
          href="https://beian.miit.gov.cn/"
          target="_blank"
          rel="noopener noreferrer"
        >
          鄂ICP备2026025921号-1
        </a>
        <a
          href="https://beian.mps.gov.cn/#/query/webSearch?code=42028102000306"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img src="/beian-ghs.png" alt="" width="14" height="14" />
          鄂公网安备42028102000306号
        </a>
      </footer>
    </div>
  </div>
</template>

<style scoped>
/* 管理员工具以电脑为主：固定侧栏 212px，内容占满剩余宽度，不套前台 AppShell。 */
.admin-shell {
  display: grid;
  grid-template-columns: 212px minmax(0, 1fr);
  min-height: 100vh;
  background: var(--bg-base);
  color: var(--ink-1);
  font-family: var(--font-sans);
}

.admin-sidebar {
  position: sticky;
  top: 0;
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow-y: auto;
  background: var(--bg-soft);
  border-right: 1px solid var(--line);
}

.admin-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  height: 56px;
  padding: 0 18px;
  border-bottom: 1px solid var(--line);
}

.admin-brand__logo {
  display: inline-flex;
  align-items: center;
}

.admin-brand__logo img {
  display: block;
  height: 22px;
  width: auto;
}

.admin-brand__tag {
  padding: 2px 8px;
  border-radius: 6px;
  background: var(--brand-soft-deep);
  color: var(--ink-1);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1px;
}

.admin-brand__back {
  display: none;
}

.admin-nav {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 2px;
  padding: 14px 10px;
}

.admin-nav__section {
  margin: 12px 10px 6px;
  color: var(--ink-2);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 1.5px;
}

.admin-nav__section:first-child {
  margin-top: 0;
}

.admin-nav__item {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 36px;
  padding: 0 10px;
  border-radius: 8px;
  color: var(--ink-2);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.admin-nav__item:hover {
  background: var(--brand-soft);
  color: var(--ink-1);
}

.admin-nav__item.is-active {
  background: var(--bg-card);
  color: var(--brand-deep);
  font-weight: 700;
  box-shadow: inset 0 0 0 1px var(--line);
}

.admin-nav__item:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.admin-sidebar__back {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0 10px 14px;
  padding: 12px 10px 0;
  border-top: 1px solid var(--line);
  color: var(--ink-2);
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
}

.admin-sidebar__back:hover {
  color: var(--ink-1);
}

.admin-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 100vh;
}

.admin-topbar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 56px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-base);
}

.admin-crumb {
  color: var(--ink-2);
  font-size: 13px;
}

.admin-crumb b {
  color: var(--ink-1);
  font-weight: 700;
}

.admin-who {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ink-2);
  font-size: 13px;
}

.admin-who__avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--brand-soft-deep);
  color: var(--ink-1);
  font-size: 12px;
  font-weight: 700;
}

.admin-content {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  min-width: 0;
  padding: 20px 24px 32px;
}

/* 后台备案信息：放在右侧内容区底部，颜色用 --ink-2 保证日间对比度。 */
.admin-filing {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 6px 20px;
  padding: 12px 24px 20px;
  color: var(--ink-2);
  font-size: 12px;
  line-height: 20px;
}

.admin-filing a {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  min-height: 32px;
  color: inherit;
  text-decoration: none;
  white-space: nowrap;
}

.admin-filing a:hover {
  text-decoration: underline;
}

.admin-filing img {
  display: block;
  width: 14px;
  height: 14px;
  flex: none;
}

/* ≤1024px：侧栏改为顶部横向导航，内容占满整行；手机和中等宽度共用这套导航。 */
@media (max-width: 1024px) {
  .admin-shell {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: auto minmax(0, 1fr);
  }

  .admin-sidebar {
    position: sticky;
    top: 0;
    z-index: 20;
    height: auto;
    overflow: visible;
    border-right: 0;
    border-bottom: 1px solid var(--line);
  }

  .admin-brand {
    height: 52px;
    padding: 0 14px;
  }

  .admin-brand__back {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    margin-left: auto;
    color: var(--brand-deep);
    font-size: 12px;
    font-weight: 600;
    text-decoration: none;
  }

  .admin-brand__back :deep(.admin-icon) {
    width: 14px;
    height: 14px;
  }

  .admin-nav {
    flex-direction: row;
    gap: 6px;
    padding: 8px 12px;
    overflow-x: auto;
    scrollbar-width: none;
  }

  .admin-nav::-webkit-scrollbar {
    display: none;
  }

  .admin-nav__section,
  .admin-sidebar__back {
    display: none;
  }

  .admin-nav__item {
    flex: none;
    height: 32px;
    padding: 0 12px;
    border-radius: var(--radius-pill);
    background: var(--bg-card);
    box-shadow: inset 0 0 0 1px var(--line);
    white-space: nowrap;
  }

  .admin-nav__item :deep(.admin-icon) {
    width: 14px;
    height: 14px;
  }

  .admin-nav__item.is-active {
    background: var(--brand-deep);
    color: var(--bg-card);
    box-shadow: none;
  }

  .admin-topbar {
    display: none;
  }

  .admin-main {
    min-height: 0;
  }

  .admin-content {
    padding: 16px 16px 28px;
  }
}

@media (max-width: 768px) {
  .admin-content {
    padding: 14px 12px 24px;
    gap: 12px;
  }
}
</style>

<style>
/* 后台把备案信息渲染在右侧内容区底部，隐藏 index.html 里横跨全站的那份。 */
body:has(.admin-shell) .site-filing {
  display: none;
}
</style>
