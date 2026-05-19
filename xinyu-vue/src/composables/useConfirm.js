import { reactive } from 'vue'

const state = reactive({
  open: false,
  title: '',
  content: '',
  confirmText: '确定',
  cancelText: '取消',
  tone: 'default', // default | danger
  _resolve: null
})

function confirm(options = {}) {
  state.title = options.title || '确认操作'
  state.content = options.content || ''
  state.confirmText = options.confirmText || '确定'
  state.cancelText = options.cancelText || '取消'
  state.tone = options.tone || 'default'
  state.open = true

  return new Promise((resolve) => {
    state._resolve = resolve
  })
}

function resolveWith(value) {
  state.open = false
  if (state._resolve) {
    state._resolve(value)
    state._resolve = null
  }
}

export function useConfirm() {
  return { confirm }
}

// 给 ConfirmDialog 组件用
export function _confirmState() {
  return {
    state,
    accept: () => resolveWith(true),
    reject: () => resolveWith(false)
  }
}
