<script setup>
// 方案 B · 简净：桌面左侧固定书写栏，右侧统计与记录；去掉涂鸦和渐变，心情用色点标签。
import { computed, ref } from 'vue'
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseSwitch from '../components/ui/BaseSwitch.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import { MOOD_OPTIONS, moodExpression } from '../utils/mood'
import { MOCK_USER, MOCK_GREETING, MOCK_ENTRIES, MOCK_WEEK, MOCK_WEEK_TOTAL, toneStyle } from './mock'

const selectedMood = ref('')
const content = ref('')
const needAiReply = ref(true)
const range = ref('all')
const openReplyIds = ref(new Set())

const maxWeekCount = computed(() => Math.max(...MOCK_WEEK.map(item => item.count)))

function barStyle(item) {
  return {
    ...toneStyle(item.moodType),
    width: `${(item.count / maxWeekCount.value) * 100}%`
  }
}

// AI 回复默认收起，按记录单独展开；用新 Set 替换以触发响应式更新
function toggleReply(entryId) {
  const next = new Set(openReplyIds.value)

  if (next.has(entryId)) {
    next.delete(entryId)
  } else {
    next.add(entryId)
  }

  openReplyIds.value = next
}
</script>

<template>
  <div class="c">
    <nav class="c-nav">
      <span class="c-nav__brand"><Xiaoya expression="default" :size="24" />心屿</span>
      <span class="c-nav__links">
        <a class="is-on">心情</a>
        <a>社区</a>
        <a>收藏</a>
      </span>
      <span class="c-nav__me">{{ MOCK_USER.nickname }}</span>
    </nav>

    <main class="c-main">
      <aside class="c-panel c-compose">
        <p class="c-compose__hello">{{ MOCK_GREETING }}，{{ MOCK_USER.nickname }}</p>
        <h1 class="c-compose__title">记录此刻</h1>

        <p class="c-label">心情</p>
        <div class="c-chips" role="group" aria-label="选择心情">
          <button
            v-for="item in MOOD_OPTIONS"
            :key="item.value"
            type="button"
            class="c-chip"
            :class="{ 'is-on': selectedMood === item.value }"
            :style="toneStyle(item.value)"
            :aria-pressed="selectedMood === item.value"
            @click="selectedMood = item.value"
          >{{ item.label }}</button>
        </div>

        <div v-if="selectedMood" class="c-picked" :style="toneStyle(selectedMood)">
          <Xiaoya :expression="moodExpression(selectedMood)" animated :size="44" />
          <span>已选「{{ selectedMood }}」</span>
        </div>

        <p class="c-label">内容</p>
        <textarea
          v-model="content"
          class="c-input"
          rows="5"
          maxlength="500"
          placeholder="今天发生了什么？你现在是什么感受？"
        />
        <div class="c-compose__meta">
          <BaseSwitch v-model="needAiReply" label="需要 AI 回复" />
          <span>{{ content.length }}/500</span>
        </div>

        <BaseButton block :disabled="!selectedMood || !content.trim()">保存</BaseButton>
      </aside>

      <div class="c-side">
        <section class="c-panel">
          <header class="c-panel__head">
            <h2>近 7 天</h2>
            <span class="c-muted">{{ MOCK_WEEK_TOTAL }} 条记录</span>
          </header>
          <ul class="c-bars">
            <li v-for="item in MOCK_WEEK" :key="item.moodType">
              <span class="c-bars__name">{{ item.moodType }}</span>
              <span class="c-bars__track"><span :style="barStyle(item)" /></span>
              <span class="c-bars__count">{{ item.count }}</span>
            </li>
          </ul>
        </section>

        <section class="c-panel">
          <header class="c-panel__head">
            <h2>记录</h2>
            <div class="c-seg">
              <button :class="{ 'is-on': range === 'all' }" type="button" @click="range = 'all'">全部</button>
              <button :class="{ 'is-on': range === 'week' }" type="button" @click="range = 'week'">近 7 天</button>
              <button :class="{ 'is-on': range === 'month' }" type="button" @click="range = 'month'">近 30 天</button>
            </div>
          </header>

          <article v-for="entry in MOCK_ENTRIES" :key="entry.id" class="c-row" :style="toneStyle(entry.moodType)">
            <div class="c-row__top">
              <span class="c-dot-label">{{ entry.moodType }}</span>
              <span class="c-muted">{{ entry.day }} {{ entry.time }}</span>
            </div>
            <p class="c-row__text">{{ entry.content }}</p>

            <template v-if="entry.aiReply">
              <button type="button" class="c-reply-toggle" @click="toggleReply(entry.id)">
                {{ openReplyIds.has(entry.id) ? '收起 AI 回复' : '查看 AI 回复' }}
              </button>
              <p v-if="openReplyIds.has(entry.id)" class="c-reply">{{ entry.aiReply }}</p>
            </template>
          </article>
        </section>
      </div>
    </main>
  </div>
</template>

<style scoped>
.c {
  min-height: 100%;
  background: var(--bg-base);
  color: var(--ink-1);
}

.c-nav {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 0 24px;
  border-bottom: 1px solid var(--line);
  background: var(--bg-card);
}

.c-nav__brand {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
}

.c-nav__links {
  display: flex;
  gap: 24px;
  flex: 1;
}

.c-nav__links a {
  padding: 16px 0 14px;
  border-bottom: 2px solid transparent;
  color: var(--ink-2);
}

.c-nav__links a.is-on {
  border-bottom-color: var(--ink-1);
  color: var(--ink-1);
  font-weight: 700;
}

.c-nav__me {
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.c-main {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
  max-width: 1120px;
  margin: 0 auto;
  padding: 24px;
}

.c-panel {
  padding: 20px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

/* 书写栏整体不超过一屏，因此可以安全地 sticky，不需要内部滚动 */
.c-compose {
  position: sticky;
  top: 16px;
}

.c-compose__hello {
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.c-compose__title {
  margin-bottom: 16px;
  font-size: var(--fs-xl);
}

.c-label {
  margin: 14px 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.c-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.c-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.c-chip::before {
  content: "";
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--tone);
}

.c-chip.is-on {
  border-color: var(--tone);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

.c-picked {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  color: var(--tone-ink);
  font-size: var(--fs-sm);
}

.c-input {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--line-strong);
  border-radius: var(--radius-1);
  background: var(--bg-card);
  color: var(--ink-1);
  font: inherit;
  line-height: 1.7;
  resize: vertical;
}

.c-input:focus {
  border-color: var(--brand);
  outline: none;
  box-shadow: var(--shadow-focus);
}

.c-compose__meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 10px 0 16px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.c-side {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
}

.c-panel__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.c-panel__head h2 {
  font-family: var(--font-sans);
  font-size: var(--fs-base);
}

.c-muted {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

/* 横向条形图：只列出现过的心情 */
.c-bars {
  display: grid;
  gap: 10px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.c-bars li {
  display: grid;
  grid-template-columns: 40px minmax(0, 1fr) 24px;
  align-items: center;
  gap: 10px;
  font-size: var(--fs-sm);
}

.c-bars__track {
  height: 8px;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
}

.c-bars__track span {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: var(--tone);
}

.c-bars__count {
  color: var(--ink-2);
  text-align: right;
}

.c-seg {
  display: flex;
  padding: 2px;
  border-radius: var(--radius-1);
  background: var(--bg-soft);
}

.c-seg button {
  padding: 3px 10px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.c-seg button.is-on {
  background: var(--bg-card);
  color: var(--ink-1);
  font-weight: 700;
  box-shadow: var(--shadow-sm);
}

.c-row {
  padding: 14px 0;
  border-top: 1px solid var(--line);
}

.c-row__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.c-dot-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--tone-ink);
  font-size: var(--fs-sm);
  font-weight: 700;
}

.c-dot-label::before {
  content: "";
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--tone);
}

.c-row__text {
  margin-top: 6px;
  line-height: 1.7;
}

.c-reply-toggle {
  margin-top: 6px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.c-reply {
  margin-top: 8px;
  padding: 10px 12px;
  border-radius: var(--radius-1);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

@container (max-width: 760px) {
  .c-nav { gap: 18px; padding: 0 16px; }
  .c-nav__links { gap: 16px; }
  .c-main { grid-template-columns: 1fr; gap: 16px; padding: 16px; }
  .c-compose { position: static; }
  .c-side { gap: 16px; }
}
</style>
