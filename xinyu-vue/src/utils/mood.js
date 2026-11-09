// 心情相关的共用选项与展示工具；分组仅帮助查找，不代表情绪评分。

export const MOOD_GROUPS = [
  {
    id: 'first',
    ariaLabel: '心情选项第一组',
    options: ['开心', '满足', '期待', '感动', '平静'].map(value => ({ label: value, value }))
  },
  {
    id: 'second',
    ariaLabel: '心情选项第二组',
    options: ['焦虑', '难过', '生气', '委屈', '孤独', '迷茫', '疲惫', '其他'].map(value => ({ label: value, value }))
  }
]

// 筛选和统计沿用扁平列表；与选择器从同一份分组生成，避免选项遗漏。
export const MOOD_OPTIONS = MOOD_GROUPS.flatMap(group => group.options)

const TONE_MAP = {
  开心: 'joy', 满足: 'joy', 期待: 'joy', 感动: 'calm', 平静: 'calm',
  焦虑: 'warn', 难过: 'sad', 生气: 'warn', 委屈: 'sad', 孤独: 'sad',
  迷茫: 'tired', 疲惫: 'tired', 其他: 'neutral'
}

export function moodTone(type) {
  return TONE_MAP[type] || 'neutral'
}

// 与素材包的十三种心情一一对应；历史未知值仍保留原文字，表情使用中性默认项。
const EXPRESSION_MAP = {
  开心: 'happy', 满足: 'content', 期待: 'hopeful', 感动: 'touched', 平静: 'calm',
  焦虑: 'anxious', 难过: 'sad', 生气: 'angry', 委屈: 'wronged', 孤独: 'lonely',
  迷茫: 'lost', 疲惫: 'tired', 其他: 'other'
}

export function moodExpression(type) {
  return EXPRESSION_MAP[type] || 'other'
}

// 后端返回的时间字符串 → 「YYYY-MM-DD HH:mm」展示格式
export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
