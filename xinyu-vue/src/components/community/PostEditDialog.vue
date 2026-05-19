<script setup>
// 编辑帖子弹窗。打开/post 变化时把表单值同步成当前帖子的内容。
// 保存中（saving=true）不允许通过遮罩或 ESC 关闭，避免误关丢数据。
import { ref, watch, computed } from 'vue'
import BaseDialog from '../ui/BaseDialog.vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import { MOOD_OPTIONS } from '../../utils/mood'

const props = defineProps({
  modelValue: Boolean,
  post: { type: Object, default: null },
  topics: { type: Array, default: () => [] },
  saving: Boolean
})

const emit = defineEmits(['update:modelValue', 'save'])

const topicId = ref('')
const moodType = ref('')
const content = ref('')

watch(
  () => props.post,
  (p) => {
    if (p) {
      topicId.value = p.topicId ?? ''
      moodType.value = p.moodType ?? ''
      content.value = p.content ?? ''
    }
  },
  { immediate: true }
)

const topicOptions = computed(() =>
  props.topics.map((t) => ({ label: t.name, value: t.id }))
)

const moodSelectOptions = computed(() => [...MOOD_OPTIONS])

const canSave = computed(
  () =>
    !!topicId.value &&
    !!moodType.value &&
    content.value.trim().length > 0 &&
    content.value.length <= 800 &&
    !props.saving
)

function save() {
  if (!canSave.value) return
  emit('save', {
    id: props.post.id,
    topicId: topicId.value,
    moodType: moodType.value,
    content: content.value.trim()
  })
}

function close() {
  emit('update:modelValue', false)
}
</script>

<template>
  <BaseDialog
    :model-value="modelValue"
    width="lg"
    title="编辑帖子"
    :persistent="saving"
    @update:model-value="(v) => emit('update:modelValue', v)"
  >
    <div class="ped">
      <div class="ped__row">
        <BaseSelect v-model="topicId" :options="topicOptions" label="话题" placeholder="选择话题" />
        <BaseSelect v-model="moodType" :options="moodSelectOptions" label="情绪" placeholder="选择情绪" />
      </div>
      <BaseTextarea
        v-model="content"
        :rows="6"
        :maxlength="800"
        :disabled="saving"
        placeholder="重新写一下你想说的"
      />
    </div>

    <template #footer>
      <BaseButton variant="plain" :disabled="saving" @click="close">取消</BaseButton>
      <BaseButton variant="primary" :loading="saving" :disabled="!canSave" @click="save">
        保存修改
      </BaseButton>
    </template>
  </BaseDialog>
</template>

<style scoped>
.ped { display: flex; flex-direction: column; gap: 14px; }

.ped__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

@media (max-width: 600px) {
  .ped__row { grid-template-columns: 1fr; }
}
</style>
