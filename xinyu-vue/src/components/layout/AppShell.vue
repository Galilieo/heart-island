<script setup>
// 登录后页面的整体外壳：顶部导航 + 内容区 + 底部 + 纸纹理背景 + 散点装饰。
// width: normal/narrow/wide 决定 slot 的最大宽度。
// 登录/注册页不用这个，它们用 AuthLayout。
import AppHeader from './AppHeader.vue'
import AppFooter from './AppFooter.vue'
import Doodle from '../ui/Doodle.vue'

defineProps({
  noHeader: Boolean,
  noFooter: Boolean,
  width: { type: String, default: 'normal' } // normal | narrow | wide
})
</script>

<template>
  <div class="shell">
    <!-- 背景装饰层：浮在内容下方、不接收点击 -->
    <div class="shell__ornaments" aria-hidden="true">
      <Doodle name="cloud" :size="120" color="rgba(111,169,164,0.18)" class="orn orn-1" />
      <Doodle name="leaf"  :size="90"  color="rgba(155,193,188,0.22)" class="orn orn-2" />
      <Doodle name="star"  :size="64"  color="rgba(217,140,122,0.22)" class="orn orn-3" />
      <Doodle name="wave"  :size="140" color="rgba(143,165,196,0.18)" class="orn orn-4" />
      <Doodle name="sun"   :size="80"  color="rgba(243,185,95,0.22)"  class="orn orn-5" />
      <Doodle name="plant" :size="110" color="rgba(111,169,164,0.18)" class="orn orn-6" />
      <Doodle name="heart" :size="60"  color="rgba(217,140,122,0.22)" class="orn orn-7" />
      <Doodle name="cup"   :size="78"  color="rgba(155,193,188,0.18)" class="orn orn-8" />
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
  </div>
</template>

<style scoped>
.shell {
  min-height: 100vh;
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

/* 散点装饰：固定定位，全屏铺，缓慢漂浮 */
.shell__ornaments {
  position: fixed;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}

.orn {
  position: absolute;
  animation: orn-drift 18s ease-in-out infinite alternate;
}

/* 左侧 */
.orn-1 { top: 12%;  left: 4%;  animation-delay: 0s; }
.orn-2 { top: 42%;  left: 7%;  animation-delay: 3s; }
.orn-3 { top: 70%;  left: 3%;  animation-delay: 6s; transform: rotate(-12deg); }
.orn-6 { bottom: 6%; left: 12%; animation-delay: 4s; }

/* 右侧 */
.orn-4 { top: 18%;   right: 5%; animation-delay: 2s; }
.orn-5 { top: 52%;   right: 4%; animation-delay: 5s; }
.orn-7 { top: 78%;   right: 9%; animation-delay: 7s; }
.orn-8 { bottom: 12%; right: 3%; animation-delay: 1s; transform: rotate(8deg); }

@keyframes orn-drift {
  from { transform: translateY(0) rotate(0deg); }
  to   { transform: translateY(-12px) rotate(4deg); }
}

/* 小屏关掉这些大涂鸦，免得挤压内容 */
@media (max-width: 1100px) {
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
