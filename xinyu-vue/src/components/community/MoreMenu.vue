<script setup>
import { ref, nextTick, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  reportedStatus: { type: String, default: '' },
  disabled: Boolean,
  label: { type: String, default: '更多操作' }
})

const emit = defineEmits(['report'])

const open = ref(false)
const wrapRef = ref(null)
const triggerRef = ref(null)
const itemRef = ref(null)

function toggleMenu() {
  if (props.disabled) {
    return
  }
  open.value = !open.value
  if (open.value) {
    nextTick(() => itemRef.value?.focus())
  }
}

function closeMenu({ restoreFocus = false } = {}) {
  if (!open.value) {
    return
  }
  open.value = false
  if (restoreFocus) {
    nextTick(() => triggerRef.value?.focus())
  }
}

function handleReportClick() {
  if (props.reportedStatus) {
    return
  }
  open.value = false
  // 弹窗关闭后需把焦点退回持久存在的触发按钮，而不是即将卸载的菜单项
  triggerRef.value?.focus()
  emit('report')
}

function onDocumentClick(event) {
  if (open.value && wrapRef.value && !wrapRef.value.contains(event.target)) {
    open.value = false
  }
}

function onMenuKeydown(event) {
  if (event.key === 'Escape') {
    event.stopPropagation()
    closeMenu({ restoreFocus: true })
  }
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
})

onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <div ref="wrapRef" class="more-menu" @click.stop>
    <button
      ref="triggerRef"
      type="button"
      class="more-menu__trigger"
      :aria-label="label"
      aria-haspopup="menu"
      :aria-expanded="open"
      :disabled="disabled"
      @click="toggleMenu"
    >
      ⋯
    </button>

    <div v-if="open" class="more-menu__pop" role="menu" @keydown="onMenuKeydown">
      <button
        ref="itemRef"
        type="button"
        role="menuitem"
        class="more-menu__item"
        :class="{ 'is-reported': Boolean(reportedStatus) }"
        :aria-disabled="Boolean(reportedStatus)"
        :disabled="Boolean(reportedStatus)"
        @click="handleReportClick"
      >
        <template v-if="reportedStatus === 'PENDING'">✓ 已举报 · 处理中</template>
        <template v-else-if="reportedStatus">✓ 已举报</template>
        <template v-else>举报</template>
      </button>
    </div>
  </div>
</template>

<style scoped>
.more-menu {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.more-menu__trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  min-height: 34px;
  padding: 0 6px;
  border: 1px solid transparent;
  border-radius: var(--radius-2);
  background: transparent;
  color: var(--ink-3);
  font-size: 16px;
  line-height: 1;
  cursor: pointer;
  transition:
    background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.more-menu__trigger:hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.more-menu__trigger:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.more-menu__pop {
  position: absolute;
  top: calc(100% + 4px);
  right: 0;
  z-index: 30;
  min-width: 152px;
  padding: 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
}

.more-menu__item {
  display: flex;
  align-items: center;
  width: 100%;
  min-height: 38px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-1);
  background: transparent;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  font-weight: 500;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
}

.more-menu__item:hover:not(:disabled) {
  background: var(--bg-soft);
  color: var(--danger-ink);
}

.more-menu__item:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 1px;
}

.more-menu__item.is-reported,
.more-menu__item:disabled {
  color: var(--ink-3);
  cursor: default;
}
</style>
