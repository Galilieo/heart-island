<script setup>
import { _confirmState } from '../../composables/useConfirm'

const { state, accept, reject } = _confirmState()
</script>

<template>
  <Teleport to="body">
    <transition name="confirm">
      <div
        v-if="state.open"
        class="confirm-mask"
        role="dialog"
        aria-modal="true"
        @click.self="reject"
      >
        <div class="confirm-card float-in">
          <h3 class="confirm-title">{{ state.title }}</h3>
          <p v-if="state.content" class="confirm-body">{{ state.content }}</p>
          <div class="confirm-actions">
            <button class="btn-plain" type="button" @click="reject">
              {{ state.cancelText }}
            </button>
            <button
              type="button"
              :class="state.tone === 'danger' ? 'btn-danger' : 'btn-primary'"
              @click="accept"
            >
              {{ state.confirmText }}
            </button>
          </div>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: var(--z-dialog);
  background: var(--bg-overlay);
  backdrop-filter: blur(2px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}

.confirm-card {
  width: 100%;
  max-width: 360px;
  padding: 24px;
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-lg);
  border: 1px solid var(--line);
}

.confirm-title {
  font-size: var(--fs-xl);
  margin-bottom: 8px;
}

.confirm-body {
  color: var(--ink-2);
  margin-bottom: 20px;
  line-height: 1.7;
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-actions button {
  height: 40px;
  padding: 0 18px;
  border: none;
  border-radius: var(--radius-2);
  font-size: var(--fs-md);
  font-weight: 600;
  transition: transform var(--t-fast) var(--ease-soft),
    background var(--t-fast) var(--ease-soft);
}

.btn-plain {
  background: var(--bg-soft);
  color: var(--ink-2);
}
.btn-plain:hover {
  background: var(--line);
}

.btn-primary {
  background: var(--brand);
  color: #fff;
  box-shadow: 0 8px 20px rgba(111, 169, 164, 0.3);
}
.btn-primary:hover {
  background: var(--brand-deep);
}

.btn-danger {
  background: var(--danger);
  color: #fff;
  box-shadow: 0 8px 20px rgba(198, 106, 106, 0.28);
}
.btn-danger:hover {
  background: #b15555;
}

.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
}
.confirm-enter-active,
.confirm-leave-active {
  transition: opacity var(--t-base) var(--ease-soft);
}
</style>
