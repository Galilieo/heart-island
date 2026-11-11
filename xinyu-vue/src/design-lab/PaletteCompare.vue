<script setup>
// 配色对比：同一份迷你内容并排套用多套日间配色。
// 每一栏把候选配色的 CSS 变量写在栏容器上，栏内组件读取 var(--brand) 等即可得到对应颜色。
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import XiaoyaNote from './XiaoyaNote.vue'
import { moodExpression } from '../utils/mood'
import { PALETTE_OPTIONS } from './styleOptions'
import { toneStyle } from './mock'
import { inject, ref } from 'vue'
import brandLockup from '../assets/brand/xinyu-lockup.svg'

// 字标取当前选中的版本；单独使用时退回正式字标（站酷小薇加粗版）
const logoSrc = inject('logoSrc', ref(brandLockup))

// 暖杏与“生气”等暖色心情容易混淆，不进入并排对比
const COMPARED_IDS = ['mint', 'sprout', 'moss', 'ink']

const comparedPalettes = COMPARED_IDS.map(id => PALETTE_OPTIONS.find(item => item.id === id))

const SAMPLE_MOODS = ['开心', '平静', '难过', '疲惫']
const SELECTED_MOOD = '难过'

const SAMPLE_WEEK = [
  { day: 5, moodType: '焦虑' },
  { day: 6, moodType: '疲惫' },
  { day: 7, moodType: '平静' },
  { day: 8, moodType: '开心' },
  { day: 9, moodType: '焦虑' },
  { day: 10, moodType: '平静' },
  { day: 11, moodType: '疲惫', isToday: true }
]
</script>

<template>
  <div class="pc">
    <section
      v-for="palette in comparedPalettes"
      :key="palette.id"
      class="pc-col"
      :style="palette.vars"
    >
      <header class="pc-col__head">
        <h2>{{ palette.name }}</h2>
        <div class="pc-chips" aria-hidden="true">
          <span style="background: var(--brand)" />
          <span style="background: var(--brand-deep)" />
          <span style="background: var(--brand-soft)" />
          <span style="background: var(--accent)" />
          <span style="background: var(--bg-base)" />
        </div>
        <p>{{ palette.trait }}</p>
      </header>

      <div class="pc-page">
        <nav class="pc-nav">
          <img :src="logoSrc" alt="心屿" class="pc-logo">
          <a class="is-on">心情</a>
          <a>社区</a>
        </nav>

        <h3 class="pc-hello">午后好，Galilieo</h3>

        <div class="pc-card">
          <div class="pc-compose">
            <span>今天是什么心情？</span>
            <Xiaoya :expression="moodExpression(SELECTED_MOOD)" :size="44" />
          </div>
          <div class="pc-moods">
            <span
              v-for="mood in SAMPLE_MOODS"
              :key="mood"
              class="pc-mood"
              :class="{ 'is-on': mood === SELECTED_MOOD }"
              :style="toneStyle(mood)"
            >{{ mood }}</span>
          </div>
          <BaseButton size="sm">保存这一页</BaseButton>
        </div>

        <div class="pc-card">
          <span class="pc-tag" :style="toneStyle('疲惫')">疲惫</span>
          <p class="pc-text">连着开了三个会，脑子已经转不动了。</p>
          <XiaoyaNote text="累是很正常的，先歇一会儿。" />
          <div class="pc-actions">
            <span class="pc-hug">♥ 抱抱 12</span>
            <span class="pc-fav">已收藏</span>
          </div>
        </div>

        <div class="pc-card pc-week">
          <span
            v-for="cell in SAMPLE_WEEK"
            :key="cell.day"
            class="pc-day"
            :class="{ 'is-today': cell.isToday }"
            :style="toneStyle(cell.moodType)"
          >{{ cell.day }}</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.pc {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 16px;
}

.pc-col {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-base);
  color: var(--ink-1);
  overflow: hidden;
}

.pc-col__head {
  padding: 14px 14px 10px;
  border-bottom: 1px dashed var(--line-strong);
}

.pc-col__head h2 {
  font-size: var(--fs-lg);
  font-weight: 400;
}

.pc-col__head p {
  margin-top: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1.6;
}

.pc-chips {
  display: flex;
  gap: 4px;
  margin-top: 8px;
}

.pc-chips span {
  width: 22px;
  height: 22px;
  border: 1px solid var(--line);
  border-radius: 50%;
}

.pc-page {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 12px;
  background: radial-gradient(circle at 20% 0%, var(--brand-soft), transparent 50%);
}

.pc-nav {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: var(--fs-sm);
  color: var(--ink-2);
}

.pc-logo {
  display: block;
  height: 22px;
  width: auto;
  margin-right: auto;
}

.pc-nav a.is-on {
  border-bottom: 2px solid var(--brand);
  color: var(--ink-1);
  font-weight: 700;
}

.pc-hello {
  font-family: var(--font-display);
  font-size: var(--fs-xl);
  font-weight: 400;
}

.pc-card {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.pc-compose {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: var(--font-display);
}

.pc-moods {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 8px 0 10px;
}

.pc-mood {
  padding: 2px 8px;
  border: 1px solid var(--line);
  border-radius: var(--radius-1);
  color: var(--ink-2);
  font-size: var(--fs-xs);
}

.pc-mood.is-on {
  border-color: var(--tone);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

.pc-tag {
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.pc-text {
  margin-top: 6px;
  font-size: var(--fs-sm);
}

.pc-actions {
  display: flex;
  gap: 10px;
  margin-top: 10px;
  font-size: var(--fs-xs);
}

.pc-hug { color: var(--accent-ink); }

.pc-fav {
  padding: 0 8px;
  border-radius: var(--radius-pill);
  background: var(--accent-soft);
  color: var(--accent-ink);
}

.pc-week {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
}

.pc-day {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border-radius: 50%;
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.pc-day.is-today { box-shadow: inset 0 0 0 2px var(--brand); }

@container (max-width: 980px) {
  .pc { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}

@container (max-width: 520px) {
  .pc { grid-template-columns: 1fr; padding: 12px; }
}
</style>
