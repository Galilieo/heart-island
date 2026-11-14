import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  buildWeekCells,
  canGoNextWeek,
  formatRangeLong,
  formatRangeShort,
  formatShortDate,
  groupDaysIntoWeeks,
  mondayOf,
  parseDateKey,
  parseLetterSegments,
  resolveReviewViewState,
  shiftWeek,
  toDateKey
} from '../src/utils/review.js'

test('日期键按本地日历解析，不经过 UTC 偏移', () => {
  const date = parseDateKey('2026-11-01')
  assert.equal(date.getFullYear(), 2026)
  assert.equal(date.getMonth(), 10)
  assert.equal(date.getDate(), 1)
  assert.equal(toDateKey(new Date(2026, 10, 1)), '2026-11-01')
})

test('周一计算跨月跨年都正确', () => {
  assert.equal(mondayOf('2026-11-13'), '2026-11-09')
  assert.equal(mondayOf('2026-11-09'), '2026-11-09')
  assert.equal(mondayOf('2026-11-01'), '2026-10-26')
  assert.equal(mondayOf('2026-01-01'), '2025-12-29')
})

test('上一周/下一周跨月正确', () => {
  assert.equal(shiftWeek('2026-11-02', -1), '2026-10-26')
  assert.equal(shiftWeek('2026-11-02', 1), '2026-11-09')
  assert.equal(shiftWeek('2026-01-05', -1), '2025-12-29')
})

test('范围短格式和长格式都是左闭右开', () => {
  assert.equal(formatShortDate('2026-11-02'), '11/2')
  assert.equal(formatRangeShort('2026-11-02', '2026-11-09'), '11/2 – 11/8')
  assert.equal(formatRangeLong('2026-11-02', '2026-11-09'), '11 月 2 日 – 11 月 8 日')
})

test('周格子按周一到周日排序，缺失日期补空', () => {
  const cells = buildWeekCells('2026-11-02', [
    { date: '2026-11-02', moods: ['疲惫'] },
    { date: '2026-11-03', moods: ['焦虑', '平静'] },
    { date: '2026-11-06', moods: [] }
  ])

  assert.equal(cells.length, 7)
  assert.deepEqual(
    cells.map((cell) => cell.weekday),
    ['一', '二', '三', '四', '五', '六', '日']
  )
  assert.deepEqual(
    cells.map((cell) => cell.day),
    [2, 3, 4, 5, 6, 7, 8]
  )
  assert.deepEqual(cells[0].moods, ['疲惫'])
  assert.deepEqual(cells[1].moods, ['焦虑', '平静'])
  assert.deepEqual(cells[2].moods, [])
})

test('近 30 天按周分组：首尾可能是不完整周，当前周有标记', () => {
  const days = []
  for (let day = 15; day <= 31; day++) {
    days.push({ date: `2026-10-${day}`, moods: day % 2 === 0 ? ['平静'] : [] })
  }
  for (let day = 1; day <= 13; day++) {
    days.push({ date: `2026-11-${String(day).padStart(2, '0')}`, moods: day % 3 === 0 ? ['开心'] : [] })
  }

  const weeks = groupDaysIntoWeeks(days, '2026-11-13')

  assert.deepEqual(
    weeks.map((week) => week.startKey),
    ['2026-10-12', '2026-10-19', '2026-10-26', '2026-11-02', '2026-11-09']
  )
  assert.equal(weeks[0].label, '10/12 – 10/18')
  assert.equal(weeks.at(-1).isCurrent, true)
  assert.equal(weeks.at(-1).label, '11/9 – 11/15（本周）')
  assert.equal(
    weeks.reduce((sum, week) => sum + week.total, 0),
    days.reduce((sum, day) => sum + day.moods.length, 0)
  )
  assert.deepEqual(weeks.reduce((all, week) => all.concat(week.days.map((day) => day.date)), []), days.map((day) => day.date))
})

test('只有下一周仍是完整周时可以点下一周', () => {
  assert.equal(canGoNextWeek('2026-10-26', '2026-11-13'), true)
  assert.equal(canGoNextWeek('2026-11-02', '2026-11-13'), false)
  assert.equal(canGoNextWeek('2026-11-09', '2026-11-13'), false)
  assert.equal(canGoNextWeek('2026-11-16', '2026-11-13'), false)
})

const SAMPLE_SOURCES = [
  { ref: 'r1', recordId: '101', date: '2026-11-02', weekday: '周一', moodType: '疲惫', content: '加班' },
  { ref: 'r2', recordId: '102', date: '2026-11-03', weekday: '周二', moodType: '平静', content: '散步' }
]

test('parseLetterSegments 处理普通文本、句首标记、相邻标记、标点绑定、未知引用与残缺括号', () => {
  assert.deepEqual(parseLetterSegments('普通的一周。', SAMPLE_SOURCES), [
    { type: 'text', text: '普通的一周。' }
  ])

  assert.deepEqual(parseLetterSegments('加班到十点{{r1}}{{r2}}，后来松下来了', SAMPLE_SOURCES), [
    { type: 'text', text: '加班到十' },
    {
      type: 'refGroup',
      prefix: '点',
      refs: [
        { ref: 'r1', recordId: '101', label: '11/2' },
        { ref: 'r2', recordId: '102', label: '11/3' }
      ],
      punctuation: '，'
    },
    { type: 'text', text: '后来松下来了' }
  ])

  assert.deepEqual(parseLetterSegments('{{r1}}，那天刚起步', SAMPLE_SOURCES), [
    {
      type: 'refGroup',
      prefix: '',
      refs: [{ ref: 'r1', recordId: '101', label: '11/2' }],
      punctuation: '，'
    },
    { type: 'text', text: '那天刚起步' }
  ])

  const warnings = []
  assert.deepEqual(
    parseLetterSegments('这天{{r99}}，还有{{r1}}和{{r99}}以及 {{ 没写完', SAMPLE_SOURCES, {
      onWarn: (msg) => warnings.push(msg)
    }),
    [
      { type: 'text', text: '这天，还' },
      {
        type: 'refGroup',
        prefix: '有',
        refs: [{ ref: 'r1', recordId: '101', label: '11/2' }],
        punctuation: ''
      },
      { type: 'text', text: '和以及 {{ 没写完' }
    ]
  )
  assert.equal(warnings.length, 2)
})

test('resolveReviewViewState 覆盖状态表每一行', () => {
  const baseStats = { total: 3, aiEnabledCount: 3, report: null, active: null, failed: null }

  assert.equal(resolveReviewViewState(baseStats, { mode: 'week', isCurrentWeek: true }), 'week_not_finished')
  assert.equal(resolveReviewViewState({ ...baseStats, total: 0, aiEnabledCount: 0 }, { mode: 'week' }), 'empty')
  assert.equal(resolveReviewViewState({ ...baseStats, aiEnabledCount: 0 }, { mode: 'week' }), 'no_ai_enabled')
  assert.equal(resolveReviewViewState(baseStats, { mode: 'week' }), 'not_generated')

  assert.equal(
    resolveReviewViewState({ ...baseStats, active: { id: '1', status: 'queued', jobId: '9' } }, { mode: 'week' }),
    'generating'
  )
  assert.equal(
    resolveReviewViewState({ ...baseStats, active: { id: '1', status: 'running', jobId: '9' } }, { mode: 'week' }),
    'generating'
  )
  assert.equal(
    resolveReviewViewState(
      { ...baseStats, active: { id: '1', status: 'running', jobId: '9' } },
      { mode: 'week', pollingTimedOut: true }
    ),
    'polling_timeout'
  )

  assert.equal(
    resolveReviewViewState(
      { ...baseStats, report: { id: '1', status: 'succeeded', resultKind: 'MODEL', body: 'ok', stale: false } },
      { mode: 'week' }
    ),
    'ready'
  )
  assert.equal(
    resolveReviewViewState(
      { ...baseStats, report: { id: '1', status: 'succeeded', resultKind: 'SUPPORT', body: '支持文案', stale: false } },
      { mode: 'week' }
    ),
    'crisis'
  )
  assert.equal(
    resolveReviewViewState(
      { ...baseStats, report: { id: '1', status: 'succeeded', resultKind: 'MODEL', body: '旧信', stale: true } },
      { mode: 'week' }
    ),
    'stale_edited'
  )
  assert.equal(
    resolveReviewViewState(
      { ...baseStats, report: { id: '1', status: 'succeeded', resultKind: 'MODEL', body: null, stale: true } },
      { mode: 'week' }
    ),
    'stale_deleted'
  )
  assert.equal(
    resolveReviewViewState(
      {
        ...baseStats,
        report: { id: '1', status: 'succeeded', resultKind: 'MODEL', body: '旧信', stale: false },
        failed: { id: '2', status: 'failed', jobId: '10' }
      },
      { mode: 'week' }
    ),
    'failed_with_report'
  )
  assert.equal(
    resolveReviewViewState({ ...baseStats, failed: { id: '2', status: 'failed', jobId: '10' } }, { mode: 'week' }),
    'failed_empty'
  )
})

