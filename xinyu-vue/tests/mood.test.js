import test from 'node:test'
import assert from 'node:assert/strict'
import { MOOD_OPTIONS, moodTone } from '../src/utils/mood.js'
import * as mood from '../src/utils/mood.js'

const expected = ['开心', '满足', '期待', '感动', '平静', '焦虑', '难过', '生气', '委屈', '孤独', '迷茫', '疲惫', '其他']

test('新增、筛选和统计使用同一套十三项心情，旧值继续有效', () => {
  assert.deepEqual(MOOD_OPTIONS.map(item => item.value), expected)
  assert.equal(new Set(MOOD_OPTIONS.map(item => item.value)).size, 13)
  assert.ok(MOOD_OPTIONS.every(item => item.label === item.value))
})

test('选择器的两组覆盖全部选项且没有重复，其他并入末组', () => {
  assert.ok(Array.isArray(mood.MOOD_GROUPS))
  assert.deepEqual(mood.MOOD_GROUPS.map(group => group.options.length), [5, 8])
  assert.deepEqual(mood.MOOD_GROUPS.flatMap(group => group.options.map(item => item.value)), expected)
  for (const item of MOOD_OPTIONS) {
    assert.ok(['joy', 'tension', 'calm', 'sad', 'tired', 'neutral'].includes(moodTone(item.value)))
  }
})

test('焦虑与生气使用独立的紧绷色，不借用状态提示色 warn', () => {
  assert.equal(moodTone('焦虑'), 'tension')
  assert.equal(moodTone('生气'), 'tension')
})

test('历史未知类型保留文本展示时可安全使用中性色', () => {
  assert.equal(moodTone('历史未知值'), 'neutral')
})

test('十三种心情都有小芽的一句回应，未选心情时有默认提示', () => {
  for (const item of MOOD_OPTIONS) {
    const line = mood.MOOD_RESPONSE_LINES[item.value]
    assert.equal(typeof line, 'string', `${item.value} 缺少回应文案`)
    assert.ok(line.trim().length > 0, `${item.value} 的回应文案不能为空`)
  }
  assert.deepEqual(
    Object.keys(mood.MOOD_RESPONSE_LINES).sort(),
    expected.slice().sort(),
    '回应文案不应多出或遗漏心情'
  )
  assert.equal(mood.MOOD_DEFAULT_HINT, '选一个最接近的，写几句就好。')
})

test('仅危机流程回复 resultKind 为 SUPPORT 时隐藏小芽', () => {
  assert.equal(mood.isSupportReply({ resultKind: 'SUPPORT' }), true)
  assert.equal(mood.isSupportReply({ resultKind: 'MODEL' }), false)
  assert.equal(mood.isSupportReply({ resultKind: null }), false)
  assert.equal(mood.isSupportReply({}), false)
  assert.equal(mood.isSupportReply(null), false)
  assert.equal(mood.isSupportReply(undefined), false)
})

test('月历聚合每天最晚一条记录，非法时间跳过且空数组返回空对象', () => {
  const records = [
    { createTime: '2026-11-11 20:00:00', moodType: '平静' },
    { createTime: '2026-11-11 08:00:00', moodType: '开心' },
    { createTime: '2026-11-10 23:30:00', moodType: '疲惫' },
    { createTime: '不是时间', moodType: '其他' },
    { createTime: null, moodType: '其他' }
  ]

  assert.deepEqual(mood.groupLatestMoodByDay(records), {
    '2026-11-10': '疲惫',
    '2026-11-11': '平静'
  })
  assert.deepEqual(mood.groupLatestMoodByDay([]), {})
})
