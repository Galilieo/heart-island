<script setup>
// 心情详情弹窗。默认展示心情/时间/内容/AI 回复；
// 点"编辑"切到编辑模式（chip + textarea + 回复开关），保存后由父级负责调 store.update。
// editing 状态留在组件内部，关闭/切换记录时自动重置。
import { ref, watch, computed } from 'vue'
import BaseDialog from '../ui/BaseDialog.vue'
import BaseTag from '../ui/BaseTag.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSwitch from '../ui/BaseSwitch.vue'
import { moodTone, moodExpression, formatTime } from '../../utils/mood'
import Xiaoya from '../ui/Xiaoya.vue'
import MoodPicker from './MoodPicker.vue'

const props = defineProps({
  modelValue: Boolean,
  mood: { type: Object, default: null },
  updating: Boolean
})

const emit = defineEmits(['update:modelValue', 'update', 'close'])

const editing = ref(false)
const editMoodType = ref('')
const editContent = ref('')
const editNeedAiReply = ref(true)

watch(
  () => props.mood,
  (m) => {
    editing.value = false
    if (m) {
      editMoodType.value = m.moodType || ''
      editContent.value = m.content || ''
      // 开关默认反映现状：有回复则开，没有则关
      editNeedAiReply.value = !!m.aiReply
    }
  },
  { immediate: true }
)

const canSave = computed(
  () =>
    !!editMoodType.value &&
    editContent.value.trim().length > 0 &&
    !props.updating
)

function startEdit() {
  if (!props.mood) return
  editing.value = true
  editMoodType.value = props.mood.moodType || ''
  editContent.value = props.mood.content || ''
  editNeedAiReply.value = !!props.mood.aiReply
}

function cancelEdit() {
  editing.value = false
  if (props.mood) {
    editMoodType.value = props.mood.moodType || ''
    editContent.value = props.mood.content || ''
    editNeedAiReply.value = !!props.mood.aiReply
  }
}

function save() {
  emit('update', {
    id: props.mood.id,
    moodType: editMoodType.value,
    content: editContent.value.trim(),
    needAiReply: editNeedAiReply.value
  })
}

function close() {
  emit('update:modelValue', false)
  emit('close')
}
</script>

<template>
  <BaseDialog
    :model-value="modelValue"
    width="lg"
    title="心情记录详情"
    :persistent="updating"
    @update:model-value="(v) => emit('update:modelValue', v)"
    @close="close"
  >
    <div v-if="mood" class="md">
      <div class="md__meta">
        <BaseTag :tone="moodTone(mood.moodType)" variant="soft" size="lg">
          <Xiaoya :expression="moodExpression(mood.moodType)" :size="36" />
          {{ mood.moodType }}
        </BaseTag>
        <span>创建于 {{ formatTime(mood.createTime) }}</span>
        <span v-if="mood.updateTime && mood.updateTime !== mood.createTime">
          · 更新于 {{ formatTime(mood.updateTime) }}
        </span>
      </div>

      <section class="md__block">
        <h4>心情内容</h4>
        <p>{{ mood.content }}</p>
      </section>

      <section class="md__block md__block--ai">
        <h4>AI 回复</h4>
        <p>{{ mood.aiReply || '暂无 AI 回复' }}</p>
      </section>

      <section v-if="editing" class="md__edit">
        <h4>编辑这段记录</h4>
        <MoodPicker v-model="editMoodType" :disabled="updating" />
        <BaseTextarea
          v-model="editContent"
          :rows="6"
          :maxlength="500"
          :disabled="updating"
          placeholder="编辑心情内容"
        />
        <div class="md__options">
          <BaseSwitch v-model="editNeedAiReply" label="需要 AI 回复" :disabled="updating" />
        </div>
      </section>
    </div>

    <template #footer>
      <template v-if="!editing">
        <BaseButton variant="plain" :disabled="updating" @click="close">关闭</BaseButton>
        <BaseButton variant="soft" :disabled="updating" @click="startEdit">编辑</BaseButton>
      </template>
      <template v-else>
        <BaseButton variant="plain" :disabled="updating" @click="cancelEdit">取消</BaseButton>
        <BaseButton variant="primary" :loading="updating" :disabled="!canSave" @click="save">
          {{ updating ? (editNeedAiReply ? 'AI 正在生成回复…' : '正在保存…') : '保存修改' }}
        </BaseButton>
      </template>
    </template>
  </BaseDialog>
</template>

<style scoped>
.md__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  margin-bottom: 18px;
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

.md__block {
  margin-top: 14px;
  padding: 16px 18px;
  background: var(--bg-soft);
  border-radius: var(--radius-2);
  border: 1px solid var(--line);
}

.md__block h4 {
  margin: 0 0 8px;
  font-size: var(--fs-md);
  font-family: var(--font-sans);
  color: var(--ink-2);
  letter-spacing: 1px;
}

.md__block p {
  margin: 0;
  color: var(--ink-1);
  line-height: 1.85;
  font-size: var(--fs-md);
  white-space: pre-wrap;
}

.md__block--ai {
  position: relative;
  background: linear-gradient(135deg, var(--accent-soft), var(--bg-soft));
  border-color: var(--accent-line);
}

.md__block--ai::before {
  content: '';
  position: absolute;
  left: 0;
  top: 14%;
  bottom: 14%;
  width: 3px;
  border-radius: var(--radius-pill);
  background: var(--accent);
}

.md__edit {
  margin-top: 18px;
  padding: 18px;
  background: var(--bg-card);
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-2);
}

.md__edit h4 {
  margin: 0 0 12px;
  font-size: var(--fs-md);
  color: var(--ink-2);
}

.md__options {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

</style>
