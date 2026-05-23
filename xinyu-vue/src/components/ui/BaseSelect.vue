<script setup>
// 下拉选择框。options 形如 [{label, value}]。
// 不使用原生 <select>，而是自建按钮触发 + 浮层面板，确保跨平台外观一致、
// 和设计系统（奶油底 + 薄荷青聚焦）统一。
//
// 外部 API 与旧版完全一致：
//   props:  modelValue / options / placeholder / label / disabled / size
//   emits:  update:modelValue / change
import { ref, computed, onBeforeUnmount, watch, nextTick } from 'vue'

const props = defineProps({
  modelValue: { type: [String, Number, null], default: '' },
  options: { type: Array, default: () => [] }, // [{label, value}]
  placeholder: { type: String, default: '请选择' },
  label: String,
  disabled: Boolean,
  size: { type: String, default: 'md' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const open = ref(false)
const triggerRef = ref(null)
const panelRef = ref(null)
const activeIndex = ref(-1)
const panelStyle = ref({})

const selected = computed(() =>
  props.options.find((o) => String(o.value) === String(props.modelValue))
)

const displayText = computed(() => {
  if (selected.value) return selected.value.label
  return ''
})

function toggle() {
  if (props.disabled) return
  if (open.value) close()
  else show()
}

function show() {
  open.value = true
  activeIndex.value = props.options.findIndex(
    (o) => String(o.value) === String(props.modelValue)
  )
  nextTick(positionPanel)
  document.addEventListener('mousedown', onOutsideClick)
  window.addEventListener('resize', positionPanel)
  window.addEventListener('scroll', positionPanel, true)
}

function close() {
  open.value = false
  document.removeEventListener('mousedown', onOutsideClick)
  window.removeEventListener('resize', positionPanel)
  window.removeEventListener('scroll', positionPanel, true)
}

function pick(opt) {
  emit('update:modelValue', opt.value)
  emit('change', opt.value)
  close()
}

function onOutsideClick(e) {
  if (triggerRef.value?.contains(e.target)) return
  if (panelRef.value?.contains(e.target)) return
  close()
}

function positionPanel() {
  const el = triggerRef.value
  if (!el) return
  const rect = el.getBoundingClientRect()
  const gap = 6
  const maxPanelHeight = 280
  const spaceBelow = window.innerHeight - rect.bottom - gap
  const showAbove = spaceBelow < 180 && rect.top > spaceBelow

  panelStyle.value = {
    position: 'fixed',
    left: `${rect.left}px`,
    width: `${rect.width}px`,
    top: showAbove
      ? `${rect.top - gap - Math.min(maxPanelHeight, rect.top - 8)}px`
      : `${rect.bottom + gap}px`,
    maxHeight: showAbove
      ? `${Math.min(maxPanelHeight, rect.top - 8)}px`
      : `${Math.min(maxPanelHeight, spaceBelow - 8)}px`
  }
}

function onKey(e) {
  if (props.disabled) return
  if (!open.value) {
    if (e.key === 'Enter' || e.key === ' ' || e.key === 'ArrowDown') {
      e.preventDefault()
      show()
    }
    return
  }
  if (e.key === 'Escape') {
    e.preventDefault()
    close()
  } else if (e.key === 'ArrowDown') {
    e.preventDefault()
    activeIndex.value = Math.min(props.options.length - 1, activeIndex.value + 1)
  } else if (e.key === 'ArrowUp') {
    e.preventDefault()
    activeIndex.value = Math.max(0, activeIndex.value - 1)
  } else if (e.key === 'Enter') {
    e.preventDefault()
    const opt = props.options[activeIndex.value]
    if (opt) pick(opt)
  }
}

watch(
  () => props.disabled,
  (v) => {
    if (v && open.value) close()
  }
)

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', onOutsideClick)
  window.removeEventListener('resize', positionPanel)
  window.removeEventListener('scroll', positionPanel, true)
})
</script>

<template>
  <div class="field" :class="{ 'is-disabled': disabled }">
    <span v-if="label" class="field__label">{{ label }}</span>

    <button
      ref="triggerRef"
      type="button"
      class="field__control"
      :class="[`field__control--${size}`, { 'is-open': open }]"
      :disabled="disabled"
      :aria-haspopup="'listbox'"
      :aria-expanded="open"
      @click="toggle"
      @keydown="onKey"
    >
      <span class="field__value" :class="{ 'is-placeholder': !selected }">
        {{ displayText || placeholder }}
      </span>
      <span class="field__caret" :class="{ 'is-open': open }" aria-hidden="true">▾</span>
    </button>

    <Teleport to="body">
      <transition name="bs-pop">
        <ul
          v-if="open"
          ref="panelRef"
          class="bs-panel"
          :style="panelStyle"
          role="listbox"
        >
          <li
            v-for="(opt, i) in options"
            :key="String(opt.value)"
            class="bs-option"
            :class="{
              'is-selected': String(opt.value) === String(modelValue),
              'is-active': i === activeIndex
            }"
            role="option"
            :aria-selected="String(opt.value) === String(modelValue)"
            @mouseenter="activeIndex = i"
            @mousedown.prevent="pick(opt)"
          >
            <span class="bs-option__text">{{ opt.label }}</span>
            <span v-if="String(opt.value) === String(modelValue)" class="bs-option__check">✓</span>
          </li>
          <li v-if="!options.length" class="bs-empty">无可选项</li>
        </ul>
      </transition>
    </Teleport>
  </div>
</template>

<style scoped>
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
}

.field__label {
  font-size: var(--fs-sm);
  font-weight: 600;
  color: var(--ink-2);
  letter-spacing: 0.5px;
}

.field__control {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  width: 100%;
  padding: 0 14px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  color: var(--ink-1);
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft);
}

.field__control:focus-visible {
  outline: none;
}

.field__control:focus-visible,
.field__control.is-open {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

.field__control--sm { height: 34px; font-size: var(--fs-sm); }
.field__control--md { height: 42px; }
.field__control--lg { height: 48px; font-size: var(--fs-lg); }

.field.is-disabled .field__control,
.field__control:disabled {
  background: var(--bg-soft);
  color: var(--ink-3);
  cursor: not-allowed;
}

.field__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.field__value.is-placeholder {
  color: var(--ink-3);
}

.field__caret {
  color: var(--ink-3);
  font-size: 11px;
  transition: transform var(--t-fast) var(--ease-soft);
}

.field__caret.is-open {
  transform: rotate(180deg);
  color: var(--brand-deep);
}
</style>

<style>
/* 浮层走 body 挂载，不走 scoped */
.bs-panel {
  list-style: none;
  margin: 0;
  padding: 6px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  box-shadow: var(--shadow-md);
  overflow-y: auto;
  z-index: var(--z-dialog);
  font-family: inherit;
}

.bs-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 8px 10px;
  border-radius: var(--radius-1);
  color: var(--ink-1);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.bs-option.is-active,
.bs-option:hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.bs-option.is-selected {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}

.bs-option.is-selected.is-active {
  background: #d6e8e5;
}

.bs-option__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bs-option__check {
  color: var(--brand);
  font-size: 12px;
  flex-shrink: 0;
}

.bs-empty {
  padding: 12px;
  text-align: center;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.bs-pop-enter-from,
.bs-pop-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.bs-pop-enter-active,
.bs-pop-leave-active {
  transition: opacity var(--t-fast) var(--ease-soft),
    transform var(--t-fast) var(--ease-soft);
  transform-origin: top;
}
</style>
