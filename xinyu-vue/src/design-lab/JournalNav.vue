<script setup>
// 手账风格的全站顶栏：左侧为横版字标图片，导航为纯文字，当前页用品牌色下划线标示。
import { inject, ref } from 'vue'
import { MOCK_USER } from './mock'
import brandLockup from '../assets/brand/xinyu-lockup.svg'

// 字标由设计对比页按当前选项与主题提供；单独使用时退回正式字标（站酷小薇加粗版）
const logoSrc = inject('logoSrc', ref(brandLockup))

defineProps({
  // 当前页面名称，与 NAV_ITEMS 中的文字对应
  active: { type: String, required: true },
  // 宽屏两栏布局时顶栏随内容加宽
  wide: Boolean
})

const NAV_ITEMS = ['心情', '社区', '收藏']
</script>

<template>
  <nav class="nav" :class="{ 'nav--wide': wide }">
    <img :src="logoSrc" alt="心屿" class="nav__logo">
    <span class="nav__links">
      <a v-for="item in NAV_ITEMS" :key="item" :class="{ 'is-on': item === active }">{{ item }}</a>
    </span>
    <span class="nav__me">{{ MOCK_USER.nickname.slice(0, 1) }}</span>
  </nav>
</template>

<style scoped>
.nav {
  display: flex;
  align-items: center;
  gap: 28px;
  max-width: 720px;
  margin: 0 auto;
  padding: 18px 20px;
}

.nav--wide { max-width: 1100px; }

/* 字标高度 32px，对应素材包“页头 32px”的规范 */
.nav__logo {
  display: block;
  height: 32px;
  width: auto;
}

.nav__links {
  display: flex;
  gap: 20px;
  flex: 1;
  color: var(--ink-2);
}

.nav__links a { cursor: pointer; }

.nav__links a.is-on {
  padding-bottom: 2px;
  border-bottom: 2px solid var(--brand);
  color: var(--ink-1);
  font-weight: 700;
}

.nav__me {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

@container (max-width: 640px) {
  .nav { gap: 16px; padding: 14px 16px; }
  .nav__logo { height: 26px; }
  .nav__links { gap: 14px; }
}
</style>
