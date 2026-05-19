<script setup>
import { ref, computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'

const props = defineProps({
  submitting: Boolean
})

const emit = defineEmits(['submit'])

const content = ref('')

const canSubmit = computed(
  () => content.value.trim().length > 0 &&
        content.value.length <= 500 &&
        !props.submitting
)

function submit() {
  if (!canSubmit.value) return
  emit('submit', content.value.trim())
}

function reset() {
  content.value = ''
}

defineExpose({ reset })
</script>

<template>
  <BaseCard padding="md" tone="cream" class="reply-form">
    <p class="reply-form__title">写下你的共鸣</p>
    <BaseTextarea
      v-model="content"
      :rows="3"
      :maxlength="500"
      :disabled="submitting"
      placeholder="留一句温柔的回应，被看见就是被陪伴。"
    />
    <div class="reply-form__foot">
      <BaseButton
        variant="primary"
        size="sm"
        :loading="submitting"
        :disabled="!canSubmit"
        @click="submit"
      >发送回复</BaseButton>
    </div>
  </BaseCard>
</template>

<style scoped>
.reply-form__title {
  margin: 0 0 10px;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-2);
}

.reply-form__foot {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
