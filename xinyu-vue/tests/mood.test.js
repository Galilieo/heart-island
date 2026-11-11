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
