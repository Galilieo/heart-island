<script setup>
// 新增心情的表单卡。心情类型用 6 个 chip（命中后高亮对应颜色）+ 大段 textarea。
// 表单值通过 v-model:mood-type / v-model:content / v-model:need-ai-reply 由父级管理，自身不持有状态。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSwitch from '../ui/BaseSwitch.vue'
import Doodle from '../ui/Doodle.vue'
import { MOOD_OPTIONS, moodTone } from '../../utils/mood'

const props = defineProps({
  moodType: { type: String, default: '' },
  content: { type: String, default: '' },
  needAiReply: { type: Boolean, default: true },
  adding: Boolean
})

defineEmits(['update:moodType', 'update:content', 'update:needAiReply', 'submit'])

const canSubmit = computed(
  () => !!props.moodType && props.content.trim().length > 0 && !props.adding
)

// 心情 → 岛景天气符号（与「潮汐之窗」同一套视觉语言）
const MOOD_ICONS = {
  开心: 'sun',
  平静: 'wave',
  焦虑: 'cloud',
  难过: 'leaf',
  疲惫: 'cup',
  其他: 'star'
}
</script>

<template>
  <BaseCard padding="lg" class="mood-form">
    <header class="mood-form__head">
      <div>
        <p class="mood-form__kicker">此刻 · 岛上天气</p>
        <h2 class="mood-form__title">把此刻放下来</h2>
      </div>
      <Doodle name="heart" :size="36" color="var(--accent)" />
    </header>

    <p class="mood-form__desc">写下一点真实感受，心屿会给你一段轻轻的回应。</p>

    <div class="mood-form__moods" role="group" aria-label="选择心情类型">
      <button
        v-for="item in MOOD_OPTIONS"
        :key="item.value"
        type="button"
        class="mood-chip"
        :class="[`mood-chip--${moodTone(item.value)}`, { 'is-active': moodType === item.value }]"
        :aria-pressed="moodType === item.value"
        :disabled="adding"
        @click="$emit('update:moodType', item.value)"
      >
        <Doodle :name="MOOD_ICONS[item.value] || 'star'" :size="17" color="currentColor" />
        <span>{{ item.label }}</span>
      </button>
    </div>

    <BaseTextarea
      :model-value="content"
      :rows="6"
      :maxlength="500"
      :disabled="adding"
      placeholder="今天发生了什么？你现在是什么感受？"
      @update:model-value="(v) => $emit('update:content', v)"
    />

    <div class="mood-form__options">
      <BaseSwitch
        :model-value="needAiReply"
        label="让心屿回应我"
        :disabled="adding"
        @update:model-value="(v) => $emit('update:needAiReply', v)"
      />
    </div>

    <BaseButton
      variant="primary"
      size="lg"
      :loading="adding"
      :disabled="!canSubmit"
      block
      @click="$emit('submit')"
    >
      {{ adding ? (needAiReply ? '心屿正在生成回复…' : '正在记录…') : '记录今天的心情' }}
    </BaseButton>
  </BaseCard>
</template>

<style scoped>
.mood-form {
  position: sticky;
  top: 88px;
  /* 吸附后若表单比视口高，底部按钮会永远够不着：限高 + 内部滚动 */
  max-height: calc(100dvh - 104px);
  overflow-y: auto;
  scrollbar-width: thin;
}

.mood-form__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mood-form__kicker {
  margin: 0;
  color: var(--sea-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.mood-form__title {
  margin: 6px 0 0;
  font-size: var(--fs-2xl);
}

.mood-form__desc {
  margin: 8px 0 18px;
  color: var(--ink-2);
  line-height: 1.7;
}

.mood-form__moods {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  margin-bottom: 16px;
}

.mood-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 44px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  color: var(--ink-2);
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: transform var(--t-fast) var(--ease-bounce),
    background var(--t-fast) var(--ease-soft),
    border-color var(--t-fast) var(--ease-soft);
}

.mood-chip:not(:disabled):hover {
  transform: translateY(-1px);
  background: var(--sea-soft);
  border-color: var(--sea-soft);
}

.mood-chip:disabled { opacity: 0.55; cursor: not-allowed; }

.mood-chip.is-active {
  border-color: transparent;
  color: var(--ink-1);
  font-weight: 700;
  box-shadow: 0 6px 14px rgba(120, 98, 60, 0.12);
}

.mood-chip--joy.is-active   { background: var(--joy-soft); color: var(--joy-ink); }
.mood-chip--calm.is-active  { background: var(--calm-soft); color: var(--calm-ink); }
.mood-chip--warn.is-active  { background: var(--warn-soft); color: var(--warn-ink); }
.mood-chip--sad.is-active   { background: var(--sad-soft); color: var(--sad-ink); }
.mood-chip--tired.is-active { background: var(--tired-soft); color: var(--tired-ink); }
.mood-chip--neutral.is-active { background: var(--brand-soft); color: var(--brand-deep); }

.mood-form > :deep(.field) {
  margin-bottom: 16px;
}

.mood-form__options {
  display: flex;
  justify-content: flex-end;
  margin: -4px 0 12px;
}

@media (max-width: 1024px) {
  /* 非桌面布局不吸附，也不需要限高内滚（否则卡片出内部滚动条） */
  .mood-form {
    position: static;
    max-height: none;
    overflow: visible;
  }
}
</style>
