// 深夜模式：light / night 两档主题。
// 用户手动选择存 localStorage；没选过时跟随系统 prefers-color-scheme。
// initTheme() 在 main.js 挂载前调用，避免首屏先亮后暗的闪烁。
import { ref } from 'vue'

const STORAGE_KEY = 'xinyu-theme'
const theme = ref('light')

function apply() {
  document.documentElement.dataset.theme = theme.value
}

export function initTheme() {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'night') {
    theme.value = saved
  } else {
    theme.value = window.matchMedia('(prefers-color-scheme: dark)').matches
      ? 'night'
      : 'light'
  }
  apply()
}

export function useTheme() {
  function toggleTheme() {
    theme.value = theme.value === 'night' ? 'light' : 'night'
    localStorage.setItem(STORAGE_KEY, theme.value)
    apply()
  }

  return { theme, toggleTheme }
}
