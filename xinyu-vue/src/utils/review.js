// 回顾页的日期与统计展示工具。日期键统一为 YYYY-MM-DD，
// 按本地日历解析，不经过 UTC，避免东八区跨日偏移。
const MONDAY_FIRST_WEEKDAYS = ['一', '二', '三', '四', '五', '六', '日']

export function parseDateKey(dateKey) {
  const [year, month, day] = dateKey.split('-').map(Number)
  return new Date(year, month - 1, day)
}

export function toDateKey(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

export function addDays(dateKey, days) {
  const date = parseDateKey(dateKey)
  date.setDate(date.getDate() + days)
  return toDateKey(date)
}

/** 返回该日期所在周的周一（YYYY-MM-DD）。 */
export function mondayOf(dateKey) {
  const date = parseDateKey(dateKey)
  const offset = (date.getDay() + 6) % 7
  date.setDate(date.getDate() - offset)
  return toDateKey(date)
}

export function shiftWeek(startKey, delta) {
  return addDays(startKey, delta * 7)
}

export function formatShortDate(dateKey) {
  const [, month, day] = dateKey.split('-').map(Number)
  return `${month}/${day}`
}

/** 范围展示为 [start, endExclusive)，所以结束日显示为 endExclusive 前一天。 */
export function formatRangeShort(startKey, endExclusiveKey) {
  return `${formatShortDate(startKey)} – ${formatShortDate(addDays(endExclusiveKey, -1))}`
}

export function formatRangeLong(startKey, endExclusiveKey) {
  const [startYear, startMonth, startDay] = startKey.split('-').map(Number)
  const [endYear, endMonth, endDay] = addDays(endExclusiveKey, -1).split('-').map(Number)
  const start = `${startMonth} 月 ${startDay} 日`
  const end = startYear === endYear ? `${endMonth} 月 ${endDay} 日` : `${endYear} 年 ${endMonth} 月 ${endDay} 日`
  return `${start} – ${end}`
}

/** 周视图的 7 个格子；接口没返回的日期补空，保留日期顺序。 */
export function buildWeekCells(startKey, days) {
  const moodsByDate = new Map((days || []).map((day) => [day.date, day.moods || []]))
  const cells = []

  for (let index = 0; index < 7; index++) {
    const dateKey = addDays(startKey, index)
    const date = parseDateKey(dateKey)
    cells.push({
      date: dateKey,
      weekday: MONDAY_FIRST_WEEKDAYS[index],
      day: date.getDate(),
      moods: moodsByDate.get(dateKey) || []
    })
  }

  return cells
}

/** 近 30 天按自然周分组；首尾允许是不完整周，当前周加「（本周）」后缀。 */
export function groupDaysIntoWeeks(days, todayKey) {
  const currentMonday = mondayOf(todayKey)
  const groups = new Map()

  for (const day of days || []) {
    const startKey = mondayOf(day.date)
    if (!groups.has(startKey)) {
      groups.set(startKey, { startKey, days: [] })
    }
    groups.get(startKey).days.push(day)
  }

  return [...groups.values()]
    .sort((left, right) => left.startKey.localeCompare(right.startKey))
    .map((group) => {
      const endExclusive = addDays(group.startKey, 7)
      const isCurrent = group.startKey === currentMonday
      return {
        ...group,
        endKey: addDays(endExclusive, -1),
        label: `${formatRangeShort(group.startKey, endExclusive)}${isCurrent ? '（本周）' : ''}`,
        total: group.days.reduce((sum, day) => sum + (day.moods || []).length, 0),
        isCurrent
      }
    })
}

/** 只有下一周仍是已结束的完整周时，才能点「下一周」。 */
export function canGoNextWeek(startKey, todayKey) {
  return shiftWeek(startKey, 1) < mondayOf(todayKey)
}

const REF_TOKEN_PATTERN = /\{\{(r\d+)\}\}/g
const REF_RUN_PATTERN = /(?:\{\{(r\d+)\}\})+/g
const TRAILING_PUNCTUATION_PATTERN = /^[，。！？、；：,.!?;:]/

function stripUnknownRefs(text, sourceMap, warn) {
  let result = ''
  let lastIndex = 0

  for (const match of text.matchAll(REF_TOKEN_PATTERN)) {
    const start = match.index
    const ref = match[1]
    result += text.slice(lastIndex, start)
    if (sourceMap.has(ref)) {
      result += match[0]
    } else {
      warn(`回信引用了未知编号 {{${ref}}}`)
    }
    lastIndex = start + match[0].length
  }

  result += text.slice(lastIndex)
  return result
}

function splitLeadingPrefix(text) {
  const chars = Array.from(text)
  if (chars.length === 0) {
    return { head: '', prefix: '' }
  }

  const lastChar = chars[chars.length - 1]
  if (lastChar === '\n' || lastChar === '\r') {
    return { head: text, prefix: '' }
  }

  return {
    head: chars.slice(0, -1).join(''),
    prefix: lastChar
  }
}

function appendTextSegment(segments, text) {
  if (!text) {
    return
  }

  const previous = segments[segments.length - 1]
  if (previous && previous.type === 'text') {
    previous.text += text
    return
  }

  segments.push({ type: 'text', text })
}

/**
 * 解析回信正文或 P.S. 中的 {{rN}} 引用标记，
 * 把前置单个字符、相邻引用与后接单个标点打包成 refGroup 避免行尾悬空。
 */
export function parseLetterSegments(text, sources = [], options = {}) {
  const rawText = String(text || '')
  if (!rawText) {
    return []
  }

  const sourceMap = new Map()
  for (const source of sources || []) {
    if (source && source.ref) {
      sourceMap.set(source.ref, source)
    }
  }

  const warn = options.onWarn || console.warn
  const cleaned = stripUnknownRefs(rawText, sourceMap, warn)
  const segments = []
  let cursor = 0

  for (const match of cleaned.matchAll(REF_RUN_PATTERN)) {
    const runStart = match.index
    const runEnd = runStart + match[0].length
    const beforeRun = cleaned.slice(cursor, runStart)
    const { head, prefix } = splitLeadingPrefix(beforeRun)

    appendTextSegment(segments, head)

    const refs = []
    for (const token of match[0].matchAll(REF_TOKEN_PATTERN)) {
      const source = sourceMap.get(token[1])
      if (source) {
        refs.push({
          ref: source.ref,
          recordId: String(source.recordId),
          label: formatShortDate(source.date)
        })
      }
    }

    const afterRun = cleaned.slice(runEnd)
    const punctuationMatch = afterRun.match(TRAILING_PUNCTUATION_PATTERN)
    const punctuation = punctuationMatch ? punctuationMatch[0] : ''

    segments.push({
      type: 'refGroup',
      prefix,
      refs,
      punctuation
    })

    cursor = runEnd + punctuation.length
  }

  appendTextSegment(segments, cleaned.slice(cursor))
  return segments
}

/** 根据后端返回的统计与任务状态，映射为前端回信区单一视图状态。 */
export function resolveReviewViewState(pageData, options = {}) {
  const mode = options.mode ?? 'week'
  const isCurrentWeek = Boolean(options.isCurrentWeek)
  const pollingTimedOut = Boolean(options.pollingTimedOut)

  if (mode === 'week' && isCurrentWeek) {
    return 'week_not_finished'
  }
  if (!pageData) {
    return 'empty'
  }
  if (pageData.active) {
    return pollingTimedOut ? 'polling_timeout' : 'generating'
  }

  const total = Number(pageData.total || 0)
  if (total === 0) {
    return 'empty'
  }
  if (pageData.failed) {
    return pageData.report && pageData.report.body ? 'failed_with_report' : 'failed_empty'
  }

  if (pageData.report) {
    if (pageData.report.stale) {
      return pageData.report.body ? 'stale_edited' : 'stale_deleted'
    }
    if (pageData.report.resultKind === 'SUPPORT') {
      return 'crisis'
    }
    return 'ready'
  }

  const aiEnabledCount = Number(pageData.aiEnabledCount ?? total)
  if (aiEnabledCount === 0) {
    return 'no_ai_enabled'
  }

  return 'not_generated'
}

/** YYYY-MM-DD 转为中文“M 月 D 日”日期文本，供引用按钮无障碍标签与原文浮层共用。 */
export function formatChineseDate(dateKey) {
  if (!dateKey || !/^\d{4}-\d{2}-\d{2}$/.test(String(dateKey))) {
    return ''
  }
  const [, month, day] = String(dateKey).split('-').map(Number)
  return `${month} 月 ${day} 日`
}

/** 计算桌面端引用原文浮层相对于信纸容器的位置，并在贴近视口底边时翻转到上方。 */
export function computeCitationPopoverLayout({ letterRect, tagRect, popWidth, popHeight, viewportHeight }) {
  const tagCenter = tagRect.left + tagRect.width / 2
  const minLeft = letterRect.left + 16
  const maxLeft = Math.max(minLeft, letterRect.right - popWidth - 16)
  const left = Math.min(Math.max(tagCenter - 30, minLeft), maxLeft)
  const roomBelow = viewportHeight - tagRect.bottom
  const above = roomBelow < popHeight + 24 && tagRect.top > popHeight + 24

  return {
    left: left - letterRect.left,
    top: above ? tagRect.top - letterRect.top - popHeight - 12 : tagRect.bottom - letterRect.top + 12,
    above,
    arrowX: tagCenter - left - 6
  }
}

/** 从路由 query.record 中提取正整数记录 ID，非法值返回 null。 */
export function parseRecordQueryParam(value) {
  const raw = Array.isArray(value) ? value[0] : value
  const text = String(raw || '').trim()
  return /^[1-9]\d*$/.test(text) ? text : null
}

/** 生成去掉 record 参数的 query 浅拷贝，用于首页打开详情后清理 URL。 */
export function stripRecordQuery(query = {}) {
  const next = { ...query }
  delete next.record
  return next
}

