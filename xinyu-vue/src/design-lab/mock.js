// 设计对比页的假数据与配色工具；只服务 /design-lab，不调用任何接口。
import { moodTone } from '../utils/mood'

export const MOCK_USER = { nickname: 'Galilieo' }

export const MOCK_GREETING = '午后好'

export const MOCK_DATE_LABEL = '11月11日 · 周三'

export const MOCK_ENTRIES = [
  {
    id: 4,
    moodType: '疲惫',
    day: '今天',
    date: '11',
    weekday: '周三',
    time: '14:27',
    content: '连着开了三个会，脑子已经转不动了。下午还有一堆要改的东西，先喝口水歇一下。',
    aiReply: '连轴转的一天，累是很正常的。先把水喝完，给自己五分钟什么都不想，剩下的事一件一件来就好。'
  },
  {
    id: 3,
    moodType: '平静',
    day: '昨天',
    date: '10',
    weekday: '周二',
    time: '22:05',
    content: '今天整体感觉不错，晚上散了会儿步，风很舒服。',
    aiReply: '平平稳稳的一天本身就挺好。晚风和散步，听起来是很适合收尾的方式。'
  },
  {
    id: 2,
    moodType: '焦虑',
    day: '周一',
    date: '09',
    weekday: '周一',
    time: '23:40',
    content: '面试结果还没出来，一直在想是不是哪里答得不好。',
    aiReply: null
  },
  {
    id: 1,
    moodType: '开心',
    day: '周日',
    date: '08',
    weekday: '周日',
    time: '18:12',
    content: '拖了两周的报告终于交了！',
    aiReply: '恭喜！拖了两周终于落地，这种松一口气的感觉值得好好享受一下。'
  }
]

// 近 7 天统计：只列出现过的心情，按次数从多到少。
export const MOCK_WEEK = [
  { moodType: '平静', count: 3 },
  { moodType: '疲惫', count: 2 },
  { moodType: '焦虑', count: 1 },
  { moodType: '开心', count: 1 }
]

export const MOCK_WEEK_TOTAL = MOCK_WEEK.reduce((sum, item) => sum + item.count, 0)

// 本月心情日历：2026 年 11 月，1 日是周日；按周一开头排列，前面补 6 个空格。
const MONTH_LEADING_BLANKS = 6
const MONTH_DAYS = 30
const MONTH_TODAY = 11
const MONTH_RECORDS = {
  2: '平静', 3: '开心', 5: '焦虑', 6: '疲惫', 7: '平静', 8: '开心', 9: '焦虑', 10: '平静', 11: '疲惫'
}

export const MOCK_MONTH_LABEL = '11 月'

export const MOCK_MONTH_CELLS = [
  ...Array.from({ length: MONTH_LEADING_BLANKS }, (_, index) => ({ key: `blank-${index}`, day: null })),
  ...Array.from({ length: MONTH_DAYS }, (_, index) => {
    const day = index + 1
    return {
      key: `day-${day}`,
      day,
      moodType: MONTH_RECORDS[day] || null,
      isToday: day === MONTH_TODAY,
      isFuture: day > MONTH_TODAY
    }
  })
]

// 社区假数据：帖子统一以“匿名岛民”展示，与后端当前行为一致。
export const MOCK_TOPICS = [
  { name: '学习压力', count: 12 },
  { name: '实习焦虑', count: 8 },
  { name: '人际关系', count: 6 },
  { name: '情绪低落', count: 5 },
  { name: '开心分享', count: 9 },
  { name: '睡眠问题', count: 3 }
]

export const MOCK_POSTS = [
  {
    id: 3,
    topic: '实习焦虑',
    moodType: '焦虑',
    timeLabel: '2 小时前',
    content: '投了二十多家，只有两家回了。看着同学陆续拿到 offer，心里越来越慌，不知道是不是自己方向选错了。',
    aiReply: '投了这么多却只有零星回音，慌是很自然的。回复率低常常和时间、岗位数量有关，不一定说明方向错了。可以先挑两三家最想去的，把简历再针对性改一版。',
    replyCount: 4,
    likeCount: 12,
    favorited: false
  },
  {
    id: 2,
    topic: '开心分享',
    moodType: '开心',
    timeLabel: '昨天 21:30',
    content: '今天第一次自己做了红烧肉，居然没翻车！室友说比食堂好吃哈哈哈。',
    aiReply: '第一次就成功，太厉害了！被室友夸的那一刻一定很有成就感。',
    replyCount: 7,
    likeCount: 26,
    favorited: true
  },
  {
    id: 1,
    topic: '睡眠问题',
    moodType: '疲惫',
    timeLabel: '11 月 9 日',
    content: '连续一周凌晨三点才睡着，白天上课完全听不进去。',
    aiReply: null,
    replyCount: 2,
    likeCount: 5,
    favorited: false
  }
]

// 长期记忆示意（规划中功能，仅展示可能的形态，不代表已实现）。
export const MOCK_MEMORY = [
  '最近常提到「面试」（3 次）和「加班」（2 次）',
  '上一次说起面试，是 11 月 9 日，那天你有点焦虑'
]

// 选中心情后，小芽给出的一句回应（只用于方案 C 的对话气泡）。
export const MASCOT_LINES = {
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

// 心情分组 → 页面配色变量。“warn”使用警示色，“neutral”使用品牌色。
const TONE_VARS = {
  joy: { color: 'var(--joy)', soft: 'var(--joy-soft)', ink: 'var(--joy-ink)' },
  calm: { color: 'var(--calm)', soft: 'var(--calm-soft)', ink: 'var(--calm-ink)' },
  warn: { color: 'var(--warn)', soft: 'var(--warn-soft)', ink: 'var(--warn-ink)' },
  sad: { color: 'var(--sad)', soft: 'var(--sad-soft)', ink: 'var(--sad-ink)' },
  tired: { color: 'var(--tired)', soft: 'var(--tired-soft)', ink: 'var(--tired-ink)' },
  neutral: { color: 'var(--brand)', soft: 'var(--brand-soft)', ink: 'var(--brand-deep)' }
}

// 返回可直接绑定到 style 的 CSS 自定义属性，组件内用 var(--tone) 等读取。
export function toneStyle(moodType) {
  const tone = TONE_VARS[moodTone(moodType)]
  return {
    '--tone': tone.color,
    '--tone-soft': tone.soft,
    '--tone-ink': tone.ink
  }
}
