<script setup>
// 设计对比页（仅开发环境）：同一份假数据套用三种整体方向，便于切换比较。
// 手机宽度用固定 375px 的容器模拟，各方案用容器查询而不是视口媒体查询响应宽度。
import { computed, onBeforeUnmount, provide, ref, watch } from 'vue'
import { FONT_OPTIONS, PALETTE_OPTIONS, NOTE_OPTIONS, LOGO_OPTIONS, MOOD_PALETTE_OPTIONS } from './styleOptions'
import PaletteCompare from './PaletteCompare.vue'
import LogoCompare from './LogoCompare.vue'
import VariantJournal from './VariantJournal.vue'
import VariantCalm from './VariantCalm.vue'
import VariantCompanion from './VariantCompanion.vue'
import VariantCommunity from './VariantCommunity.vue'
import StyleGuide from './StyleGuide.vue'

// group 决定显示在切换栏的哪一组；props 传给方案组件（例如 A+ 打开侧栏）
const VARIANTS = [
  {
    id: 'journal-wide',
    group: 'home',
    name: 'A+ · 手账宽屏',
    component: VariantJournal,
    props: { withRail: true },
    idea: '在 A 的基础上，宽屏增加 300px 侧栏：本周统计、本月心情日历，以及给长期记忆和多轮对话预留的入口（虚线卡片表示规划中）。选心情后副标题变成小芽的一句回应，这一点借自 C。',
    pros: '解决桌面两侧留白；侧栏是以后放记忆、对话入口的固定位置，扩展时不用推翻版式。',
    cons: '侧栏需要真实数据支撑（月历要新增接口或由前端聚合）；980px 以下侧栏卡片插入主栏，手机页面会变长。'
  },
  {
    id: 'journal',
    group: 'home',
    name: 'A · 手账',
    component: VariantJournal,
    idea: '像一本安静的日记本：单栏居中、纸面质感、标题用霞鹜文楷。记录按日期排成时间线，AI 回复像小芽在页边写的批注。',
    pros: '最贴近“写日记”的感觉，阅读舒服；手机和桌面结构一致，维护简单。',
    cons: '桌面两侧留白多，信息密度低；统计只保留一条摘要。'
  },
  {
    id: 'calm',
    group: 'home',
    name: 'B · 简净',
    component: VariantCalm,
    idea: '克制的产品风：去掉涂鸦和渐变，桌面左侧固定书写栏、右侧统计和记录。心情用色点标签，小芽只在选中时出现。',
    pros: '信息清晰、桌面利用率高，像成熟的工具类产品，后续加功能好扩展。',
    cons: '情绪温度最低，小芽的存在感弱，表情素材用得最少。'
  },
  {
    id: 'companion',
    group: 'home',
    name: 'C · 小芽陪伴',
    component: VariantCompanion,
    idea: '以小芽为主角：选心情时小芽换表情并说一句话，输入框像聊天框；历史记录排成“你说 / 小芽回”的对话。',
    pros: '13 个表情和动态素材用得最充分，AI 陪伴的定位最鲜明，有记忆点。',
    cons: '风格偏可爱，长文本阅读不如手账；对话式布局要处理好没有 AI 回复的记录。'
  },
  {
    id: 'community',
    group: 'extend',
    name: '社区 · 手账',
    component: VariantCommunity,
    idea: '同一套顶栏、主栏加侧栏、心情标签和小芽批注用到社区；话题改成横向标签，发布按钮放在标题旁；“点赞”试改为“抱抱”。',
    pros: '和首页一眼就是同一个产品；社区约定常驻侧栏。',
    cons: '“抱抱”等叫法改动要同步后台和其他页面；侧栏的话题计数需要接口支持。'
  },
  {
    id: 'palettes',
    group: 'extend',
    name: '配色对比',
    component: PaletteCompare,
    idea: '同一份内容并排套用四套配色：现有薄荷青、取自小芽身上的小芽色、苔绿、墨水蓝。字体与批注沿用上方的选择。',
    pros: '一眼比较整体气质，不用来回切换下拉框。',
    cons: '只做了日间版；选定后再配对应的深夜版。'
  },
  {
    id: 'logos',
    group: 'extend',
    name: 'Logo 对比',
    component: LogoCompare,
    idea: '素材包原版字标与站酷小薇合并版并排，分别看大尺寸、顶栏尺寸和小尺寸，日间与深夜底色同时展示。',
    pros: '直接看到小尺寸下笔画是否够清楚，再决定用常规还是加粗。',
    cons: '加粗版是给轮廓描边，属于程序化处理；正式注册商标前仍建议请设计师微调笔画。'
  },
  {
    id: 'guide',
    group: 'extend',
    name: '风格规范',
    component: StyleGuide,
    idea: '把手账风格写成全站规范：品牌元素、心情、配色、字体、组件和使用规则。',
    pros: '之后改任何页面都按这一份来，保证统一和品牌辨识度。',
    cons: '规范里的标签、批注、卡片还要抽成真正的公共组件，才能在全站复用。'
  }
]

const VARIANT_GROUPS = [
  { id: 'home', label: '首页方案' },
  { id: 'extend', label: '手账延伸' }
]

function variantsInGroup(groupId) {
  return VARIANTS.filter(item => item.group === groupId)
}

const variantId = ref('journal-wide')
const deviceWidth = ref('desktop')
const theme = ref(document.documentElement.getAttribute('data-theme') || 'light')

const currentVariant = computed(() => VARIANTS.find(item => item.id === variantId.value))

// 预览主题直接写到 <html> 上，离开页面时恢复进入前的主题，不写入用户偏好。
const themeBeforeEnter = document.documentElement.getAttribute('data-theme')

watch(theme, (value) => {
  document.documentElement.setAttribute('data-theme', value)
}, { immediate: true })

// 风格候选：标题字体、配色、批注样式，作用于预览框内的所有方案。
// 默认值即已定稿的选择：霞鹜文楷 + 小芽色 + 页边手写。
const fontId = ref('wenkai')
const paletteId = ref('sprout')
const noteStyle = ref('margin')
const moodPaletteId = ref('harmonic')
// 已定稿：站酷小薇合并版字标（加粗）
const logoId = ref('xiaowei-bold')

provide('noteStyle', noteStyle)

// 顶栏等处按当前主题取对应的字标图片
const currentLogo = computed(() => LOGO_OPTIONS.find(item => item.id === logoId.value))
const logoSrc = computed(() => theme.value === 'night' ? currentLogo.value.dark : currentLogo.value.light)

provide('logoSrc', logoSrc)

const currentFont = computed(() => FONT_OPTIONS.find(item => item.id === fontId.value))
const currentPalette = computed(() => PALETTE_OPTIONS.find(item => item.id === paletteId.value))
const currentNote = computed(() => NOTE_OPTIONS.find(item => item.id === noteStyle.value))
const currentMoodPalette = computed(() => MOOD_PALETTE_OPTIONS.find(item => item.id === moodPaletteId.value))

// 日间使用 vars；深夜使用 nightVars，没有深夜版的候选配色则保持现有深夜 tokens
const frameStyle = computed(() => {
  const isNight = theme.value === 'night'
  const paletteVars = isNight ? (currentPalette.value.nightVars || {}) : currentPalette.value.vars
  const moodVars = isNight ? currentMoodPalette.value.nightVars : currentMoodPalette.value.vars

  return {
    '--font-display': currentFont.value.family,
    ...paletteVars,
    ...moodVars
  }
})

// 候选字体首次用到时才插入样式表，每个地址只加载一次；离开页面时移除
const fontLinks = new Map()

function loadFontStylesheet(href) {
  if (!href || fontLinks.has(href)) {
    return
  }

  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = href
  document.head.appendChild(link)
  fontLinks.set(href, link)
}

watch(currentFont, font => loadFontStylesheet(font.href), { immediate: true })

onBeforeUnmount(() => {
  if (themeBeforeEnter) {
    document.documentElement.setAttribute('data-theme', themeBeforeEnter)
  } else {
    document.documentElement.removeAttribute('data-theme')
  }

  fontLinks.forEach(link => link.remove())
})
</script>

<template>
  <div class="lab">
    <header class="lab__bar">
      <strong class="lab__name">设计对比</strong>

      <div
        v-for="group in VARIANT_GROUPS"
        :key="group.id"
        class="lab__group"
        role="radiogroup"
        :aria-label="group.label"
      >
        <button
          v-for="item in variantsInGroup(group.id)"
          :key="item.id"
          type="button"
          role="radio"
          :aria-checked="variantId === item.id"
          :class="{ 'is-on': variantId === item.id }"
          @click="variantId = item.id"
        >{{ item.name }}</button>
      </div>

      <div class="lab__group" role="radiogroup" aria-label="预览宽度">
        <button
          type="button"
          role="radio"
          :aria-checked="deviceWidth === 'desktop'"
          :class="{ 'is-on': deviceWidth === 'desktop' }"
          @click="deviceWidth = 'desktop'"
        >桌面</button>
        <button
          type="button"
          role="radio"
          :aria-checked="deviceWidth === 'mobile'"
          :class="{ 'is-on': deviceWidth === 'mobile' }"
          @click="deviceWidth = 'mobile'"
        >手机</button>
      </div>

      <div class="lab__group" role="radiogroup" aria-label="主题">
        <button
          type="button"
          role="radio"
          :aria-checked="theme === 'light'"
          :class="{ 'is-on': theme === 'light' }"
          @click="theme = 'light'"
        >日间</button>
        <button
          type="button"
          role="radio"
          :aria-checked="theme === 'night'"
          :class="{ 'is-on': theme === 'night' }"
          @click="theme = 'night'"
        >深夜</button>
      </div>

      <label class="lab__select">
        字体
        <select v-model="fontId">
          <option v-for="item in FONT_OPTIONS" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="lab__select">
        配色
        <select v-model="paletteId">
          <option v-for="item in PALETTE_OPTIONS" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="lab__select">
        Logo
        <select v-model="logoId">
          <option v-for="item in LOGO_OPTIONS" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="lab__select">
        心情色
        <select v-model="moodPaletteId">
          <option v-for="item in MOOD_PALETTE_OPTIONS" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>

      <label class="lab__select">
        批注
        <select v-model="noteStyle">
          <option v-for="item in NOTE_OPTIONS" :key="item.id" :value="item.id">{{ item.name }}</option>
        </select>
      </label>
    </header>

    <section class="lab__note">
      <p><b>思路</b>{{ currentVariant.idea }}</p>
      <p><b>优点</b>{{ currentVariant.pros }}</p>
      <p><b>代价</b>{{ currentVariant.cons }}</p>
      <p class="lab__note-style">
        <b>风格</b>
        字体「{{ currentFont.name }}」{{ currentFont.trait }}
        配色「{{ currentPalette.name }}」{{ currentPalette.trait }}{{ theme === 'night' && !currentPalette.nightVars ? '（这套配色还没有深夜版，暂用现有深夜配色）' : '' }}
        心情色「{{ currentMoodPalette.name }}」{{ currentMoodPalette.trait }}
        批注「{{ currentNote.name }}」{{ currentNote.trait }}
      </p>
    </section>

    <div class="lab__stage" :class="`lab__stage--${deviceWidth}`">
      <div class="lab__frame" :style="frameStyle">
        <component :is="currentVariant.component" :key="currentVariant.id" v-bind="currentVariant.props || {}" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.lab {
  min-height: 100vh;
  background: var(--bg-soft);
}

.lab__bar {
  position: sticky;
  top: 0;
  z-index: 10;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 16px;
  padding: 10px 20px;
  background: var(--abyss-deep);
  color: var(--ink-on-sea);
  font-size: var(--fs-sm);
}

.lab__name { margin-right: 8px; }

.lab__group {
  display: inline-flex;
  padding: 3px;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.08);
}

.lab__group button {
  padding: 5px 12px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: inherit;
  font-size: var(--fs-sm);
  cursor: pointer;
}

.lab__group button.is-on {
  background: var(--ink-on-sea);
  color: var(--abyss-deep);
  font-weight: 700;
}

.lab__select {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.lab__select select {
  padding: 4px 8px;
  border: 0;
  border-radius: var(--radius-pill);
  background: rgba(255, 255, 255, 0.12);
  color: inherit;
  font: inherit;
}

.lab__select option { color: #2f2a24; }

.lab__note-style {
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px dashed var(--line-strong);
}

.lab__note {
  display: grid;
  gap: 4px;
  max-width: 1200px;
  margin: 0 auto;
  padding: 14px 20px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.lab__note b {
  margin-right: 8px;
  color: var(--ink-1);
}

.lab__stage {
  padding: 0 20px 48px;
}

/* 预览框建立容器上下文，方案内部用 @container 判断宽窄 */
.lab__frame {
  container-type: inline-size;
  margin: 0 auto;
  overflow: hidden;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-3);
  background: var(--bg-base);
  box-shadow: var(--shadow-md);
}

.lab__stage--desktop .lab__frame { max-width: 1200px; }

.lab__stage--mobile .lab__frame {
  width: 375px;
  max-width: 100%;
  border-radius: 28px;
}
</style>
