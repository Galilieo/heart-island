<script setup>
// 心情时间线条目：左侧日期（日 / 星期 / 时分），右侧心情标签、正文（3 行截断）、
// AI 回复（页边手写批注）或生成状态，以及查看详情 / 删除操作。
// ≤480px 时批注默认收起为一行开关（aiOpen 为纯展示层局部状态）。
import { computed, ref } from 'vue'
import MoodAiStatus from './MoodAiStatus.vue'
import MoodTag from './MoodTag.vue'
import XiaoyaNote from '../ui/XiaoyaNote.vue'
import BaseButton from '../ui/BaseButton.vue'
import { formatTime, isSupportReply } from '../../utils/mood'

const props = defineProps({
  mood: { type: Object, required: true },
  detailLoading: Boolean,
  deleting: Boolean,
  retrying: Boolean,
  now: Number
})

defineEmits(['detail', 'delete', 'retry', 'refresh'])

const aiOpen = ref(false)

const supportReply = computed(() => isSupportReply(props.mood))

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

// createTime 用 new Date 按本地时区解析，与 formatTime 的时区处理保持一致；
// 解析失败（非法或缺字段）时退回 formatTime 原文展示。
const parsedTime = computed(() => {
  const raw = props.mood.createTime
  if (!raw) return null

  const date = new Date(raw)
  if (Number.isNaN(date.getTime())) return null
  return date
})

function pad(value) {
  return String(value).padStart(2, '0')
}

const dayLabel = computed(() => {
  if (!parsedTime.value) return formatTime(props.mood.createTime)
  return String(parsedTime.value.getDate())
})

const weekdayLabel = computed(
  () => parsedTime.value ? WEEKDAYS[parsedTime.value.getDay()] : ''
)

const timeLabel = computed(() => {
  if (!parsedTime.value) return ''
  return `${pad(parsedTime.value.getHours())}:${pad(parsedTime.value.getMinutes())}`
})
</script>

<template>
  <article class="mood-entry">
    <div class="mood-entry__date">
      <span class="mood-entry__day">{{ dayLabel }}</span>
      <span>{{ weekdayLabel }}</span>
      <span>{{ timeLabel }}</span>
    </div>

    <div class="mood-entry__body">
      <MoodTag :mood-type="mood.moodType" :hide-mascot="supportReply" />

      <p class="mood-entry__content clamp-3">{{ mood.content }}</p>

      <template v-if="mood.aiReply">
        <div class="mood-entry__reply" :class="{ 'is-open': aiOpen }">
          <button
            type="button"
            class="mood-entry__reply-toggle"
            :aria-expanded="aiOpen"
            @click="aiOpen = !aiOpen"
          >
            {{ supportReply ? '心屿的回复' : '小芽的回复' }}
            <span class="mood-entry__caret" aria-hidden="true">{{ aiOpen ? '收起 ▴' : '展开 ▾' }}</span>
          </button>
          <XiaoyaNote class="mood-entry__note" :text="mood.aiReply" :support="supportReply" :clamp="3" />
        </div>
      </template>

      <MoodAiStatus v-else :mood="mood" :now="now" :busy="retrying || deleting"
        @retry="id => $emit('retry', id)" @refresh="$emit('refresh')" />

      <div class="mood-entry__actions">
        <BaseButton
          variant="link"
          size="sm"
          :loading="detailLoading"
          :disabled="deleting"
          @click="$emit('detail', mood.id)"
        >查看详情</BaseButton>
        <BaseButton
          variant="link"
          size="sm"
          :loading="deleting"
          @click="$emit('delete', mood.id)"
        >删除</BaseButton>
      </div>
    </div>
  </article>
</template>

<style scoped>
.mood-entry {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  gap: 16px;
  padding: 18px 0;
  border-top: 1px dashed var(--line-strong);
}

.mood-entry__date {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--ink-3);
  font-size: var(--fs-xs);
  text-align: center;
}

.mood-entry__day {
  color: var(--ink-1);
  font-family: var(--font-display);
  font-size: var(--fs-2xl);
  font-weight: 400;
  line-height: 1.2;
  overflow-wrap: anywhere;
}

.mood-entry__body {
  min-width: 0;
}

.mood-entry__content {
  margin: 8px 0;
  color: var(--ink-1);
  font-size: var(--fs-base);
  line-height: 1.8;
}

/* 桌面端不显示折叠开关，AI 回复直接以页边批注展示 */
.mood-entry__reply-toggle {
  display: none;
}

.mood-entry__actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}

@media (max-width: 640px) {
  .mood-entry {
    grid-template-columns: 44px minmax(0, 1fr);
    gap: 12px;
  }
}

/* ≤480px：批注默认收起，点开关后在条目内展开 */
@media (max-width: 480px) {
  .mood-entry__reply-toggle {
    display: flex;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    min-height: 32px;
    padding: 0;
    border: none;
    background: transparent;
    color: var(--brand-deep);
    font-size: var(--fs-xs);
    font-weight: 700;
    text-align: left;
    cursor: pointer;
  }

  .mood-entry__caret {
    color: var(--ink-3);
    font-weight: 600;
  }

  .mood-entry__reply .mood-entry__note {
    display: none;
  }

  .mood-entry__reply.is-open .mood-entry__note {
    display: block;
  }
}
</style>
