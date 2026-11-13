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
