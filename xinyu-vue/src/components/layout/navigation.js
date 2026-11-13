// 顶部和底部共用业务入口及当前页规则。
export const navigationItems = [
  { label: '心情', to: '/home', match: ['/home'], icon: 'heart' },
  { label: '回顾', to: '/review', match: ['/review'], icon: 'cup' },
  { label: '社区', to: '/posts', match: ['/posts', '/community'], icon: 'cloud' },
  // 收藏在手机底部导航收起，入口移到「我的」页；电脑顶栏仍保留。
  { label: '收藏', to: '/favorites', match: ['/favorites'], icon: 'star', mobile: false },
  { label: '我的', to: '/profile', match: ['/profile'], icon: 'leaf' },
]

export function isNavigationActive(item, path) {
  return item.match.some((prefix) => path === prefix || path.startsWith(`${prefix}/`))
}
