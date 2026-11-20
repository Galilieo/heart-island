<script setup>
import { useTemplateRef, watch } from 'vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseDialog from '../ui/BaseDialog.vue'
import XiaoyaNote from '../ui/XiaoyaNote.vue'

const props = defineProps({ text: { type: String, default: null } })
const emit = defineEmits(['close'])
const dismissButton = useTemplateRef('dismissButton')

watch(
  () => props.text,
  (text) => {
    if (text) {
      dismissButton.value?.$el.focus()
    }
  },
  { flush: 'post' }
)
</script>

<template>
  <BaseDialog
    :model-value="Boolean(text)"
    title="给你的支持"
    width="sm"
    :closable="false"
    persistent
  >
    <XiaoyaNote v-if="text" :text="text" support />
    <template #footer>
      <BaseButton ref="dismissButton" variant="primary" @click="emit('close')">我知道了</BaseButton>
    </template>
  </BaseDialog>
</template>
