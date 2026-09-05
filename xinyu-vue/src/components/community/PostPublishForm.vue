<script setup>
// 发帖表单（弹窗内容）：话题 + 情绪 + 内容（800 字内）+ 是否要心屿回复。
// 表单值在组件内部维护，提交时通过 @submit 把完整 payload 给父级；
// 父级请求成功后调 resetAfterSuccess() 清空表单（情绪 + 内容，话题和开关保留方便连续发）。
import { ref, computed } from 'vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSwitch from '../ui/BaseSwitch.vue'
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
const needAiReply = ref(true)

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
    content: content.value.trim(),
    needAiReply: needAiReply.value
  })
}

function resetAfterSuccess() {
  moodType.value = ''
  content.value = ''
}

defineExpose({ resetAfterSuccess })
</script>

<template>
  <div class="publish">
    <p class="publish__desc">帖子会匿名展示。聊聊你的近况，也请尊重不同的感受。</p>

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
      placeholder="最近有什么想和大家聊聊的？"
    />

    <div class="publish__foot">
      <BaseSwitch v-model="needAiReply" label="需要 AI 回复" :disabled="submitting" />
      <BaseButton
        variant="primary"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >发布</BaseButton>
    </div>
  </div>
</template>

<style scoped>
.publish__desc {
  margin: 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.7;
}

.publish__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin: 16px 0 14px;
}

.publish__foot {
  margin-top: 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

@media (max-width: 480px) {
  .publish__row { grid-template-columns: 1fr; }
}
</style>
