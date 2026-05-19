<script setup>
import Doodle from '../ui/Doodle.vue'
import logoUrl from '../../assets/logo-xinyu.png'

defineProps({
  kicker: String,
  title: String,
  subtitle: String,
  slogan: { type: String, default: '把今天的情绪放慢一点。' }
})
</script>

<template>
  <div class="auth">
    <aside class="auth__visual">
      <div class="auth__doodles">
        <Doodle name="cloud" :size="80" color="rgba(111,169,164,0.4)" class="d d-1" />
        <Doodle name="star"  :size="44" color="rgba(217,140,122,0.5)" class="d d-2" />
        <Doodle name="leaf"  :size="60" color="rgba(155,193,188,0.55)" class="d d-3" />
        <Doodle name="sun"   :size="56" color="rgba(243,185,95,0.55)"  class="d d-4" />
        <Doodle name="wave"  :size="120" color="rgba(143,165,196,0.35)" class="d d-5" />
        <Doodle name="heart" :size="40" color="rgba(217,140,122,0.45)" class="d d-6" />
      </div>

      <blockquote class="auth__slogan">
        “{{ slogan }}”
      </blockquote>
      <p class="auth__sign">— 心屿</p>
    </aside>

    <section class="auth__panel">
      <div class="auth__card float-in">
        <header class="auth__brand">
          <img :src="logoUrl" alt="心屿" class="auth__logo" />
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
.auth {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--bg-base);
}

.auth__visual {
  position: relative;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 48px;
  background:
    radial-gradient(circle at 30% 30%, rgba(111, 169, 164, 0.22), transparent 50%),
    radial-gradient(circle at 75% 65%, rgba(217, 140, 122, 0.18), transparent 50%),
    linear-gradient(135deg, #f6efe1 0%, #fdfaf3 60%, #f0e6d3 100%);
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
.d-1 { top: 8%;  left: 12%; }
.d-2 { top: 20%; right: 18%; animation-delay: 1.2s; }
.d-3 { top: 50%; left: 8%;  animation-delay: 0.6s; }
.d-4 { top: 18%; left: 48%; animation-delay: 2.4s; }
.d-5 { bottom: 18%; left: 22%; animation-delay: 1.8s; }
.d-6 { bottom: 36%; right: 12%; animation-delay: 0.3s; }

@keyframes drift {
  from { transform: translateY(0) rotate(-2deg); }
  to   { transform: translateY(-12px) rotate(2deg); }
}

.auth__slogan {
  position: relative;
  z-index: 1;
  margin: 0;
  max-width: 320px;
  color: var(--ink-1);
  font-family: var(--font-display);
  font-size: var(--fs-2xl);
  line-height: 1.45;
  font-weight: 700;
}

.auth__sign {
  position: relative;
  z-index: 1;
  margin-top: 12px;
  color: var(--ink-3);
  font-size: var(--fs-sm);
  letter-spacing: 1px;
}

.auth__panel {
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
  width: 56px;
  height: 56px;
  margin: 0 auto 18px;
  border-radius: var(--radius-3);
  overflow: hidden;
  box-shadow: 0 12px 28px rgba(111, 169, 164, 0.28);
}

.auth__logo {
  width: 100%;
  height: 100%;
  object-fit: cover;
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

@media (max-width: 820px) {
  .auth { grid-template-columns: 1fr; }
  .auth__visual {
    min-height: 200px;
    padding: 24px;
  }
  .auth__slogan { font-size: var(--fs-xl); }
  .auth__panel { padding: 24px 18px 36px; }
}
</style>
