<script setup>
// 手账风格规范：品牌元素、配色、字体、组件和使用规则；色值全部读取现有 tokens，切换深夜主题同步变化。
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import { MOOD_GROUPS, moodExpression } from '../utils/mood'
import { toneStyle } from './mock'
import XiaoyaNote from './XiaoyaNote.vue'
import { inject, ref } from 'vue'
import brandLockup from './assets/xinyu-lockup.svg'

const logoSrc = inject('logoSrc', ref(brandLockup))

const COLOR_GROUPS = [
  {
    name: '纸面与墨色',
    colors: [
      { token: '--bg-base', label: '纸面' },
      { token: '--bg-soft', label: '柔底' },
      { token: '--bg-card', label: '卡片' },
      { token: '--line', label: '线条' },
      { token: '--ink-1', label: '正文' },
      { token: '--ink-2', label: '次要' },
      { token: '--ink-3', label: '辅助' }
    ]
  },
  {
    name: '品牌（只表示可点击与小芽）',
    colors: [
      { token: '--brand', label: '品牌色' },
      { token: '--brand-deep', label: '深青' },
      { token: '--brand-soft', label: '青柔底' },
      { token: '--sea', label: '海色' }
    ]
  },
  {
    name: '心情（只表示心情）',
    colors: [
      { token: '--joy', label: '愉快' },
      { token: '--calm', label: '平和' },
      { token: '--tension', label: '紧绷' },
      { token: '--sad', label: '低落' },
      { token: '--tired', label: '疲惫' }
    ]
  }
]

const TYPE_SCALE = [
  { sample: '午后好，Galilieo', usage: '页面标题 · 问候', font: 'display', size: 'var(--fs-3xl)' },
  { sample: '今天是什么心情？', usage: '区块标题', font: 'display', size: 'var(--fs-xl)' },
  { sample: '11 · 周三 · 7 次', usage: '日期与数字', font: 'display', size: 'var(--fs-2xl)' },
  { sample: '连着开了三个会，脑子已经转不动了。', usage: '正文（系统黑体 16px / 1.8）', font: 'sans', size: 'var(--fs-base)' },
  { sample: '需要 AI 回复 · 0/500 · 2 小时前', usage: '辅助信息 · 表单', font: 'sans', size: 'var(--fs-sm)' }
]

const RULES = [
  {
    title: '字标是图形，字体只有两种',
    detail: '字标「心屿」是站酷小薇转曲线的 SVG，不加载字体；页面标题、问候、日期数字、小芽说的话和批注用霞鹜文楷；按钮、表单、正文和后台用系统黑体。站酷小薇不用于任何其他文字。'
  },
  {
    title: '颜色各有分工',
    detail: '薄荷青只表示“可以点”和“小芽 / AI”；心情色只表示心情；红色只留给删除与错误。AI 回复不再用杏红底色。'
  },
  {
    title: '小芽出现必须有角色',
    detail: '回应你的选择、为 AI 回复署名、代表一周的心情、陪伴空状态。不再放与内容无关的漂浮涂鸦。'
  },
  {
    title: '版式：主栏 + 侧栏',
    detail: '主栏不超过 680px，适合阅读；宽屏加 300px 侧栏放统计、话题和入口；980px 以下侧栏卡片插入主栏。'
  },
  {
    title: '层级靠字号，不靠小标题',
    detail: '去掉标题上方字间距很宽的小字；需要眉题时只用日期。分隔用虚线，卡片用 1px 线加轻阴影。'
  },
  {
    title: '文案口吻',
    detail: '称呼用“你”，短句、口语；小芽温和、不说教，不替用户下结论。AI 回复署名“小芽”，开关等处仍注明是 AI 生成。'
  }
]

function swatchStyle(token) {
  return { background: `var(${token})` }
}
</script>

<template>
  <div class="g">
    <header class="g-head">
      <h1>心屿 · 手账风格规范</h1>
      <p>所有页面共用这一套：纸面、文楷、小芽和心情色。下面的色块和组件都直接读取项目里的 tokens。</p>
    </header>

    <section class="g-section">
      <h2>品牌元素</h2>
      <div class="g-brand">
        <figure class="g-brand__item">
          <Xiaoya expression="happy" animated :size="72" />
          <figcaption><b>小芽</b>会回应、会署名、会陪伴的角色，13 种表情各自对应一种心情。</figcaption>
        </figure>
        <figure class="g-brand__item">
          <img :src="logoSrc" alt="心屿" class="g-lockup">
          <figcaption><b>字标 + 霞鹜文楷</b>字标是站酷小薇转曲线的 SVG 图形，“心”上一点用腮红；标题用霞鹜文楷，像一本写过的本子。</figcaption>
        </figure>
        <figure class="g-brand__item">
          <span class="g-paper">今天发生了什么？</span>
          <figcaption><b>横线信纸</b>书写区的横线和虚线分隔，是手账感的来源。</figcaption>
        </figure>
        <figure class="g-brand__item">
          <XiaoyaNote text="累是很正常的，先歇一会儿。" />
          <figcaption><b>小芽批注</b>AI 回复像写在页边的批注，而不是系统通知。</figcaption>
        </figure>
      </div>
    </section>

    <section class="g-section">
      <h2>心情</h2>
      <div v-for="group in MOOD_GROUPS" :key="group.id" class="g-moods">
        <span
          v-for="item in group.options"
          :key="item.value"
          class="g-tag"
          :style="toneStyle(item.value)"
        >
          <Xiaoya :expression="moodExpression(item.value)" :size="22" />{{ item.label }}
        </span>
      </div>
    </section>

    <section class="g-section">
      <h2>配色</h2>
      <div v-for="group in COLOR_GROUPS" :key="group.name" class="g-colors">
        <h3>{{ group.name }}</h3>
        <div class="g-swatches">
          <div v-for="color in group.colors" :key="color.token" class="g-swatch">
            <span class="g-swatch__chip" :style="swatchStyle(color.token)" />
            <span>{{ color.label }}</span>
            <code>{{ color.token }}</code>
          </div>
        </div>
      </div>
    </section>

    <section class="g-section">
      <h2>字体</h2>
      <div class="g-type">
        <div v-for="item in TYPE_SCALE" :key="item.usage" class="g-type__row">
          <span
            class="g-type__sample"
            :class="`g-type__sample--${item.font}`"
            :style="{ fontSize: item.size }"
          >{{ item.sample }}</span>
          <span class="g-type__usage">{{ item.usage }}</span>
        </div>
      </div>
    </section>

    <section class="g-section">
      <h2>组件</h2>
      <div class="g-components">
        <div class="g-row">
          <BaseButton>保存这一页</BaseButton>
          <BaseButton variant="soft">查看详情</BaseButton>
          <BaseButton variant="ghost">取消</BaseButton>
          <BaseButton variant="link">还没有账号？去注册 →</BaseButton>
          <BaseButton variant="danger">删除</BaseButton>
        </div>
        <div class="g-row">
          <span class="g-range is-on">全部</span>
          <span class="g-range">近 7 天</span>
          <span class="g-range">近 30 天</span>
          <span class="g-plan">规划中</span>
        </div>
        <div class="g-card">
          <p class="g-card__title">卡片</p>
          <p class="g-card__text">1px 线条 + 轻阴影 + 18px 圆角；卡片内的分隔使用虚线。</p>
        </div>
        <div class="g-card g-card--plan">
          <p class="g-card__title">规划中的卡片</p>
          <p class="g-card__text">虚线边框、无阴影，和已实现的功能区分开，不把计划描述成已上线。</p>
        </div>
      </div>
    </section>

    <section class="g-section">
      <h2>使用规则</h2>
      <ol class="g-rules">
        <li v-for="rule in RULES" :key="rule.title">
          <b>{{ rule.title }}</b>
          <p>{{ rule.detail }}</p>
        </li>
      </ol>
    </section>
  </div>
</template>

<style scoped>
.g {
  max-width: 920px;
  margin: 0 auto;
  padding: 32px 24px 56px;
  color: var(--ink-1);
}

.g-head h1 {
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
}

.g-head p {
  margin-top: 6px;
  color: var(--ink-2);
}

.g-section {
  margin-top: 32px;
  padding-top: 20px;
  border-top: 1px dashed var(--line-strong);
}

.g-section h2 {
  margin-bottom: 16px;
  font-size: var(--fs-xl);
  font-weight: 400;
}

.g-brand {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.g-brand__item {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 12px;
  margin: 0;
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
}

.g-brand__item figcaption {
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.g-brand__item b {
  display: block;
  color: var(--ink-1);
}

.g-wenkai {
  font-family: var(--font-display);
  font-size: 56px;
  line-height: 1.2;
}

.g-paper {
  display: block;
  padding-top: 8px;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 31px,
    var(--line) 31px,
    var(--line) 32px
  );
  color: var(--ink-3);
  line-height: 32px;
  height: 72px;
}

.g-moods {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.g-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px 2px 4px;
  border-radius: var(--radius-pill);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.g-colors { margin-bottom: 16px; }

.g-colors h3 {
  margin-bottom: 8px;
  color: var(--ink-2);
  font-family: var(--font-sans);
  font-size: var(--fs-sm);
}

.g-swatches {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(104px, 1fr));
  gap: 10px;
}

.g-swatch {
  display: flex;
  flex-direction: column;
  gap: 2px;
  font-size: var(--fs-xs);
}

.g-swatch__chip {
  height: 44px;
  margin-bottom: 4px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
}

.g-swatch code { color: var(--ink-3); }

.g-type__row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 16px;
  padding: 10px 0;
  border-bottom: 1px dashed var(--line);
}

.g-type__sample--display { font-family: var(--font-display); }

.g-lockup {
  display: block;
  height: 48px;
  width: auto;
}

.g-type__sample--sans { font-family: var(--font-sans); }

.g-type__usage {
  flex-shrink: 0;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.g-components {
  display: grid;
  gap: 16px;
}

.g-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.g-range {
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.g-range.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.g-plan {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.g-card {
  padding: 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.g-card--plan {
  border-style: dashed;
  box-shadow: none;
}

.g-card__title {
  font-family: var(--font-display);
  font-size: var(--fs-lg);
}

.g-card__text {
  margin-top: 4px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.g-rules {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
  margin: 0;
  padding-left: 1.2em;
}

.g-rules p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

@container (max-width: 760px) {
  .g { padding: 24px 16px 40px; }
  .g-head h1 { font-size: var(--fs-2xl); }
  .g-brand { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .g-rules { grid-template-columns: 1fr; }
  .g-type__row { flex-direction: column; gap: 2px; }
}

@container (max-width: 420px) {
  .g-brand { grid-template-columns: 1fr; }
}
</style>
