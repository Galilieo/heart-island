<script setup>
// 方案 C · 小芽陪伴：小芽是主角。选心情时它换表情并回应一句，记录排成“你说 / 小芽回”的对话。
import { computed, ref } from 'vue'
import Xiaoya from '../components/ui/Xiaoya.vue'
import BaseSwitch from '../components/ui/BaseSwitch.vue'
import BaseButton from '../components/ui/BaseButton.vue'
import { MOOD_OPTIONS, moodExpression } from '../utils/mood'
import {
  MOCK_USER, MOCK_GREETING, MOCK_ENTRIES, MOCK_WEEK, MOCK_WEEK_TOTAL, MASCOT_LINES, toneStyle
} from './mock'

const selectedMood = ref('')
const content = ref('')
const needAiReply = ref(true)

const heroExpression = computed(() => selectedMood.value ? moodExpression(selectedMood.value) : 'default')

// 未选心情时打招呼；选中后换成对应心情的一句回应
const heroLine = computed(() => {
  if (!selectedMood.value) {
    return `${MOCK_GREETING}，${MOCK_USER.nickname}。今天过得怎么样？`
  }
  return MASCOT_LINES[selectedMood.value]
})

const topMood = MOCK_WEEK[0]
</script>

<template>
  <div class="m">
    <nav class="m-nav">
      <span class="m-nav__brand"><Xiaoya expression="default" :size="28" />心屿</span>
      <span class="m-nav__links">
        <a class="is-on">心情</a>
        <a>社区</a>
        <a>收藏</a>
      </span>
    </nav>

    <main class="m-page">
      <section class="m-hero">
        <Xiaoya :key="heroExpression" :expression="heroExpression" animated :size="132" class="m-hero__mascot" />
        <p class="m-bubble">{{ heroLine }}</p>
      </section>

      <div class="m-moods" role="group" aria-label="选择心情">
        <button
          v-for="item in MOOD_OPTIONS"
          :key="item.value"
          type="button"
          class="m-mood"
          :class="{ 'is-on': selectedMood === item.value }"
          :style="toneStyle(item.value)"
          :aria-pressed="selectedMood === item.value"
          @click="selectedMood = item.value"
        >
          <span class="m-mood__face"><Xiaoya :expression="moodExpression(item.value)" :size="44" /></span>
          {{ item.label }}
        </button>
      </div>

      <section class="m-composer">
        <textarea
          v-model="content"
          rows="3"
          maxlength="500"
          :placeholder="selectedMood ? '写给小芽…' : '先选一个心情，再写给小芽…'"
        />
        <footer>
          <BaseSwitch v-model="needAiReply" label="让小芽回复" />
          <span class="m-count">{{ content.length }}/500</span>
          <BaseButton :disabled="!selectedMood || !content.trim()">写好了</BaseButton>
        </footer>
      </section>

      <section class="m-week">
        <Xiaoya :expression="moodExpression(topMood.moodType)" :size="52" />
        <p>小芽的周记：这周你写了 <b>{{ MOCK_WEEK_TOTAL }}</b> 次，最常是「{{ topMood.moodType }}」。</p>
      </section>

      <section class="m-talk">
        <template v-for="entry in MOCK_ENTRIES" :key="entry.id">
          <p class="m-day">{{ entry.day }} · {{ entry.time }}</p>

          <div class="m-say m-say--me" :style="toneStyle(entry.moodType)">
            <div class="m-say__bubble">
              <span class="m-say__mood">{{ entry.moodType }}</span>
              <p>{{ entry.content }}</p>
            </div>
          </div>

          <div v-if="entry.aiReply" class="m-say m-say--bot">
            <Xiaoya :expression="moodExpression(entry.moodType)" :size="36" />
            <div class="m-say__bubble"><p>{{ entry.aiReply }}</p></div>
          </div>
          <p v-else class="m-quiet">这一条没有请小芽回复</p>
        </template>
      </section>
    </main>
  </div>
</template>

<style scoped>
.m {
  min-height: 100%;
  background:
    radial-gradient(ellipse at 50% 0%, var(--brand-soft), transparent 55%),
    var(--bg-base);
  color: var(--ink-1);
}

.m-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  max-width: 760px;
  margin: 0 auto;
  padding: 16px 20px;
}

.m-nav__brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-display);
  font-size: var(--fs-xl);
}

.m-nav__links {
  display: flex;
  gap: 6px;
  padding: 4px;
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.m-nav__links a {
  padding: 4px 14px;
  border-radius: var(--radius-pill);
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.m-nav__links a.is-on {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

.m-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 760px;
  margin: 0 auto;
  padding: 8px 20px 56px;
}

/* 小芽 + 对话气泡 */
.m-hero {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 20px;
  padding-top: 8px;
}

.m-bubble {
  position: relative;
  max-width: 320px;
  padding: 14px 18px;
  border-radius: var(--radius-3);
  background: var(--bg-card);
  box-shadow: var(--shadow-md);
  font-family: var(--font-display);
  font-size: var(--fs-lg);
  line-height: 1.6;
}

/* 气泡左侧的小尖角，指向小芽 */
.m-bubble::before {
  content: "";
  position: absolute;
  top: 50%;
  left: -7px;
  width: 14px;
  height: 14px;
  background: inherit;
  transform: translateY(-50%) rotate(45deg);
}

.m-moods {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 6px 4px;
}

.m-mood {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  width: 68px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  cursor: pointer;
}

.m-mood__face {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  border: 2px solid transparent;
  border-radius: 50%;
  background: var(--bg-card);
  transition: transform var(--t-fast) var(--ease-bounce);
}

.m-mood:hover .m-mood__face { transform: translateY(-2px); }

.m-mood.is-on { color: var(--tone-ink); font-weight: 700; }

.m-mood.is-on .m-mood__face {
  border-color: var(--tone);
  background: var(--tone-soft);
}

/* 聊天式输入框 */
.m-composer {
  padding: 14px 16px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-4);
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
}

.m-composer textarea {
  width: 100%;
  border: 0;
  background: transparent;
  color: var(--ink-1);
  font: inherit;
  font-size: var(--fs-base);
  line-height: 1.7;
  resize: none;
  outline: none;
}

.m-composer footer {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-top: 6px;
}

.m-count {
  flex: 1;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.m-week {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius-3);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.m-week b {
  color: var(--ink-1);
  font-family: var(--font-display);
  font-size: var(--fs-lg);
}

/* 对话：用户在右，小芽在左 */
.m-talk {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.m-day {
  margin-top: 10px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  text-align: center;
}

.m-say {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.m-say--me { justify-content: flex-end; }

.m-say__bubble {
  max-width: 78%;
  padding: 10px 14px;
  border-radius: var(--radius-3);
  line-height: 1.7;
}

.m-say--me .m-say__bubble {
  border-bottom-right-radius: 4px;
  background: var(--tone-soft);
}

.m-say--bot .m-say__bubble {
  border-bottom-left-radius: 4px;
  background: var(--bg-card);
  box-shadow: var(--shadow-sm);
  color: var(--ink-2);
}

.m-say__mood {
  color: var(--tone-ink);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.m-quiet {
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

/* 窄屏：小芽和气泡上下排列；心情改为可横向滑动的一行，减少首屏高度 */
@container (max-width: 640px) {
  .m-nav { padding: 14px 16px; }
  .m-page { padding: 0 16px 40px; gap: 16px; }
  .m-hero { flex-direction: column; gap: 12px; }
  .m-hero__mascot { width: 96px !important; height: 96px !important; }
  .m-bubble { font-size: var(--fs-base); text-align: center; }
  .m-bubble::before { top: -7px; left: 50%; transform: translateX(-50%) rotate(45deg); }
  .m-moods {
    flex-wrap: nowrap;
    justify-content: flex-start;
    margin: 0 -16px;
    padding: 4px 16px 8px;
    overflow-x: auto;
    scroll-snap-type: x mandatory;
    scrollbar-width: none;
  }
  .m-mood { flex: 0 0 60px; scroll-snap-align: start; }
  .m-composer footer { flex-wrap: wrap; }
  .m-say__bubble { max-width: 86%; }
}
</style>
