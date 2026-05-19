<script setup>
// 登录后页面的整体外壳：顶部导航 + 内容区 + 底部 + 纸纹理背景。
// width: normal/narrow/wide 决定 slot 的最大宽度。
// 登录/注册页不用这个，它们用 AuthLayout。
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'

defineProps({
  noHeader: Boolean,
  noFooter: Boolean,
  width: { type: String, default: 'normal' } // normal | narrow | wide
})
</script>

<template>
  <div class="shell">
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
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  position: relative;
}

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

.shell__main {
  flex: 1;
  position: relative;
  z-index: 1;
  width: 100%;
  padding: 28px 0;
}

.shell__main--narrow :slotted(*) { max-width: 760px; margin-left: auto; margin-right: auto; }
.shell__main--wide   :slotted(*) { max-width: none; }
</style>
