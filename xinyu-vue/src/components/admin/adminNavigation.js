// 后台导航的唯一配置源：侧栏分组、名称、路径、图标名和当前项判断。
// 页面名称与路由 meta.title 保持一致（第 5 步决定：概览 / 用户管理 / 帖子管理 /
// 回复管理 / 话题管理 / AI 记录），避免导航和面包屑各写一套文案。
export const adminNavigationGroups = [
  {
    label: '总览',
    items: [{ label: '概览', to: '/admin', icon: 'dashboard' }]
  },
  {
    label: '账号与社区',
    items: [
      { label: '用户管理', to: '/admin/users', icon: 'users' },
      { label: '帖子管理', to: '/admin/posts', icon: 'posts' },
      { label: '回复管理', to: '/admin/replies', icon: 'replies' },
      { label: '话题管理', to: '/admin/topics', icon: 'topics' }
    ]
  },
  {
    label: 'AI 服务',
    items: [{ label: 'AI 记录', to: '/admin/ai-replies', icon: 'ai' }]
  }
]

export const adminNavigationItems = adminNavigationGroups.flatMap(
  (group) => group.items
)

// 概览页只匹配 /admin 本身；其余页面要求路径完全相等或带子路径，
// 防止 /admin/postsxx 这类前缀相似的地址误点亮帖子管理。
export function isAdminNavigationActive(item, path) {
  if (item.to === '/admin') {
    return path === '/admin' || path === '/admin/'
  }

  return path === item.to || path.startsWith(`${item.to}/`)
}

export function findAdminNavigationItem(path) {
  return adminNavigationItems.find((item) =>
    isAdminNavigationActive(item, path)
  )
}
