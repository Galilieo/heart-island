<script setup>
// 新增心情的表单卡。心情类型使用分组单选按钮（命中后高亮对应颜色）+ 大段 textarea。
// 表单值通过 v-model:mood-type / v-model:content / v-model:need-ai-reply 由父级管理，自身不持有状态。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSwitch from '../ui/BaseSwitch.vue'
import Xiaoya from '../ui/Xiaoya.vue'
import MoodPicker from './MoodPicker.vue'

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

</script>

<template>
  <BaseCard padding="lg" class="mood-form">
    <header class="mood-form__head">
      <div>
        <p class="mood-form__kicker">心情日记</p>
        <h2 class="mood-form__title">现在心情怎么样？</h2>
      </div>
      <Xiaoya expression="default" :size="40" />
    </header>

    <p class="mood-form__desc">记件小事，或者说说现在的感受，写几句就好。</p>

    <MoodPicker
      :model-value="moodType"
      :disabled="adding"
      @update:model-value="$emit('update:moodType', $event)"
    />

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
        label="需要 AI 回复"
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
      {{ adding ? '正在保存…' : '保存这条心情' }}
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
  gap: 12px;
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

@media (max-width: 480px) {
  .mood-form__title { font-size: clamp(20px, 5.4vw, 24px); }
}
</style>
