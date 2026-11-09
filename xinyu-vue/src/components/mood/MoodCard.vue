<script setup>
// 心情记录卡片。展示心情类型、时间、内容（3 行截断）和 AI 回复（2 行截断）。
// 点查看详情触发 @detail，点删除触发 @delete，loading 状态由父级传入。
import { ref } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTag from '../ui/BaseTag.vue'
import BaseButton from '../ui/BaseButton.vue'
import { moodTone, moodExpression, formatTime } from '../../utils/mood'
import Xiaoya from '../ui/Xiaoya.vue'

defineProps({
  mood: { type: Object, required: true },
  detailLoading: Boolean,
  deleting: Boolean
})

defineEmits(['detail', 'delete'])

// ≤480px 时心屿回复默认收起为一行开关，点开在卡内展开。
// 纯展示层局部状态；桌面端始终完整显示。
const aiOpen = ref(false)
</script>

<template>
  <BaseCard
    padding="md"
    hoverable
    class="mood-card motion-pop-in"
    :class="`mood-card--${moodTone(mood.moodType)}`"
  >
    <div class="mood-card__top">
      <BaseTag :tone="moodTone(mood.moodType)" variant="soft" size="md">
        <Xiaoya :expression="moodExpression(mood.moodType)" :size="28" />
        {{ mood.moodType }}
      </BaseTag>
      <span class="mood-card__time">{{ formatTime(mood.createTime) }}</span>
    </div>

    <p class="mood-card__content clamp-3">{{ mood.content }}</p>

    <div v-if="mood.aiReply" class="mood-card__ai" :class="{ 'is-open': aiOpen }">
      <button
        type="button"
        class="mood-card__ai-toggle"
        :aria-expanded="aiOpen"
        @click="aiOpen = !aiOpen"
      >
        <span class="dot" aria-hidden="true" /> AI 回复
        <span class="mood-card__ai-caret" aria-hidden="true">
          {{ aiOpen ? '收起 ▴' : '展开 ▾' }}
        </span>
      </button>
      <p class="mood-card__ai-text clamp-2">
        {{ mood.aiReply || '暂无 AI 回复' }}
      </p>
    </div>

    <div class="mood-card__actions">
      <BaseButton
        variant="soft"
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
  </BaseCard>
</template>

<style scoped>
/* 左侧心情色水线：让每条记录在手账流里一眼可辨 */
.mood-card {
  border-left-width: 3px;
}

.mood-card--joy     { border-left-color: var(--joy); }
.mood-card--calm    { border-left-color: var(--calm); }
.mood-card--warn    { border-left-color: var(--warn); }
.mood-card--sad     { border-left-color: var(--sad); }
.mood-card--tired   { border-left-color: var(--tired); }
.mood-card--neutral { border-left-color: var(--line-strong); }

.mood-card__top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.mood-card__time {
  color: var(--ink-3);
  font-size: var(--fs-xs);
  white-space: nowrap;
}

.mood-card__content {
  margin: 14px 0;
  color: var(--ink-1);
  line-height: 1.75;
  font-size: var(--fs-md);
}

.mood-card__ai {
  position: relative;
  margin-top: 4px;
  padding: 12px 14px 12px 18px;
  background: linear-gradient(135deg, var(--accent-soft), var(--bg-soft));
  border-radius: var(--radius-2);
  overflow: hidden;
}

.mood-card__ai::before {
  content: '';
  position: absolute;
  left: 0;
  top: 10%;
  bottom: 10%;
  width: 3px;
  border-radius: var(--radius-pill);
  background: var(--accent);
}

.mood-card__ai-toggle {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  margin-bottom: 4px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--accent-ink);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 1px;
  text-align: left;
  cursor: pointer;
}

.mood-card__ai-caret {
  display: none;
  margin-left: auto;
  color: var(--accent);
  font-weight: 600;
  letter-spacing: 0;
}

.dot {
  width: 6px;
  height: 6px;
  background: var(--accent);
  border-radius: 50%;
}

.mood-card__ai-text {
  margin: 0;
  color: var(--ink-2);
  line-height: 1.7;
  font-size: var(--fs-sm);
}

.mood-card__actions {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 4px;
}

/* ── 小手机（≤480px）：紧凑记录卡，回复默认折叠 ── */
@media (max-width: 480px) {
  .mood-card__content {
    margin: 10px 0;
    -webkit-line-clamp: 2;
  }

  .mood-card__ai {
    padding: 10px 12px 10px 16px;
  }

  .mood-card__ai-toggle {
    min-height: 32px;
    margin-bottom: 0;
  }

  .mood-card__ai-caret {
    display: inline;
  }

  .mood-card__ai-text {
    display: none;
  }

  .mood-card__ai.is-open .mood-card__ai-text {
    display: -webkit-box;
    margin-top: 6px;
  }

  .mood-card__actions {
    margin-top: 10px;
  }
}
</style>
