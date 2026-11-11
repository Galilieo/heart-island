<script setup>
// 新增心情的书写卡：标题 + 小芽回应式副标题 + 心情小卡 + 横线纸 textarea。
// 表单值通过 v-model:mood-type / v-model:content / v-model:need-ai-reply 由父级管理，自身不持有状态。
// 卡片不再 sticky / 限高内滚（旧写法让桌面端保存按钮藏在卡内滚动区）。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSwitch from '../ui/BaseSwitch.vue'
import Xiaoya from '../ui/Xiaoya.vue'
import MoodPicker from './MoodPicker.vue'
import { MOOD_DEFAULT_HINT, MOOD_RESPONSE_LINES, moodExpression } from '../../utils/mood'

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

// 未选心情时小芽保持默认表情；选中后换成对应表情并播放动态版
const headerExpression = computed(
  () => props.moodType ? moodExpression(props.moodType) : 'default'
)

// 副标题跟着心情变化：选中时展示小芽的一句回应，否则用默认提示
const headerHint = computed(
  () => MOOD_RESPONSE_LINES[props.moodType] || MOOD_DEFAULT_HINT
)

</script>

<template>
  <BaseCard padding="md" class="mood-form">
    <header class="mood-form__head">
      <div class="mood-form__head-text">
        <h2 class="mood-form__title">今天是什么心情？</h2>
        <p class="mood-form__hint">{{ headerHint }}</p>
      </div>
      <Xiaoya :expression="headerExpression" :animated="Boolean(moodType)" :size="88" />
    </header>

    <MoodPicker
      :model-value="moodType"
      :disabled="adding"
      @update:model-value="$emit('update:moodType', $event)"
    />

    <BaseTextarea
      variant="paper"
      :model-value="content"
      :rows="4"
      :maxlength="500"
      :disabled="adding"
      placeholder="今天发生了什么？你现在是什么感受？"
      @update:model-value="(v) => $emit('update:content', v)"
    />

    <div class="mood-form__options">
      <BaseSwitch
        :model-value="needAiReply"
        label="让小芽回复（AI 生成）"
        :disabled="adding"
        @update:model-value="(v) => $emit('update:needAiReply', v)"
      />
    </div>

    <div class="mood-form__actions">
      <BaseButton
        variant="primary"
        size="lg"
        :loading="adding"
        :disabled="!canSubmit"
        @click="$emit('submit')"
      >
        {{ adding ? '正在保存…' : '保存这一页' }}
      </BaseButton>
    </div>
  </BaseCard>
</template>

<style scoped>
.mood-form__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.mood-form__head-text {
  min-width: 0;
}

.mood-form__title {
  font-family: var(--font-display);
  font-size: var(--fs-xl);
  font-weight: 400;
}

/* 副标题随心情变化，固定最小高度，避免切换时下方内容跳动 */
.mood-form__hint {
  min-height: 1.7em;
  margin-top: 4px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
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

.mood-form__actions {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 640px) {
  .mood-form__head :deep(.xiaoya) {
    width: 64px !important;
    height: 64px !important;
  }

  .mood-form__actions :deep(.btn) {
    width: 100%;
  }
}
</style>
