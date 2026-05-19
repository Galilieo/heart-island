<script setup>
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import Doodle from '../ui/Doodle.vue'
import { MOOD_OPTIONS, moodTone } from '../../utils/mood'

const props = defineProps({
  moodType: { type: String, default: '' },
  content: { type: String, default: '' },
  adding: Boolean
})

defineEmits(['update:moodType', 'update:content', 'submit'])

const canSubmit = computed(
  () => !!props.moodType && props.content.trim().length > 0 && !props.adding
)
</script>

<template>
  <BaseCard padding="lg" class="mood-form">
    <header class="mood-form__head">
      <div>
        <p class="mood-form__kicker">今日记录</p>
        <h2 class="mood-form__title">把此刻放下来</h2>
      </div>
      <Doodle name="heart" :size="36" color="var(--accent)" />
    </header>

    <p class="mood-form__desc">写下一点真实感受，心屿会给你一段轻轻的回应。</p>

    <div class="mood-form__moods">
      <button
        v-for="item in MOOD_OPTIONS"
        :key="item.value"
        type="button"
        class="mood-chip"
        :class="[`mood-chip--${moodTone(item.value)}`, { 'is-active': moodType === item.value }]"
        :disabled="adding"
        @click="$emit('update:moodType', item.value)"
      >{{ item.label }}</button>
    </div>

    <BaseTextarea
      :model-value="content"
      :rows="6"
      :maxlength="500"
      :disabled="adding"
      placeholder="今天发生了什么？你现在是什么感受？"
      @update:model-value="(v) => $emit('update:content', v)"
    />

    <BaseButton
      variant="primary"
      size="lg"
      :loading="adding"
      :disabled="!canSubmit"
      block
      @click="$emit('submit')"
    >
      {{ adding ? '心屿正在生成回复…' : '记录今天的心情' }}
    </BaseButton>
  </BaseCard>
</template>

<style scoped>
.mood-form {
  position: sticky;
  top: 88px;
}

.mood-form__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mood-form__kicker {
  margin: 0;
  color: var(--brand-deep);
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
  height: 40px;
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
  background: var(--bg-soft);
}

.mood-chip:disabled { opacity: 0.55; cursor: not-allowed; }

.mood-chip.is-active {
  border-color: transparent;
  color: var(--ink-1);
  font-weight: 700;
  box-shadow: 0 6px 14px rgba(120, 98, 60, 0.12);
}

.mood-chip--joy.is-active   { background: var(--joy-soft); color: #a06b1e; }
.mood-chip--calm.is-active  { background: var(--calm-soft); color: #3f6f6a; }
.mood-chip--warn.is-active  { background: var(--warn-soft); color: #946200; }
.mood-chip--sad.is-active   { background: var(--sad-soft); color: #45597a; }
.mood-chip--tired.is-active { background: var(--tired-soft); color: #6e6557; }
.mood-chip--neutral.is-active { background: var(--brand-soft); color: var(--brand-deep); }

.mood-form > :deep(.field) {
  margin-bottom: 16px;
}

@media (max-width: 980px) {
  .mood-form { position: static; }
}
</style>
