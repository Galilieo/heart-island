/** 保存成功后交给提交者的支持文案。开启 AI 的帖子由公开 SUPPORT 回复呈现。 */
export function communitySupportText(result, needAiReply = false) {
  if (!result?.ok || needAiReply) {
    return null
  }

  const text = result.data?.supportText
  return typeof text === 'string' && text.length > 0 ? text : null
}

export const PENDING_RISK = 'PENDING'

const riskStatuses = {
  [PENDING_RISK]: { text: '待关注', tone: 'bad' },
  REVIEWED: { text: '已关注', tone: 'ok' }
}

export const RISK_STATUS_OPTIONS = [
  { label: '全部关注状态', value: '' },
  ...Object.entries(riskStatuses).map(([value, status]) => ({ label: status.text, value }))
]

export function riskStatusMeta(status) {
  return typeof status === 'string' && Object.hasOwn(riskStatuses, status)
    ? riskStatuses[status]
    : null
}
