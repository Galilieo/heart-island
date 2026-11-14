<script setup>
import { computed } from 'vue'
import BaseButton from '../ui/BaseButton.vue'
import Xiaoya from '../ui/Xiaoya.vue'

const props = defineProps({ mood: { type: Object, required: true }, now: Number, busy: Boolean })
defineEmits(['retry', 'refresh'])

const messages = {
  queued: 'AI 回复正在排队，记录已经保存。',
  running: 'AI 正在生成回复，你可以继续记录或离开页面。',
  failed: '这次 AI 回复未能生成，心情记录已保存。',
  disabled: '本条记录未开启 AI 回复。',
  unavailable: '暂未找到对应的生成任务，请刷新核对。',
  superseded: '内容已更新，请刷新查看当前回复状态。',
  cancelled: '本次生成已取消。',
}
const remaining = computed(() => {
  if (props.mood.aiReplyState !== 'failed' || !props.mood.aiJob?.retryAfterSeconds) {
    return 0
  }
  return Math.max(0, Math.ceil(((props.mood.retryAvailableAt || 0) - props.now) / 1000))
})
const canRetry = computed(() => {
  const job = props.mood.aiJob
  const eligible = job?.retryable || job?.retryAfterSeconds > 0
  return props.mood.aiReplyState === 'failed' && eligible && remaining.value === 0 &&
    !props.mood.observationIssue && !props.busy
})
const observationMessage = computed(() => {
  if (props.mood.observationIssue === 'removed') return '记录已不可用，请刷新列表核对。'
  if (props.mood.observationIssue === 'limit') return '已暂停自动查询，生成可能仍在继续，可稍后刷新查看。'
  if (props.mood.observationIssue) return '暂时未能查询回复状态，请刷新后查看。'
  return ''
})

// 仅用于展示：排队 / 生成中的状态配一个小芽“思考”表情；不参与任何状态判断。
// 小于 36px 时头顶的“…”气泡和挑眉看不清，会被看成普通笑脸。
const isThinking = computed(() => ['queued', 'running'].includes(props.mood.aiReplyState))
</script>

<template>
  <div class="ai-status" role="status" aria-live="polite">
    <p class="ai-status__message">
      <Xiaoya v-if="isThinking" expression="thinking" animated :size="36" />
      <span>{{ messages[mood.aiReplyState] || '暂无 AI 回复。' }}</span>
    </p>
    <p v-if="observationMessage">{{ observationMessage }}</p>
    <p v-else-if="remaining > 0">还需等待 {{ remaining }} 秒，之后可手动重试。</p>
    <div class="ai-status__actions">
      <BaseButton v-if="mood.aiReplyState === 'failed'" variant="soft" size="sm"
        :disabled="!canRetry" :loading="busy" @click="$emit('retry', mood.id)">重试 AI 回复</BaseButton>
      <BaseButton v-if="observationMessage || ['unavailable', 'superseded', 'cancelled'].includes(mood.aiReplyState)"
        variant="link" size="sm" :disabled="busy" @click="$emit('refresh')">刷新状态</BaseButton>
    </div>
  </div>
</template>

<style scoped>
.ai-status { margin-top: 12px; color: var(--ink-2); font-size: var(--fs-sm); }
.ai-status p { margin: 6px 0; line-height: 1.7; }
.ai-status__message { display: flex; align-items: center; gap: 8px; }
.ai-status__actions { display: flex; flex-wrap: wrap; gap: 8px; }
</style>
