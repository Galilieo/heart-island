<script setup>
// 登录后页面的整体外壳：顶部导航 + 内容区 + 底部 + 纸纹理背景。
// 按风格规范“小芽出现必须有角色”，不再放与内容无关的漂浮涂鸦。
// width: normal/narrow/wide 决定 slot 的最大宽度。
// 登录/注册页不用这个，它们用 AuthLayout。
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import AppBottomNav from './AppBottomNav.vue'

defineProps({
  noHeader: Boolean,
  noFooter: Boolean,
  width: { type: String, default: 'normal' } // normal | narrow | wide
})
</script>

<template>
  <div class="shell" :class="{ 'shell--with-nav': !noHeader }">
    <AppHeader v-if="!noHeader" />
    <main
      class="shell__main"
      :class="{
        'shell__main--narrow': width === 'narrow',
        'shell__main--wide': width === 'wide'
      }"
    >
      <slot />
    </main>
    <AppFooter v-if="!noFooter" />
    <AppBottomNav v-if="!noHeader" />
  </div>
</template>

<style scoped>
.shell {
  min-height: inherit;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* 纸纹理 */
.shell::before {
  content: '';
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(
    rgba(120, 98, 60, 0.04) 1px,
    transparent 1px
  );
  background-size: 14px 14px;
  z-index: 0;
  opacity: 0.5;
}

/* 底部海面薄雾，给页面一个“岸” */
.shell::after {
  content: '';
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 200px;
  pointer-events: none;
  /* 色值取自小芽色的 --sea-deep（#2f6157） */
  background: linear-gradient(to top, rgba(47, 97, 87, 0.08), transparent);
  z-index: 0;
}

.shell__main {
  flex: 1;
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 28px 0;
}

.shell__main--narrow :slotted(*) { max-width: 1080px; margin-left: auto; margin-right: auto; }
.shell__main--wide   :slotted(*) { max-width: none; }
</style>
