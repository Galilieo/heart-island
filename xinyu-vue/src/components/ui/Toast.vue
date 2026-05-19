<script setup>
// Toast 队列的全局渲染器。挂在 App.vue 顶层一份就够，
// 不要在多个地方挂，会渲染重复。点击 toast 可立即关闭。
import { _toastState, useToast } from '../../composables/useToast'

const state = _toastState()
const toast = useToast()
</script>

<template>
  <Teleport to="body">
    <div class="toast-stack" role="region" aria-live="polite">
      <transition-group name="toast">
        <div
          v-for="item in state.items"
          :key="item.id"
          class="toast"
          :class="`toast--${item.type}`"
          @click="toast.dismiss(item.id)"
        >
          <span class="toast__dot" aria-hidden="true" />
          <span class="toast__text">{{ item.message }}</span>
        </div>
      </transition-group>
    </div>
  </Teleport>
</template>

<style scoped>
.toast-stack {
  position: fixed;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  z-index: var(--z-toast);
  display: flex;
  flex-direction: column;
  gap: 10px;
  pointer-events: none;
}

.toast {
  pointer-events: auto;
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-width: 220px;
  max-width: min(420px, 90vw);
  padding: 12px 18px;
  border-radius: var(--radius-3);
  background: var(--bg-card);
  color: var(--ink-1);
  font-size: var(--fs-md);
  box-shadow: var(--shadow-md);
  border: 1px solid var(--line);
  cursor: pointer;
  transition: transform var(--t-fast) var(--ease-soft);
}

.toast:hover {
  transform: translateY(-1px);
}

.toast__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ink-3);
  flex-shrink: 0;
}

.toast--success .toast__dot { background: var(--success); }
.toast--error   .toast__dot { background: var(--danger); }
.toast--warn    .toast__dot { background: var(--warn); }
.toast--info    .toast__dot { background: var(--brand); }

.toast--success { border-color: var(--success-soft); }
.toast--error   { border-color: var(--danger-soft); }
.toast--warn    { border-color: var(--warn-soft); }
.toast--info    { border-color: var(--brand-soft); }

.toast-enter-from {
  opacity: 0;
  transform: translateY(-12px) scale(0.96);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}
.toast-enter-active,
.toast-leave-active {
  transition: opacity var(--t-base) var(--ease-soft),
    transform var(--t-base) var(--ease-bounce);
}
.toast-move {
  transition: transform var(--t-base) var(--ease-soft);
}
</style>
