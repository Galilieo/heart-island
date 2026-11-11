// 心情相关的共用选项与展示工具；分组仅帮助查找，不代表情绪评分。

export const MOOD_GROUPS = [
  {
    id: 'first',
    ariaLabel: '心情选项第一组',
    options: ['开心', '满足', '期待', '感动', '平静'].map(value => ({ label: value, value }))
  },
  {
    id: 'second',
    ariaLabel: '心情选项第二组',
    options: ['焦虑', '难过', '生气', '委屈', '孤独', '迷茫', '疲惫', '其他'].map(value => ({ label: value, value }))
  }
]

// 筛选和统计沿用扁平列表；与选择器从同一份分组生成，避免选项遗漏。
export const MOOD_OPTIONS = MOOD_GROUPS.flatMap(group => group.options)

// 心情 → 配色分组，对应 tokens.css 中的 --joy / --tension / --calm / --sad / --tired。
// 焦虑、生气归入“紧绷”（tension），不再借用状态提示色 warn。
const TONE_MAP = {
  开心: 'joy', 满足: 'joy', 期待: 'joy', 感动: 'calm', 平静: 'calm',
  焦虑: 'tension', 难过: 'sad', 生气: 'tension', 委屈: 'sad', 孤独: 'sad',
  迷茫: 'tired', 疲惫: 'tired', 其他: 'neutral'
}

export function moodTone(type) {
  return TONE_MAP[type] || 'neutral'
}

// 与素材包的十三种心情一一对应；历史未知值仍保留原文字，表情使用中性默认项。
const EXPRESSION_MAP = {
  开心: 'happy', 满足: 'content', 期待: 'hopeful', 感动: 'touched', 平静: 'calm',
  焦虑: 'anxious', 难过: 'sad', 生气: 'angry', 委屈: 'wronged', 孤独: 'lonely',
  迷茫: 'lost', 疲惫: 'tired', 其他: 'other'
}

export function moodExpression(type) {
  return EXPRESSION_MAP[type] || 'other'
}

// 后端返回的时间字符串 → 「YYYY-MM-DD HH:mm」展示格式
export function formatTime(time) {
  if (!time) return ''
  const d = new Date(time)
  if (Number.isNaN(d.getTime())) return time
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

// 小芽对每种心情的一句回应，用于书写卡在选中心情后的副标题；
// 文案与品牌素材包的设计稿一致（取自 design-lab/mock.js 的 MASCOT_LINES，真实页面不依赖设计对比页）。
export const MOOD_RESPONSE_LINES = {
  开心: '真好！是什么让你开心的？说给我听听。',
  满足: '这种刚刚好的感觉很珍贵，记下来吧。',
  期待: '在期待什么呢？我也想知道。',
  感动: '是被什么打动了吗？慢慢写。',
  平静: '平平静静的也很好。今天有什么想留下的？',
  焦虑: '心里有点乱的时候，写下来会清楚一些。',
  难过: '嗯，难过的时候我在这儿。想说说吗？',
  生气: '生气也没关系，先把发生了什么写下来。',
  委屈: '委屈的感觉不好受。是发生了什么？',
  孤独: '我陪着你。想写点什么都可以。',
  迷茫: '不知道往哪走的时候，先写下现在在哪儿。',
  疲惫: '辛苦了。写几句就好，不用写很多。',
  其他: '说不清是什么心情也没关系，写写看。'
}

// 尚未选中心情时的书写卡副标题，语气与 MOOD_RESPONSE_LINES 一致。
export const MOOD_DEFAULT_HINT = '选一个最接近的，写几句就好。'

// 危机流程回复：品牌素材包规定，高风险走危机流程时不显示卡通表情，
// 因此 resultKind 为 SUPPORT 的记录要隐藏小芽并改用心屿署名。
export function isSupportReply(record) {
  return record?.resultKind === 'SUPPORT'
}
