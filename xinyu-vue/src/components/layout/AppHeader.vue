<script setup>
// 顶部导航条。包含 Logo、心情/社区 两个 Tab、右侧用户菜单。
// 用户菜单里的"退出登录"会弹确认框，确认后清登录态并跳回登录页。
import { ref } from 'vue'
import { useRouter, useRoute, RouterLink } from 'vue-router'
import { useUserStore } from '../../stores/user'
import { useConfirm } from '../../composables/useConfirm'
import { useToast } from '../../composables/useToast'
import logoUrl from '../../assets/logo-xinyu.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()
const { confirm } = useConfirm()
const toast = useToast()

const menuOpen = ref(false)

function toggleMenu() {
  menuOpen.value = !menuOpen.value
}

function closeMenu() {
  menuOpen.value = false
}

function goProfile() {
  closeMenu()
  router.push('/profile')
}

async function handleLogout() {
  closeMenu()
  const ok = await confirm({
    title: '退出登录？',
    content: '退出后需重新登录才能查看心情和社区。',
    confirmText: '退出',
    tone: 'danger'
  })
  if (!ok) return
  userStore.logout()
  toast.info('已退出')
  router.push('/')
}

const navItems = [
  { label: '心情', to: '/home', match: ['/home'] },
  { label: '社区', to: '/posts', match: ['/posts', '/community'] },
  { label: '收藏', to: '/favorites', match: ['/favorites'] },
  { label: '后台', to: '/admin/users', match: ['/admin'], adminOnly: true }
]

function isActive(item) {
  return item.match.some((m) => route.path.startsWith(m))
}
</script>

<template>
  <header class="app-header">
    <div class="app-header__inner container">
      <RouterLink to="/home" class="brand" @click="closeMenu">
        <img :src="logoUrl" alt="心屿" class="brand__logo" />
        <span class="brand__name">心屿</span>
      </RouterLink>

      <nav class="nav">
        <RouterLink
          v-for="item in navItems.filter((item)=> !item.adminOnly ||userStore.loginUser?.role === 'ADMIN')"
          :key="item.to"
          :to="item.to"
          class="nav__item"
          :class="{ 'is-active': isActive(item) }"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="user">
        <button
          type="button"
          class="user__chip"
          @click="toggleMenu"
          :aria-expanded="menuOpen"
        >
          <span class="user__avatar">
            {{ (userStore.loginUser?.username || '心屿')[0].toUpperCase() }}
          </span>
          <span class="user__name">{{ userStore.loginUser?.username || '游客' }}</span>
          <span class="user__caret" aria-hidden="true">▾</span>
        </button>

        <transition name="menu">
          <div v-if="menuOpen" class="user__menu" @click.stop>
            <button type="button" class="user__menu-item" @click="goProfile">
              个人资料
            </button>
            <div class="user__menu-divider" />
            <button type="button" class="user__menu-item" @click="handleLogout">
              退出登录
            </button>
          </div>
        </transition>
        <div v-if="menuOpen" class="menu-mask" @click="closeMenu" />
      </div>
    </div>
  </header>
</template>

<style scoped>
.app-header {
  position: sticky;
  top: 0;
  z-index: var(--z-header);
  background: rgba(253, 250, 243, 0.85);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--line);
}

.app-header__inner {
  display: flex;
  align-items: center;
  gap: 32px;
  height: 64px;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--ink-1);
  text-decoration: none;
  font-weight: 700;
}

.brand__logo {
  width: 32px;
  height: 32px;
  border-radius: 10px;
  object-fit: cover;
  box-shadow: 0 6px 14px rgba(111, 169, 164, 0.32);
}

.brand__name {
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  letter-spacing: 1px;
}

.nav {
  flex: 1;
  display: flex;
  gap: 6px;
}

.nav__item {
  position: relative;
  padding: 8px 16px;
  border-radius: var(--radius-pill);
  color: var(--ink-2);
  font-size: var(--fs-md);
  font-weight: 600;
  text-decoration: none;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.nav__item:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.nav__item.is-active {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 6px 14px rgba(111, 169, 164, 0.28);
}

.user {
  position: relative;
}

.user__chip {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 40px;
  padding: 0 14px 0 4px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  color: var(--ink-1);
  cursor: pointer;
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.user__chip:hover {
  border-color: var(--brand-soft);
  box-shadow: var(--shadow-sm);
}

.user__avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), var(--accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-sm);
  font-weight: 700;
}

.user__name {
  font-size: var(--fs-sm);
  font-weight: 600;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user__caret {
  color: var(--ink-3);
  font-size: 11px;
}

.user__menu {
  position: absolute;
  right: 0;
  top: calc(100% + 8px);
  min-width: 160px;
  padding: 6px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  box-shadow: var(--shadow-md);
  z-index: calc(var(--z-header) + 1);
}

.user__menu-item {
  width: 100%;
  padding: 10px 12px;
  border: none;
  background: transparent;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  text-align: left;
  cursor: pointer;
  border-radius: var(--radius-2);
  transition: background var(--t-fast) var(--ease-soft);
}

.user__menu-item:hover {
  background: var(--bg-soft);
  color: var(--brand-deep);
}

.user__menu-item:last-child:hover {
  color: var(--danger);
}

.user__menu-divider {
  height: 1px;
  margin: 4px 6px;
  background: var(--line);
}

.menu-mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-header);
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
.menu-enter-active,
.menu-leave-active {
  transition: opacity var(--t-fast) var(--ease-soft),
    transform var(--t-fast) var(--ease-soft);
}

@media (max-width: 768px) {
  .app-header__inner { gap: 12px; }
  .brand__name { display: none; }
  .user__name { display: none; }
  .nav__item { padding: 6px 12px; font-size: var(--fs-sm); }
}
</style>
