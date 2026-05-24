<script setup>
// 心情详情弹窗。默认展示心情/时间/内容/AI 回复；
// 点"编辑"切到编辑模式（chip + textarea），保存后由父级负责调 store.update。
// editing 状态留在组件内部，关闭/切换记录时自动重置。
import { ref, watch, computed } from 'vue'
import BaseDialog from '../ui/BaseDialog.vue'
import BaseTag from '../ui/BaseTag.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import { MOOD_OPTIONS, moodTone, formatTime } from '../../utils/mood'

const props = defineProps({
  modelValue: Boolean,
  mood: { type: Object, default: null },
  updating: Boolean
})

const emit = defineEmits(['update:modelValue', 'update', 'close'])

const editing = ref(false)
const editMoodType = ref('')
const editContent = ref('')

watch(
  () => props.mood,
  (m) => {
    editing.value = false
    if (m) {
      editMoodType.value = m.moodType || ''
      editContent.value = m.content || ''
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
}

function cancelEdit() {
  editing.value = false
  if (props.mood) {
    editMoodType.value = props.mood.moodType || ''
    editContent.value = props.mood.content || ''
  }
}

function save() {
  emit('update', {
    id: props.mood.id,
    moodType: editMoodType.value,
    content: editContent.value.trim()
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
        <h4>心屿回复</h4>
        <p>{{ mood.aiReply || '暂无心屿回复' }}</p>
      </section>

      <section v-if="editing" class="md__edit">
        <h4>编辑这段记录</h4>
        <div class="md__moods">
          <button
            v-for="item in MOOD_OPTIONS"
            :key="item.value"
            type="button"
            class="mood-chip"
            :class="[`mood-chip--${moodTone(item.value)}`, { 'is-active': editMoodType === item.value }]"
            :disabled="updating"
            @click="editMoodType = item.value"
          >{{ item.label }}</button>
        </div>
        <BaseTextarea
          v-model="editContent"
          :rows="6"
          :maxlength="500"
          :disabled="updating"
          placeholder="编辑心情内容"
        />
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
          {{ updating ? '心屿正在重写回复…' : '保存修改' }}
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
  border-color: #f1cdc2;
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

.md__moods {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .md__moods { grid-template-columns: repeat(3, 1fr); }
}

.mood-chip {
  height: 36px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  color: var(--ink-2);
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft);
}

.mood-chip:not(:disabled):hover {
  background: var(--bg-soft);
}

.mood-chip:disabled { opacity: 0.55; cursor: not-allowed; }

.mood-chip.is-active {
  border-color: transparent;
  font-weight: 700;
}

.mood-chip--joy.is-active   { background: var(--joy-soft); color: #a06b1e; }
.mood-chip--calm.is-active  { background: var(--calm-soft); color: #3f6f6a; }
.mood-chip--warn.is-active  { background: var(--warn-soft); color: #946200; }
.mood-chip--sad.is-active   { background: var(--sad-soft); color: #45597a; }
.mood-chip--tired.is-active { background: var(--tired-soft); color: #6e6557; }
.mood-chip--neutral.is-active { background: var(--brand-soft); color: var(--brand-deep); }
</style>
