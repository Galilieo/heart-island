<script setup>
// 登录／注册页共用的居中单栏布局。
// 从上到下：吉祥物 → 标语（页面主标题）→ 副标题 → 表单卡 → 隐私说明。
// 宽屏和窄屏用同一结构，只收紧尺寸，不再切换两栏／堆叠两套布局。
import Doodle from '../ui/Doodle.vue'
import logoUrl from '../../assets/xiaoya/xinyu-mascot.svg'
import logoDarkUrl from '../../assets/xiaoya/xinyu-mascot-dark.svg'
import { useTheme } from '../../composables/useTheme'

const { theme } = useTheme()

defineProps({
  // 页面主标题，用标题字体展示
  slogan: { type: String, required: true },
  subtitle: String,
  // 表单卡标题，说明当前是登录还是注册
  title: { type: String, required: true }
})
</script>

<template>
  <div class="auth">
    <!-- 背景装饰只保留三处同色系涂鸦，避免和表单争夺注意力 -->
    <div class="auth__doodles" aria-hidden="true">
      <Doodle name="cloud" :size="72" color="rgba(111,169,164,0.32)" class="d d-1" />
      <Doodle name="leaf" :size="52" color="rgba(111,169,164,0.28)" class="d d-2" />
      <Doodle name="wave" :size="110" color="rgba(111,169,164,0.24)" class="d d-3" />
    </div>

    <div class="auth__column float-in">
      <header class="auth__intro">
        <img :src="theme === 'night' ? logoDarkUrl : logoUrl" alt="心屿" class="auth__logo" />
        <h1 class="auth__slogan">{{ slogan }}</h1>
        <p v-if="subtitle" class="auth__subtitle">{{ subtitle }}</p>
      </header>

      <section class="auth__card" :aria-label="title">
        <h2 class="auth__title">{{ title }}</h2>

        <div class="auth__body">
          <slot />
        </div>

        <div v-if="$slots.footer" class="auth__footer">
          <slot name="footer" />
        </div>
      </section>

      <div class="auth__legal">
        <slot name="legal">
          <RouterLink to="/privacy">隐私政策</RouterLink>
        </slot>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 整页暖色画布，内容列水平、垂直居中 */
.auth {
  position: relative;
  min-height: inherit;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  overflow: hidden;
  background:
    radial-gradient(circle at 18% 20%, rgba(111, 169, 164, 0.18), transparent 46%),
    radial-gradient(circle at 84% 78%, rgba(217, 140, 122, 0.12), transparent 50%),
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
.d-1 { top: 10%; left: 12%; }
.d-2 { bottom: 16%; left: 18%; animation-delay: 1.2s; }
.d-3 { top: 18%; right: 12%; animation-delay: 2.4s; }

@keyframes drift {
  from { transform: translateY(0) rotate(-2deg); }
  to   { transform: translateY(-10px) rotate(2deg); }
}

.auth__column {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.auth__intro {
  text-align: center;
}

.auth__logo {
  display: block;
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  object-fit: contain;
}

/* 霞鹜文楷只有常规字重，标语靠字号和字形区分层级 */
.auth__slogan {
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
  line-height: 1.35;
  letter-spacing: 1px;
  /* 标语换行时平衡两行长度，避免末尾一两个字单独成行 */
  text-wrap: balance;
}

.auth__subtitle {
  margin-top: 10px;
  color: var(--ink-2);
  font-size: var(--fs-md);
  line-height: 1.7;
}

.auth__card {
  padding: 28px 28px 22px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  box-shadow: var(--shadow-md);
}

.auth__title {
  font-family: var(--font-sans);
  font-size: var(--fs-lg);
  font-weight: 700;
  letter-spacing: 0;
}

.auth__body {
  margin-top: 20px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.auth__footer {
  margin-top: 14px;
  text-align: center;
}

.auth__legal {
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.auth__legal :deep(a) {
  color: var(--ink-2);
  text-decoration: underline;
  text-underline-offset: 3px;
}

/* 窄屏：结构不变，收紧尺寸，目标是 390×844 内注册页完整落进首屏附近 */
@media (max-width: 768px) {
  .auth {
    align-items: flex-start;
    padding: 28px 16px 40px;
  }
  .auth__column { gap: 18px; }
  .auth__logo {
    width: 52px;
    height: 52px;
    margin-bottom: 10px;
  }
  /* 22px 下最长的注册标语（13 字）在 375px 宽度内可单行显示 */
  .auth__slogan { font-size: var(--fs-xl); }
  .auth__subtitle {
    margin-top: 6px;
    font-size: var(--fs-sm);
  }
  .auth__card { padding: 22px 20px 18px; }
  .auth__body {
    margin-top: 16px;
    gap: 12px;
  }
  /* 窄屏空间不足，涂鸦会压到标语和卡片，直接隐藏 */
  .auth__doodles { display: none; }
}
</style>
