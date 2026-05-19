// 全局 Toast 提示。模块级单例：任何组件 import 拿到的都是同一份队列。
// 用法：const toast = useToast(); toast.success('保存成功')
import { reactive } from 'vue'

// 当前正在显示的所有 toast；由 Toast.vue 渲染
const state = reactive({
  items: []
})

let seq = 0

// 新增一条 toast，duration 毫秒后自动消失；duration=0 表示不自动消失
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
