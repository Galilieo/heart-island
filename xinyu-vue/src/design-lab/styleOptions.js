// 设计对比页的风格候选：标题字体、日间配色、小芽批注样式。
// 候选字体在 demo 阶段从 jsDelivr 按需加载 fontsource 切片样式；定稿后改为本地依赖自托管。

import brandLockup from './assets/xinyu-lockup.svg'
import brandLockupDark from './assets/xinyu-lockup-dark.svg'
import xiaoweiLockup from './assets/xinyu-lockup-xiaowei-regular.svg'
import xiaoweiLockupDark from './assets/xinyu-lockup-xiaowei-regular-dark.svg'
import xiaoweiBoldLockup from './assets/xinyu-lockup-xiaowei-bold.svg'
import xiaoweiBoldLockupDark from './assets/xinyu-lockup-xiaowei-bold-dark.svg'

const FONTSOURCE_CDN = 'https://cdn.jsdelivr.net/npm'

export const FONT_OPTIONS = [
  {
    id: 'wenkai',
    name: '霞鹜文楷',
    family: '"LXGW WenKai Screen", var(--font-sans)',
    // 已由 main.js 本地引入，无需额外加载
    href: null,
    trait: '手写楷体，温和、像本子上的字；笔画细，小字号稍弱。'
  },
  {
    id: 'serif',
    name: '思源宋体',
    family: '"Noto Serif SC", serif',
    href: `${FONTSOURCE_CDN}/@fontsource/noto-serif-sc@5.3.1/400.css`,
    trait: '印刷宋体，安静、有文学感，像书和信；手账味弱一些。'
  },
  {
    id: 'xiaowei',
    name: '站酷小薇',
    family: '"ZCOOL XiaoWei", serif',
    href: `${FONTSOURCE_CDN}/@fontsource/zcool-xiaowei@5.3.0/400.css`,
    trait: '细长文艺宋，比思源宋体更有设计感和辨识度。'
  },
  {
    id: 'kuaile',
    name: '站酷快乐体',
    family: '"ZCOOL KuaiLe", var(--font-sans)',
    href: `${FONTSOURCE_CDN}/@fontsource/zcool-kuaile@5.3.0/400.css`,
    trait: '圆润可爱，和小芽最搭；用在难过、焦虑等情绪场景时可能显得轻佻。'
  },
  {
    id: 'sans',
    name: '不用标题字体',
    family: 'var(--font-sans)',
    href: null,
    trait: '全站系统黑体，最稳妥，品牌感只能靠小芽和配色。'
  }
]

// 横版字标候选：素材包原版（思源黑体 Medium 转曲线），以及用站酷小薇轮廓替换文字的合并版。
// 合并版保留素材包规范：小芽不动、字色 #2f5d58 / 深夜 #dcd9ce、“心”中间一点用腮红 #f29a86。
// 字标是 SVG 图形，不需要加载任何字体。
export const LOGO_OPTIONS = [
  {
    id: 'brand',
    name: '素材包原版',
    light: brandLockup,
    dark: brandLockupDark,
    trait: '思源黑体 Medium，稳重清楚，但字形比较普通。'
  },
  {
    id: 'xiaowei',
    name: '小薇常规',
    light: xiaoweiLockup,
    dark: xiaoweiLockupDark,
    trait: '站酷小薇原始笔画，细长文艺；小尺寸下笔画偏细。'
  },
  {
    id: 'xiaowei-bold',
    name: '小薇加粗',
    light: xiaoweiBoldLockup,
    dark: xiaoweiBoldLockupDark,
    trait: '在小薇轮廓外描一圈同色边，保留字形特征，小尺寸下更清楚。'
  }
]

// 日间配色覆盖项；深夜主题沿用现有深夜 tokens，定稿后再单独配深夜版。
export const PALETTE_OPTIONS = [
  {
    id: 'mint',
    name: '薄荷青',
    vars: {},
    trait: '现有配色，和小芽身体同色系，清爽；深夜版已经做好，迁移成本最低；但青色在工具类产品里很常见。'
  },
  {
    // 品牌色取自小芽身体（#79ad97 / #5a978d），强调色取自小芽脸颊（#f29a86）
    id: 'sprout',
    name: '小芽色',
    vars: {
      '--brand': '#79ad97',
      '--brand-deep': '#4b8679',
      '--brand-soft': '#e4f0e9',
      '--brand-soft-deep': '#d3e7da',
      '--sea': '#3f7a6e',
      '--sea-deep': '#2f6157',
      '--accent': '#f29a86',
      '--accent-soft': '#fde5de',
      '--accent-ink': '#a84f3c',
      '--accent-line': '#f6c4b8',
      '--bg-base': '#fcfaf4',
      '--bg-soft': '#f3efe3',
      '--line': '#e8e3d4',
      '--line-strong': '#d5cdb8',
      '--shadow-focus': '0 0 0 3px rgba(121, 173, 151, 0.24)'
    },
    // 深夜版：背景沿用现有深夜海面色，只把品牌色偏向小芽的灰绿、强调色换成柔和珊瑚
    nightVars: {
      '--brand': '#6fa08c',
      '--brand-deep': '#93bfab',
      '--brand-soft': '#1a3330',
      '--brand-soft-deep': '#213d38',
      '--sea': '#3a6658',
      '--sea-deep': '#8ab8a4',
      '--accent': '#d08f80',
      '--accent-soft': '#35251f',
      '--accent-ink': '#dd9d8c',
      '--accent-line': '#46322b',
      '--shadow-focus': '0 0 0 3px rgba(111, 160, 140, 0.28)'
    },
    trait: '直接从小芽身上取色：身体的灰绿青作品牌色，脸颊的珊瑚粉作强调色。和薄荷青接近但更偏植物、更暖，品牌色有出处。'
  },
  {
    id: 'ink',
    name: '墨水蓝',
    vars: {
      '--brand': '#5b7fa6',
      '--brand-deep': '#3f6188',
      '--brand-soft': '#e7edf5',
      '--brand-soft-deep': '#d7e2ee',
      '--sea': '#3c5a80',
      '--sea-deep': '#2d4766',
      '--bg-base': '#fbf9f4',
      '--bg-soft': '#f2eee5',
      '--line': '#e7e1d4',
      '--line-strong': '#d3cbb9',
      '--shadow-focus': '0 0 0 3px rgba(91, 127, 166, 0.22)'
    },
    trait: '钢笔墨水蓝配米白纸，最像真的笔记本；情绪温度偏冷静。'
  },
  {
    id: 'apricot',
    name: '暖杏',
    vars: {
      '--brand': '#c98a6b',
      '--brand-deep': '#9c5f43',
      '--brand-soft': '#f8e9df',
      '--brand-soft-deep': '#f0d6c6',
      '--sea': '#9c5f43',
      '--sea-deep': '#7f4a33',
      '--bg-base': '#fdf8f2',
      '--bg-soft': '#f6ebdf',
      '--line': '#eee0d0',
      '--line-strong': '#dcc7b0',
      '--shadow-focus': '0 0 0 3px rgba(201, 138, 107, 0.24)'
    },
    trait: '暖、像傍晚的光，陪伴感强；和“生气”等暖色心情容易混。'
  },
  {
    id: 'moss',
    name: '苔绿',
    vars: {
      '--brand': '#7a9a6e',
      '--brand-deep': '#566f4c',
      '--brand-soft': '#e9efe3',
      '--brand-soft-deep': '#dbe6d2',
      '--sea': '#4f6b46',
      '--sea-deep': '#3c5435',
      '--bg-base': '#fbfaf4',
      '--bg-soft': '#f0eee2',
      '--line': '#e5e2d2',
      '--line-strong': '#cfcab2',
      '--shadow-focus': '0 0 0 3px rgba(122, 154, 110, 0.24)'
    },
    trait: '呼应小芽头上的嫩芽，自然、像植物和岛屿；整体更朴素。'
  }
]

// 心情色候选。“柔和一套”用 OKLCH 生成：五种心情明度、彩度一致，只改色相，
// 色相避开品牌灰绿（约 160°）和小芽脸颊珊瑚（约 30°）：
// 愉快 85° 蜂蜜黄、紧绷 55° 杏橙、平和 215° 晴空蓝、低落 268° 长春花蓝紫、疲惫 318° 暮色藕紫。
// 文字色与柔底的对比度：日间 ≥ 5.8，深夜 ≥ 7.4（WCAG AA 要求 4.5）。
export const MOOD_PALETTE_OPTIONS = [
  {
    id: 'current',
    name: '现有心情色',
    vars: {},
    nightVars: {},
    trait: '愉快和紧绷都是黄棕色容易混；平和与品牌同为青绿；疲惫偏灰像“不可用”；五色明度不一致。'
  },
  {
    id: 'harmonic',
    name: '柔和一套',
    vars: {
      '--joy': '#e4cb95', '--joy-soft': '#f7eeda', '--joy-ink': '#6e5724',
      '--warn': '#e4b290', '--warn-soft': '#feeadd', '--warn-ink': '#7b4f30',
      '--calm': '#84cbdc', '--calm-soft': '#daf4fa', '--calm-ink': '#186575',
      '--sad': '#a9bdef', '--sad-soft': '#e6eeff', '--sad-ink': '#495985',
      '--tired': '#d2afdd', '--tired-soft': '#f7e9fb', '--tired-ink': '#6c4d76'
    },
    nightVars: {
      '--joy': '#c1ab7f', '--joy-soft': '#3e3420', '--joy-ink': '#ddc89e',
      '--warn': '#bf9478', '--warn-soft': '#443124', '--warn-ink': '#eac1a6',
      '--calm': '#6ea9b8', '--calm-soft': '#1f3a41', '--calm-ink': '#9ed5e3',
      '--sad': '#8d9ec8', '--sad-soft': '#2e3548', '--sad-ink': '#bacaf3',
      '--tired': '#b092b9', '--tired-soft': '#3d3041', '--tired-ink': '#dbbfe4'
    },
    trait: '五色同一明度和彩度，只换色相；平和改为晴空蓝不再与品牌撞色，疲惫改为暮色藕紫，深夜版单独调暗。'
  }
]

export const NOTE_OPTIONS = [
  { id: 'margin', name: '页边手写', trait: '没有底色，点线加标题字体，像用笔写在旁边，最轻。' },
  { id: 'letter', name: '回信', trait: '虚线分隔后另起一段并署名，像收到一封回信，庄重一些。' },
  { id: 'sticky', name: '便签', trait: '贴在页面上的小纸条，最有手账感，但比较占地方、偏活泼。' },
  { id: 'bubble', name: '气泡', trait: '小芽头像加对话气泡，和以后的多轮对话最一致。' },
  { id: 'callout', name: '提示框（原）', trait: '此前的样式，留作对比。' }
]
