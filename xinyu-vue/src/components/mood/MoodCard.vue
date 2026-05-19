<script setup>
// 心情记录卡片。展示心情类型、时间、内容（3 行截断）和 AI 回复（2 行截断）。
// 点查看详情触发 @detail，点删除触发 @delete，loading 状态由父级传入。
import BaseCard from '../ui/BaseCard.vue'
import BaseTag from '../ui/BaseTag.vue'
import BaseButton from '../ui/BaseButton.vue'
import { moodTone, formatTime } from '../../utils/mood'

defineProps({
  mood: { type: Object, required: true },
  detailLoading: Boolean,
  deleting: Boolean
})

defineEmits(['detail', 'delete'])
</script>

<template>
  <BaseCard padding="md" hoverable class="mood-card">
    <div class="mood-card__top">
      <BaseTag :tone="moodTone(mood.moodType)" variant="soft" size="md">
        {{ mood.moodType }}
      </BaseTag>
      <span class="mood-card__time">{{ formatTime(mood.createTime) }}</span>
    </div>

    <p class="mood-card__content clamp-3">{{ mood.content }}</p>

    <div class="mood-card__ai">
      <div class="mood-card__ai-title">
        <span class="dot" /> 心屿回复
      </div>
      <p class="mood-card__ai-text clamp-2">
        {{ mood.aiReply || '心屿还在为你写回信…' }}
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

.mood-card__ai-title {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: #a45a4a;
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 1px;
  margin-bottom: 4px;
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
</style>
