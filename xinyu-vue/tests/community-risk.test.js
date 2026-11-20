import assert from 'node:assert/strict'
import { test } from 'node:test'
import { communitySupportText } from '../src/utils/communityRisk.js'

const support = { ok: true, data: { supportText: '合成服务端支持文字' } }

test('命中的评论呈现响应中的文案', () => {
  assert.equal(communitySupportText(support), support.data.supportText)
})

test('命中且关闭 AI 的帖子呈现响应中的文案', () => {
  assert.equal(communitySupportText(support, false), support.data.supportText)
})

test('命中且开启 AI 的帖子交给公开的 SUPPORT 回复呈现', () => {
  assert.equal(communitySupportText(support, true), null)
})

test('未命中、失败或旧响应不呈现弹窗', () => {
  assert.equal(communitySupportText({ ok: true, data: { supportText: null } }), null)
  assert.equal(communitySupportText({ ok: false, data: support.data }), null)
  assert.equal(communitySupportText({ ok: true, data: null }), null)
})
