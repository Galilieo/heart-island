<script setup>
import { watch, onUnmounted } from 'vue'

const props = defineProps({
  modelValue: Boolean,
  title: String,
  width: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg', 'xl'].includes(v)
  },
  closable: { type: Boolean, default: true },
  persistent: Boolean
})

const emit = defineEmits(['update:modelValue', 'close'])

function close() {
  if (!props.closable) return
  emit('update:modelValue', false)
  emit('close')
}

function onKeydown(e) {
  if (e.key === 'Escape' && !props.persistent) close()
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      document.addEventListener('keydown', onKeydown)
      document.body.style.overflow = 'hidden'
    } else {
      document.removeEventListener('keydown', onKeydown)
      document.body.style.overflow = ''
    }
  }
)

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <transition name="dlg">
      <div
        v-if="modelValue"
        class="dlg-mask"
        role="dialog"
        aria-modal="true"
        @click.self="persistent ? null : close()"
      >
        <div class="dlg-card" :class="`dlg-card--${width}`">
          <header v-if="title || closable" class="dlg-head">
            <h3 v-if="title">{{ title }}</h3>
            <button
              v-if="closable"
              type="button"
              class="dlg-close"
              aria-label="关闭"
              @click="close"
            >×</button>
          </header>
          <div class="dlg-body"><slot /></div>
          <footer v-if="$slots.footer" class="dlg-foot"><slot name="footer" /></footer>
        </div>
      </div>
    </transition>
  </Teleport>
</template>

<style scoped>
.dlg-mask {
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

.dlg-card {
  width: 100%;
  max-height: calc(100vh - 48px);
  background: var(--bg-card);
  border-radius: var(--radius-3);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: dlg-pop var(--t-base) var(--ease-bounce);
}

.dlg-card--sm { max-width: 380px; }
.dlg-card--md { max-width: 520px; }
.dlg-card--lg { max-width: 720px; }
.dlg-card--xl { max-width: 920px; }

.dlg-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px 12px;
  border-bottom: 1px dashed var(--line-strong);
}

.dlg-head h3 {
  font-size: var(--fs-xl);
  margin: 0;
}

.dlg-close {
  width: 32px;
  height: 32px;
  border: none;
  background: var(--bg-soft);
  color: var(--ink-2);
  border-radius: 50%;
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft);
}
.dlg-close:hover {
  background: var(--line);
  color: var(--ink-1);
}

.dlg-body {
  padding: 20px 24px;
  overflow-y: auto;
  flex: 1;
}

.dlg-foot {
  padding: 12px 24px 20px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  border-top: 1px dashed var(--line-strong);
}

.dlg-enter-from,
.dlg-leave-to {
  opacity: 0;
}
.dlg-enter-active,
.dlg-leave-active {
  transition: opacity var(--t-base) var(--ease-soft);
}

@keyframes dlg-pop {
  from { transform: translateY(8px) scale(0.96); opacity: 0; }
  to   { transform: translateY(0) scale(1); opacity: 1; }
}
</style>
