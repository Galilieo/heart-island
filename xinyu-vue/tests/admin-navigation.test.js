import assert from 'node:assert/strict'
import { test } from 'node:test'
import {
  adminNavigationGroups,
  adminNavigationItems,
  findAdminNavigationItem,
  isAdminNavigationActive
} from '../src/components/admin/adminNavigation.js'

const itemOf = (path) => adminNavigationItems.find((item) => item.to === path)

test('导航分组与页面名称和任务书一致', () => {
  assert.deepEqual(
    adminNavigationGroups.map((group) => group.label),
    ['总览', '账号与社区', 'AI 服务']
  )
  assert.deepEqual(
    adminNavigationItems.map((item) => item.label),
    ['概览', '用户管理', '帖子管理', '回复管理', '话题管理', 'AI 记录']
  )
  assert.deepEqual(
    adminNavigationItems.map((item) => item.to),
    ['/admin', '/admin/users', '/admin/posts', '/admin/replies', '/admin/topics', '/admin/ai-replies']
  )
})

test('概览只在 /admin 本身点亮', () => {
  const overview = itemOf('/admin')
  assert.equal(isAdminNavigationActive(overview, '/admin'), true)
  assert.equal(isAdminNavigationActive(overview, '/admin/'), true)
  assert.equal(isAdminNavigationActive(overview, '/admin/users'), false)
})

test('子页面按完整路径匹配，前缀相似的地址不误判', () => {
  const posts = itemOf('/admin/posts')
  assert.equal(isAdminNavigationActive(posts, '/admin/posts'), true)
  assert.equal(isAdminNavigationActive(posts, '/admin/posts/12'), true)
  assert.equal(isAdminNavigationActive(posts, '/admin/postsxx'), false)
  assert.equal(isAdminNavigationActive(posts, '/admin'), false)

  const users = itemOf('/admin/users')
  assert.equal(isAdminNavigationActive(users, '/admin/user'), false)
  assert.equal(isAdminNavigationActive(users, '/admin/users/7'), true)
})

test('回复管理与 AI 记录互不误判', () => {
  const replies = itemOf('/admin/replies')
  const aiReplies = itemOf('/admin/ai-replies')
  assert.equal(findAdminNavigationItem('/admin/replies').label, '回复管理')
  assert.equal(findAdminNavigationItem('/admin/ai-replies').label, 'AI 记录')
  assert.equal(isAdminNavigationActive(replies, '/admin/ai-replies'), false)
  assert.equal(isAdminNavigationActive(aiReplies, '/admin/replies'), false)
})

test('未知路径没有当前项', () => {
  assert.equal(findAdminNavigationItem('/home'), undefined)
  assert.equal(findAdminNavigationItem('/admin/unknown'), undefined)
})
