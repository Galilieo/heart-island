export const MOOD_OPTIONS = [
  { label: '开心', value: '开心' },
  { label: '平静', value: '平静' },
  { label: '焦虑', value: '焦虑' },
  { label: '难过', value: '难过' },
  { label: '疲惫', value: '疲惫' },
  { label: '其他', value: '其他' }
]

const TONE_MAP = {
  开心: 'joy',
  平静: 'calm',
  焦虑: 'warn',
  难过: 'sad',
  疲惫: 'tired',
  其他: 'neutral'
}

export function moodTone(type) {
  return TONE_MAP[type] || 'neutral'
}

export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}
