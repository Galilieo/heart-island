<script setup>
// 登录/注册页共用的两栏布局。
// 整页一张暖色画布：渐变 + 漂浮涂鸦铺满全屏，slogan 在左下，表单卡浮在右侧。
// 窄屏自动堆叠成单列。
import Doodle from '../ui/Doodle.vue'
import logoUrl from '../../assets/xiaoya/xinyu-mascot.svg'
import logoDarkUrl from '../../assets/xiaoya/xinyu-mascot-dark.svg'
import { useTheme } from '../../composables/useTheme'

const { theme } = useTheme()

defineProps({
  kicker: String,
  title: String,
  subtitle: String,
  slogan: { type: String, default: '今天过得怎么样？' }
})
</script>

<template>
  <div class="auth">
    <div class="auth__doodles" aria-hidden="true">
      <Doodle name="cloud" :size="80" color="rgba(111,169,164,0.4)" class="d d-1" />
      <Doodle name="sun"   :size="56" color="rgba(243,185,95,0.55)"  class="d d-2" />
      <Doodle name="leaf"  :size="60" color="rgba(155,193,188,0.55)" class="d d-3" />
      <Doodle name="wave"  :size="120" color="rgba(143,165,196,0.35)" class="d d-4" />
      <Doodle name="star"  :size="44" color="rgba(217,140,122,0.5)" class="d d-5" />
      <Doodle name="heart" :size="40" color="rgba(217,140,122,0.45)" class="d d-6" />
    </div>

    <aside class="auth__visual">
      <blockquote class="auth__slogan">
        “{{ slogan }}”
      </blockquote>
      <p class="auth__sign">— 心屿</p>
    </aside>

    <section class="auth__panel">
      <div class="auth__card float-in">
        <header class="auth__brand">
          <img :src="theme === 'night' ? logoDarkUrl : logoUrl" alt="心屿" class="auth__logo" />
        </header>

        <p v-if="kicker" class="auth__kicker">{{ kicker }}</p>
        <h1 class="auth__title">{{ title }}</h1>
        <p v-if="subtitle" class="auth__subtitle">{{ subtitle }}</p>

        <div class="auth__body">
          <slot />
        </div>

        <div v-if="$slots.footer" class="auth__footer">
          <slot name="footer" />
        </div>
      </div>
    </section>

  </div>
</template>

<style scoped>
/* 统一画布：渐变铺满整页，左右没有分界线 */
.auth {
  position: relative;
  min-height: inherit;
  display: grid;
  grid-template-columns: 1fr 1fr;
  overflow: hidden;
  background:
    radial-gradient(circle at 22% 24%, rgba(111, 169, 164, 0.20), transparent 46%),
    radial-gradient(circle at 82% 70%, rgba(217, 140, 122, 0.14), transparent 50%),
    radial-gradient(circle at 60% 8%, rgba(243, 185, 95, 0.10), transparent 42%),
    linear-gradient(135deg, var(--bg-warm-from) 0%, var(--bg-warm-mid) 55%, var(--bg-warm-to) 100%);
}

.auth__doodles {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.d {
  position: absolute;
  animation: drift 10s ease-in-out infinite alternate;
}
/* 左侧一簇 + 右侧零星两点，让两边都有呼吸感 */
.d-1 { top: 8%;  left: 6%; }
.d-2 { top: 14%; left: 26%; animation-delay: 2.4s; }
.d-3 { top: 52%; left: 4%;  animation-delay: 0.6s; }
.d-4 { bottom: 12%; left: 14%; animation-delay: 1.8s; }
.d-5 { top: 12%; right: 7%; animation-delay: 1.2s; }
.d-6 { bottom: 24%; right: 10%; animation-delay: 0.3s; }

@keyframes drift {
  from { transform: translateY(0) rotate(-2deg); }
  to   { transform: translateY(-12px) rotate(2deg); }
}

.auth__visual {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 48px;
}

.auth__slogan {
  margin: 0;
  max-width: 340px;
  color: var(--ink-1);
  font-family: var(--font-display);
  font-size: var(--fs-2xl);
  line-height: 1.45;
  font-weight: 700;
}

.auth__sign {
  margin-top: 12px;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  letter-spacing: 1px;
}

.auth__panel {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 32px;
}

.auth__card {
  width: 100%;
  max-width: 380px;
  padding: 36px 32px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: var(--shadow-lg);
}

.auth__brand {
  width: 88px;
  height: 88px;
  margin: 0 auto 18px;
}

.auth__logo {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
}

.auth__kicker {
  margin: 0;
  text-align: center;
  color: var(--brand-deep);
  font-size: var(--fs-sm);
  font-weight: 700;
  letter-spacing: 2px;
}

.auth__title {
  margin-top: 8px;
  text-align: center;
  font-size: var(--fs-3xl);
}

.auth__subtitle {
  margin: 10px 0 0;
  text-align: center;
  color: var(--ink-2);
  font-size: var(--fs-md);
  line-height: 1.7;
}

.auth__body {
  margin-top: 28px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth__footer {
  margin-top: 18px;
  text-align: center;
}

/* 窄屏：单列堆叠，slogan 区压成一条横幅，表单卡收紧，
   目标是 390×844 内注册页（3 输入框）完整落进首屏 */
@media (max-width: 768px) {
  .auth { grid-template-columns: 1fr; }
  .auth__visual {
    min-height: 148px;
    padding: 20px 24px;
  }
  .auth__slogan { font-size: var(--fs-lg); max-width: 300px; }
  .auth__sign { margin-top: 6px; }
  /* 手机上涂鸦只留左上角两个，避免和表单抢注意力 */
  .d-3, .d-4, .d-6 { display: none; }
  .auth__panel {
    padding: 20px 18px 64px;
    align-items: flex-start;
  }
  .auth__card { padding: 24px 22px; }
  .auth__brand {
    width: 44px;
    height: 44px;
    margin-bottom: 12px;
  }
  .auth__title { margin-top: 6px; font-size: var(--fs-2xl); }
  .auth__subtitle { margin-top: 6px; font-size: var(--fs-sm); }
  .auth__body { margin-top: 20px; gap: 12px; }
  .auth__footer { margin-top: 14px; }
}
</style>
