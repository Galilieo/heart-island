<script setup>
import { computed } from 'vue'
import BaseButton from '../ui/BaseButton.vue'
import Xiaoya from '../ui/Xiaoya.vue'

const props = defineProps({
  mood: { type: Object, required: true },
  now: Number,
  busy: Boolean,
  community: Boolean
})
defineEmits(['retry', 'refresh'])

const messages = {
  queued: 'AI 回复正在排队，记录已经保存。',
  running: 'AI 正在生成回复，你可以继续记录或离开页面。',
  failed: '这次 AI 回复未能生成，心情记录已保存。',
  disabled: '本条记录未开启 AI 回复。',
  unavailable: '暂未找到对应的生成任务，请刷新核对。',
  superseded: '内容已更新，请刷新查看当前回复状态。',
  cancelled: '本次生成已取消。'
}

// 限流错误的专属文案（对应后端 rate_limited_* 错误码）：
// 记录已保存，只是这次不生成；小时额度稍后可重试，每天额度明天恢复。
const rateLimitMessages = {
  rate_limited_day: '今天小芽的回复次数用完了，记录已保存。',
  rate_limited_hour: '小芽这会儿回复得太多了，记录已保存，稍后可以重试。',
  rate_limited_global: '小芽这会儿太忙了，记录已保存，稍后可以重试。'
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
  return (
    props.mood.aiReplyState === 'failed' &&
    eligible &&
    remaining.value === 0 &&
    !props.mood.observationIssue &&
    !props.busy
  )
})
const observationMessage = computed(() => {
  if (props.mood.observationIssue === 'removed') {
    return props.community ? '帖子已不可用，请刷新核对。' : '记录已不可用，请刷新列表核对。'
  }
  if (props.mood.observationIssue === 'limit')
    return '已暂停自动查询，生成可能仍在继续，可稍后刷新查看。'
  if (props.mood.observationIssue) return '暂时未能查询回复状态，请刷新后查看。'
  return ''
})

// 仅用于展示：排队 / 生成中的状态配一个小芽“思考”表情；不参与任何状态判断。
// 小于 36px 时头顶的“…”气泡和挑眉看不清，会被看成普通笑脸。
const isThinking = computed(() => ['queued', 'running'].includes(props.mood.aiReplyState))

// 失败且错误码是限流时，用对应文案替换通用失败提示。
const statusMessage = computed(() => {
  const state = props.mood.aiReplyState
  if (props.community && isThinking.value) {
    return '小芽正在写，帖子已保存。'
  }
  if (state === 'failed') {
    const limited = rateLimitMessages[props.mood.aiJob?.errorCode]
    if (limited) return props.community ? limited.replace('记录已保存', '帖子已保存') : limited
    if (props.community) return '小芽这次没写出来，帖子已保存。'
  }
  return messages[state] || '暂无 AI 回复。'
})
</script>

<template>
  <div class="ai-status" role="status" aria-live="polite">
    <p class="ai-status__message">
      <Xiaoya v-if="isThinking" expression="thinking" animated :size="36" />
      <span>{{ statusMessage }}</span>
    </p>
    <p v-if="observationMessage">{{ observationMessage }}</p>
    <p v-else-if="remaining > 0">还需等待 {{ remaining }} 秒，之后可手动重试。</p>
    <div class="ai-status__actions">
      <BaseButton
        v-if="mood.aiReplyState === 'failed'"
        variant="soft"
        size="sm"
        :disabled="!canRetry"
        :loading="busy"
        @click="$emit('retry', mood.id)"
        >重试 AI 回复</BaseButton
      >
      <BaseButton
        v-if="
          observationMessage ||
          ['unavailable', 'superseded', 'cancelled'].includes(mood.aiReplyState)
        "
        variant="link"
        size="sm"
        :disabled="busy"
        @click="$emit('refresh')"
        >刷新状态</BaseButton
      >
    </div>
  </div>
</template>

<style scoped>
.ai-status {
  margin-top: 12px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}
.ai-status p {
  margin: 6px 0;
  line-height: 1.7;
}
.ai-status__message {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ai-status__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
</style>
