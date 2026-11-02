<script setup>
// 登录后页面的整体外壳：顶部导航 + 内容区 + 底部 + 纸纹理背景 + 散点装饰。
// width: normal/narrow/wide 决定 slot 的最大宽度。
// 登录/注册页不用这个，它们用 AuthLayout。
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import AppBottomNav from './AppBottomNav.vue'
import Doodle from '../ui/Doodle.vue'

defineProps({
  noHeader: Boolean,
  noFooter: Boolean,
  width: { type: String, default: 'normal' } // normal | narrow | wide
})
</script>

<template>
  <div class="shell" :class="{ 'shell--with-nav': !noHeader }">
    <!-- 背景装饰层：克制的海边元素（波 / 云 / 日光），浮在内容下方、不接收点击 -->
    <div class="shell__ornaments" aria-hidden="true">
      <Doodle name="wave" :size="170" color="rgba(61,111,110,0.13)" class="orn orn-1" />
      <Doodle name="cloud" :size="104" color="rgba(61,111,110,0.10)" class="orn orn-2" />
      <Doodle name="sun"  :size="68"  color="rgba(238,194,127,0.22)" class="orn orn-3" />
    </div>

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

/* 散点装饰：固定定位，贴边，缓慢漂浮 */
.shell__ornaments {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
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
  background: linear-gradient(to top, rgba(42, 86, 85, 0.10), transparent);
  z-index: 0;
}

.orn {
  position: absolute;
  animation: orn-drift 22s ease-in-out infinite alternate;
}

.orn-1 { bottom: 4%;  left: 2%;  }
.orn-2 { top: 14%;   right: 3%; animation-delay: 4s; }
.orn-3 { top: 46%;   right: 6%; animation-delay: 8s; }

@keyframes orn-drift {
  from { transform: translateY(0) rotate(0deg); }
  to   { transform: translateY(-10px) rotate(3deg); }
}

/* 小屏关掉这些大涂鸦，免得挤压内容 */
@media (max-width: 1024px) {
  .shell__ornaments { display: none; }
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
