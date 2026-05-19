<script setup>
// 发帖表单：话题 + 情绪 + 内容（800 字内）。
// 表单值在组件内部维护，提交时通过 @submit 把完整 payload 给父级；
// 父级请求成功后调 resetAfterSuccess() 清空表单（情绪 + 内容，话题保留方便连续发）。
import { ref, computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import Doodle from '../ui/Doodle.vue'
import { MOOD_OPTIONS } from '../../utils/mood'

const props = defineProps({
  topics: { type: Array, default: () => [] },
  defaultTopicId: { type: [String, Number, null], default: null },
  submitting: Boolean
})

const emit = defineEmits(['submit'])

const topicId = ref(props.defaultTopicId ?? '')
const moodType = ref('')
const content = ref('')

const topicOptions = computed(() => [
  { label: '选择一个话题', value: '' },
  ...props.topics.map((t) => ({ label: t.name, value: t.id }))
])

const moodOptions = computed(() => [
  { label: '选择情绪', value: '' },
  ...MOOD_OPTIONS
])

const canSubmit = computed(
  () =>
    topicId.value &&
    moodType.value &&
    content.value.trim().length > 0 &&
    content.value.length <= 800 &&
    !props.submitting
)

function submit() {
  if (!canSubmit.value) return
  emit('submit', {
    topicId: topicId.value,
    moodType: moodType.value,
    content: content.value.trim()
  })
}

function resetAfterSuccess() {
  moodType.value = ''
  content.value = ''
}

defineExpose({ resetAfterSuccess })
</script>

<template>
  <BaseCard padding="lg" class="publish">
    <header class="publish__head">
      <div>
        <p class="publish__kicker">发布</p>
        <h2 class="publish__title">写下一点想说的话</h2>
        <p class="publish__desc">内容会以匿名身份展示，请放心表达，但也尽量温柔。</p>
      </div>
      <Doodle name="plant" :size="44" color="var(--brand)" />
    </header>

    <div class="publish__row">
      <BaseSelect
        v-model="topicId"
        :options="topicOptions"
        placeholder="选择话题"
        label="话题"
      />
      <BaseSelect
        v-model="moodType"
        :options="moodOptions"
        placeholder="选择情绪"
        label="情绪"
      />
    </div>

    <BaseTextarea
      v-model="content"
      :rows="5"
      :maxlength="800"
      :disabled="submitting"
      placeholder="今天发生了什么？你想匿名说点什么？"
    />

    <div class="publish__foot">
      <BaseButton
        variant="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >发布</BaseButton>
    </div>
  </BaseCard>
</template>

<style scoped>
.publish__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.publish__kicker {
  margin: 0;
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
  letter-spacing: 2px;
}

.publish__title {
  margin: 6px 0 0;
  font-size: var(--fs-2xl);
}

.publish__desc {
  margin: 8px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.publish__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 20px 0 14px;
}

.publish__foot {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 560px) {
  .publish__row { grid-template-columns: 1fr; }
}
</style>
