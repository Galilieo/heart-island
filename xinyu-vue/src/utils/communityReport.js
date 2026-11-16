// 社区举报与审核共用的原因字典、状态文案映射及表单校验规则。
// 对应后端 CommunityReportConstants 与原型 4 个画面。

export const REPORT_REASONS = [
  { value: 'ATTACK', label: '人身攻击或骚扰' },
  { value: 'AD', label: '广告或引流' },
  { value: 'PRIVACY', label: '泄露他人隐私' },
  { value: 'ILLEGAL', label: '色情、暴力或违法内容' },
  { value: 'SAFETY', label: '担心发帖人的安全' },
  { value: 'OTHER', label: '其他' }
]

export const HIDE_REASONS = REPORT_REASONS.slice(0, 4)

export const MAX_REPORT_DETAIL_LENGTH = 200
export const MAX_HANDLE_NOTE_LENGTH = 200

const REASON_MAP = Object.fromEntries(REPORT_REASONS.map((item) => [item.value, item.label]))

export function reasonLabel(code) {
  return REASON_MAP[code] || code || '-'
}

export function myReportResultMeta(status) {
  if (status === 'PENDING') {
    return { text: '处理中', tone: 'warn' }
  }
  if (status === 'HIDDEN') {
    return { text: '已处理 · 内容已隐藏', tone: 'neutral' }
  }
  if (status === 'DISMISSED') {
    return { text: '已处理 · 未发现违规', tone: 'success' }
  }
  if (status === 'CLOSED') {
    return { text: '已处理 · 内容已被作者删除', tone: 'neutral' }
  }
  return { text: status || '-', tone: 'neutral' }
}

export function adminReportResultMeta(status, hideReason) {
  if (status === 'HIDDEN') {
    return {
      text: hideReason ? `已隐藏 · ${reasonLabel(hideReason)}` : '已隐藏',
      tone: 'bad'
    }
  }
  if (status === 'DISMISSED') {
    return { text: '未发现违规', tone: 'ok' }
  }
  if (status === 'CLOSED') {
    return { text: '内容已删除', tone: 'muted' }
  }
  return { text: '待处理', tone: 'bad' }
}

export function contentStatusMeta(status) {
  if (status === 1) {
    return { text: '正常', tone: 'ok', tagTone: 'success' }
  }
  if (status === 2) {
    return { text: '已隐藏', tone: 'bad', tagTone: 'danger' }
  }
  return { text: '作者已删除', tone: 'muted', tagTone: 'neutral' }
}

export function validateReportForm({ reason, detail }) {
  if (!reason) {
    return '请选择举报原因'
  }
  const trimmed = (detail || '').trim()
  if (reason === 'OTHER' && !trimmed) {
    return '选「其他」时请写一句原因'
  }
  if (trimmed.length > MAX_REPORT_DETAIL_LENGTH) {
    return `补充说明不能超过 ${MAX_REPORT_DETAIL_LENGTH} 字`
  }
  return ''
}

export function formatReportTime(value) {
  if (!value) return '-'
  const text = String(value).replace('T', ' ')
  return text.length >= 16 ? text.slice(5, 16) : text
}

export function formatFullDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}
