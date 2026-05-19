// 心情相关的共用常量与工具函数。
// 各处用到心情选项、颜色映射、时间格式化都从这里拿，保证一致。

// 心情类型选项（与后端约定的字符串值保持一致）
export const MOOD_OPTIONS = [
  { label: '开心', value: '开心' },
  { label: '平静', value: '平静' },
  { label: '焦虑', value: '焦虑' },
  { label: '难过', value: '难过' },
  { label: '疲惫', value: '疲惫' },
  { label: '其他', value: '其他' }
]

// 心情 → BaseTag 的 tone 名（对应 tokens.css 里的颜色变量）
const TONE_MAP = {
  开心: 'joy',
  平静: 'calm',
  焦虑: 'warn',
  难过: 'sad',
  疲惫: 'tired',
  其他: 'neutral'
}

// 根据心情类型拿对应配色名，未知类型返回中性色
export function moodTone(type) {
  return TONE_MAP[type] || 'neutral'
}

// 后端返回的时间字符串 → 「YYYY-MM-DD HH:mm」展示格式
export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
