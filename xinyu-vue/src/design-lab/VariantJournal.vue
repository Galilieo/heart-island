<script setup>
// 方案 A · 手账：日记本式首页。
// withRail 为 true 时（A+）宽屏增加右侧栏：本周统计、本月日历、规划中的记忆与对话入口；
// 窄屏时侧栏卡片插到书写区与时间线之间。
import { computed, ref } from 'vue'
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseSwitch from '../components/ui/BaseSwitch.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import JournalNav from './JournalNav.vue'
import JournalWeek from './JournalWeek.vue'
import XiaoyaNote from './XiaoyaNote.vue'
import { MOOD_OPTIONS, moodExpression } from '../utils/mood'
import {
  MOCK_USER, MOCK_GREETING, MOCK_DATE_LABEL, MOCK_ENTRIES, MASCOT_LINES,
  MOCK_MONTH_LABEL, MOCK_MONTH_CELLS, MOCK_MEMORY, toneStyle
} from './mock'

defineProps({
  withRail: Boolean
})

const WEEKDAY_HEADERS = ['一', '二', '三', '四', '五', '六', '日']

const selectedMood = ref('')
const content = ref('')
const needAiReply = ref(true)
const range = ref('all')

// 未选心情时小芽保持默认表情，选中后换成对应表情并播放动态版本
const headerExpression = computed(() => selectedMood.value ? moodExpression(selectedMood.value) : 'default')

// 选中心情后，副标题换成小芽的一句回应
const composeHint = computed(() => selectedMood.value ? MASCOT_LINES[selectedMood.value] : '选一个最接近的，写几句就好。')

function dayCellStyle(cell) {
  return cell.moodType ? toneStyle(cell.moodType) : {}
}
</script>

<template>
  <div class="j">
    <JournalNav active="心情" :wide="withRail" />

    <div class="j-layout" :class="{ 'j-layout--rail': withRail }">
      <main class="j-page">
        <header class="j-hello">
          <p class="j-hello__date">{{ MOCK_DATE_LABEL }}</p>
          <h1>{{ MOCK_GREETING }}，{{ MOCK_USER.nickname }}</h1>
          <p class="j-hello__sub">上一次你写下的是「平静」。</p>
        </header>

        <section class="j-card j-compose">
          <div class="j-compose__head">
            <div>
              <h2>今天是什么心情？</h2>
              <p>{{ composeHint }}</p>
            </div>
            <Xiaoya :expression="headerExpression" :animated="Boolean(selectedMood)" :size="88" />
          </div>

          <div class="j-picker" role="group" aria-label="选择心情">
            <button
              v-for="item in MOOD_OPTIONS"
              :key="item.value"
              type="button"
              class="j-mood"
              :class="{ 'is-on': selectedMood === item.value }"
              :style="toneStyle(item.value)"
              :aria-pressed="selectedMood === item.value"
              @click="selectedMood = item.value"
            >
              <Xiaoya :expression="moodExpression(item.value)" :size="30" />
              {{ item.label }}
            </button>
          </div>

          <textarea
            v-model="content"
            class="j-paper"
            rows="4"
            maxlength="500"
            placeholder="今天发生了什么？你现在是什么感受？"
          />

          <footer class="j-compose__foot">
            <BaseSwitch v-model="needAiReply" label="需要 AI 回复" />
            <span class="j-count">{{ content.length }}/500</span>
            <BaseButton :disabled="!selectedMood || !content.trim()">保存这一页</BaseButton>
          </footer>
        </section>

        <JournalWeek v-if="!withRail" class="j-card" />

        <section class="j-timeline">
          <header class="j-timeline__head">
            <h2>最近的记录</h2>
            <div class="j-range">
              <button :class="{ 'is-on': range === 'all' }" type="button" @click="range = 'all'">全部</button>
              <button :class="{ 'is-on': range === 'week' }" type="button" @click="range = 'week'">近 7 天</button>
              <button :class="{ 'is-on': range === 'month' }" type="button" @click="range = 'month'">近 30 天</button>
            </div>
          </header>

          <article v-for="entry in MOCK_ENTRIES" :key="entry.id" class="j-entry">
            <div class="j-entry__date">
              <span class="j-entry__day">{{ entry.date }}</span>
              <span>{{ entry.weekday }}</span>
              <span>{{ entry.time }}</span>
            </div>

            <div class="j-entry__body">
              <span class="j-tag" :style="toneStyle(entry.moodType)">
                <Xiaoya :expression="moodExpression(entry.moodType)" :size="22" />{{ entry.moodType }}
              </span>
              <p class="j-entry__text">{{ entry.content }}</p>

              <XiaoyaNote v-if="entry.aiReply" :text="entry.aiReply" />
            </div>
          </article>
        </section>
      </main>

      <aside v-if="withRail" class="j-rail">
        <section class="j-card j-rail__card">
          <h3 class="j-rail__title">这一周</h3>
          <JournalWeek compact />
        </section>

        <section class="j-card j-rail__card">
          <h3 class="j-rail__title">{{ MOCK_MONTH_LABEL }}</h3>
          <div class="j-cal" role="grid" aria-label="本月心情日历">
            <span v-for="name in WEEKDAY_HEADERS" :key="name" class="j-cal__head">{{ name }}</span>
            <span
              v-for="cell in MOCK_MONTH_CELLS"
              :key="cell.key"
              class="j-cal__cell"
              :class="{
                'has-mood': cell.moodType,
                'is-today': cell.isToday,
                'is-future': cell.isFuture
              }"
              :style="dayCellStyle(cell)"
              :title="cell.moodType ? `${cell.day} 日：${cell.moodType}` : undefined"
            >{{ cell.day }}</span>
          </div>
        </section>

        <section class="j-card j-rail__card j-rail__card--plan">
          <h3 class="j-rail__title">小芽记得 <small>规划中</small></h3>
          <ul class="j-memory">
            <li v-for="item in MOCK_MEMORY" :key="item">{{ item }}</li>
          </ul>
          <p class="j-rail__hint">长期记忆上线后，小芽会从你的记录里记住一些事，你可以随时查看和删除。</p>
        </section>

        <section class="j-card j-rail__card j-rail__card--plan j-chat-entry">
          <Xiaoya expression="hopeful" :size="48" />
          <div>
            <h3 class="j-rail__title">想多说几句？ <small>规划中</small></h3>
            <p class="j-rail__hint">和小芽连续聊几轮，它会接着你的记录回应。</p>
          </div>
        </section>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.j {
  min-height: 100%;
  background:
    radial-gradient(circle at 20% 0%, var(--brand-soft), transparent 40%),
    var(--bg-base);
  color: var(--ink-1);
}

.j-layout {
  max-width: 720px;
  margin: 0 auto;
  padding: 0 20px;
}

/* A+：主栏 + 侧栏，侧栏随页面滚动时贴顶 */
.j-layout--rail {
  display: grid;
  grid-template-columns: minmax(0, 680px) 300px;
  justify-content: center;
  gap: 40px;
  max-width: 1100px;
}

.j-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px 0 56px;
  min-width: 0;
}

.j-hello h1 {
  font-family: var(--font-display);
  font-size: var(--fs-3xl);
  font-weight: 400;
}

.j-hello__date {
  color: var(--brand-deep);
  font-size: var(--fs-sm);
}

.j-hello__sub {
  margin-top: 4px;
  color: var(--ink-2);
}

.j-card {
  padding: 24px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

/* 书写区 */
.j-compose__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.j-compose__head h2 {
  font-size: var(--fs-xl);
  font-weight: 400;
}

.j-compose__head p {
  min-height: 1.7em;
  margin-top: 4px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.j-picker {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 8px;
  margin-top: 16px;
}

.j-mood {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px 4px 6px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.j-mood:hover { border-color: var(--tone); }

.j-mood.is-on {
  border-color: var(--tone);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

/* 横线信纸：行高与背景线间距一致，文字落在线上 */
.j-paper {
  width: 100%;
  margin-top: 16px;
  padding: 4px 4px 0;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent 31px,
    var(--line) 31px,
    var(--line) 32px
  );
  color: var(--ink-1);
  font: inherit;
  font-size: var(--fs-base);
  line-height: 32px;
  resize: vertical;
  outline: none;
}

.j-compose__foot {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 16px;
}

.j-count {
  flex: 1;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

/* 时间线 */
.j-timeline__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 12px 0 8px;
}

.j-timeline__head h2 {
  font-size: var(--fs-xl);
  font-weight: 400;
}

.j-range {
  display: flex;
  gap: 4px;
}

.j-range button {
  padding: 4px 10px;
  border: 0;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
}

.j-range button.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.j-entry {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 16px;
  padding: 18px 0;
  border-top: 1px dashed var(--line-strong);
}

.j-entry__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.j-entry__day {
  color: var(--ink-1);
  font-family: var(--font-display);
  font-size: var(--fs-2xl);
  line-height: 1.2;
}

.j-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 10px 2px 4px;
  border-radius: var(--radius-pill);
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.j-entry__text {
  margin-top: 8px;
  font-size: var(--fs-base);
  line-height: 1.8;
}

/* 侧栏：总高度可能超过一屏，因此不做 sticky，避免底部卡片被固定在视口外 */
.j-rail {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 92px;
}

.j-rail__card { padding: 18px; }

.j-rail__title {
  margin-bottom: 12px;
  font-size: var(--fs-lg);
  font-weight: 400;
}

.j-rail__title small {
  margin-left: 6px;
  padding: 1px 8px;
  border-radius: var(--radius-pill);
  background: var(--bg-soft);
  color: var(--ink-3);
  font-family: var(--font-sans);
  font-size: var(--fs-xs);
}

/* 规划中的功能用虚线边框，和已实现的卡片区分开 */
.j-rail__card--plan {
  border-style: dashed;
  box-shadow: none;
}

.j-rail__hint {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  line-height: 1.7;
}

.j-cal {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 4px;
  text-align: center;
  font-size: var(--fs-xs);
}

.j-cal__head { color: var(--ink-3); }

.j-cal__cell {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  border-radius: 50%;
  color: var(--ink-2);
}

.j-cal__cell.has-mood {
  background: var(--tone-soft);
  color: var(--tone-ink);
  font-weight: 700;
}

.j-cal__cell.is-today { box-shadow: inset 0 0 0 2px var(--brand); }

.j-cal__cell.is-future { color: var(--line-strong); }

.j-memory {
  display: grid;
  gap: 8px;
  margin: 0 0 10px;
  padding-left: 1.2em;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.j-chat-entry {
  display: flex;
  align-items: center;
  gap: 12px;
}

.j-chat-entry .j-rail__title { margin-bottom: 4px; }

/* 中等宽度：侧栏放不下时取消两栏，侧栏卡片插到书写区和时间线之间 */
@container (max-width: 980px) {
  .j-layout--rail {
    display: flex;
    flex-direction: column;
    gap: 16px;
    max-width: 720px;
    padding-bottom: 40px;
  }
  .j-layout--rail .j-page,
  .j-layout--rail .j-rail { display: contents; }
  .j-layout--rail .j-hello { order: 1; }
  .j-layout--rail .j-compose { order: 2; }
  .j-layout--rail .j-rail__card { order: 3; }
  .j-layout--rail .j-timeline { order: 4; }
  .j-layout--rail .j-hello { padding-top: 8px; }
}

@container (max-width: 640px) {
  .j-layout { padding: 0 16px; }
  .j-page { padding: 4px 0 40px; gap: 16px; }
  .j-hello h1 { font-size: var(--fs-2xl); }
  .j-card { padding: 18px 16px; }
  .j-compose__head :deep(.xiaoya) { width: 64px !important; height: 64px !important; }
  .j-picker { grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 6px; }
  .j-compose__foot { flex-wrap: wrap; }
  .j-compose__foot :deep(.btn) { width: 100%; }
  .j-entry { grid-template-columns: 44px minmax(0, 1fr); gap: 12px; }
}
</style>
