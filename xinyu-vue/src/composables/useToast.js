import { reactive } from 'vue'

const state = reactive({
  items: []
})

let seq = 0

function push(type, message, duration = 2400) {
  const id = ++seq
  state.items.push({ id, type, message })

  if (duration > 0) {
    setTimeout(() => dismiss(id), duration)
  }

  return id
}

function dismiss(id) {
  const idx = state.items.findIndex((t) => t.id === id)
  if (idx !== -1) state.items.splice(idx, 1)
}

const toast = {
  success: (msg, duration) => push('success', msg, duration),
  error: (msg, duration) => push('error', msg, duration),
  warn: (msg, duration) => push('warn', msg, duration),
  info: (msg, duration) => push('info', msg, duration),
  dismiss
}

export function useToast() {
  return toast
}

// 给 Toast 组件用，view 不要直接读
export function _toastState() {
  return state
}
