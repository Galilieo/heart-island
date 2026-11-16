<script setup>
import { ref, computed, watch } from 'vue'
import BaseDialog from '../ui/BaseDialog.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import {
  REPORT_REASONS,
  MAX_REPORT_DETAIL_LENGTH,
  formatExcerpt,
  validateReportForm
} from '../../utils/communityReport'

const props = defineProps({
  modelValue: Boolean,
  target: { type: Object, default: null },
  submitting: Boolean,
  serverError: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const reason = ref('')
const detail = ref('')
const localError = ref('')

const isSafety = computed(() => reason.value === 'SAFETY')
const isOther = computed(() => reason.value === 'OTHER')
const errorMessage = computed(() => localError.value || props.serverError)
const targetExcerpt = computed(() => formatExcerpt(props.target?.excerpt))

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      reason.value = ''
      detail.value = ''
      localError.value = ''
    }
  }
)

watch([reason, detail], () => {
  if (localError.value) localError.value = ''
})

function handleClose() {
  if (props.submitting) return
  emit('update:modelValue', false)
}

function handleSubmit() {
  const error = validateReportForm({ reason: reason.value, detail: detail.value })
  if (error) {
    localError.value = error
    return
  }
  emit('submit', {
    targetType: props.target?.targetType,
    targetId: props.target?.targetId,
    reason: reason.value,
    detail: detail.value.trim() || null
  })
}
</script>

<template>
  <BaseDialog
    :model-value="modelValue"
    title="举报这条内容"
    width="md"
    mobile-sheet
    :persistent="submitting"
    @update:model-value="handleClose"
  >
    <div class="report-dlg">
      <p v-if="targetExcerpt" class="report-dlg__excerpt">
        <span>{{ target?.targetType === 'REPLY' ? '举报回复' : '举报帖子' }}：</span>
        {{ targetExcerpt }}
      </p>

      <fieldset class="report-dlg__fieldset">
        <legend class="report-dlg__legend">请选择举报原因</legend>
        <div class="report-dlg__options" role="radiogroup" aria-label="举报原因">
          <label
            v-for="item in REPORT_REASONS"
            :key="item.value"
            class="report-dlg__option"
            :class="{ 'is-selected': reason === item.value }"
          >
            <input
              v-model="reason"
              type="radio"
              name="report-reason"
              :value="item.value"
              class="report-dlg__radio"
            />
            <span>{{ item.label }}</span>
          </label>
        </div>
      </fieldset>

      <div v-if="isSafety" class="report-dlg__safety" role="status">
        如果你担心发帖人的安全，这条举报会排到最前由管理员优先查看，并在需要时给予温柔关注。若对方正在面临现实中的紧急危险，仍需联系身边的亲友或专业机构。
      </div>

      <div class="report-dlg__detail">
        <BaseTextarea
          v-model="detail"
          :label="isOther ? '补充说明（必填）' : '补充说明（选填）'"
          :placeholder="isOther ? '请写一句具体的举报原因' : '可以补充上下文或具体位置，最多 200 字'"
          :rows="3"
          :maxlength="MAX_REPORT_DETAIL_LENGTH"
          show-count
          :disabled="submitting"
        />
      </div>

      <p v-if="errorMessage" class="report-dlg__error" role="alert">
        {{ errorMessage }}
      </p>
    </div>

    <template #footer>
      <BaseButton variant="plain" :disabled="submitting" @click="handleClose">
        取消
      </BaseButton>
      <BaseButton
        variant="primary"
        :disabled="!reason || submitting"
        :loading="submitting"
        @click="handleSubmit"
      >
        提交举报
      </BaseButton>
    </template>
  </BaseDialog>
</template>

<style scoped>
.report-dlg {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.report-dlg__excerpt {
  margin: 0;
  padding: 10px 12px;
  border-radius: var(--radius-2);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.report-dlg__excerpt span {
  color: var(--ink-3);
}

.report-dlg__fieldset {
  margin: 0;
  padding: 0;
  border: none;
}

.report-dlg__legend {
  margin-bottom: 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
}

.report-dlg__options {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.report-dlg__option {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 44px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
  color: var(--ink-1);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: border-color var(--t-fast) var(--ease-soft),
    background var(--t-fast) var(--ease-soft);
}

.report-dlg__option:hover {
  border-color: var(--brand-soft-deep);
}

.report-dlg__option.is-selected {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 600;
}

.report-dlg__radio {
  accent-color: var(--brand);
}

.report-dlg__safety {
  padding: 10px 12px;
  border: 1px solid var(--warn-line, rgba(212, 147, 57, 0.35));
  border-radius: var(--radius-2);
  background: var(--warn-soft, rgba(242, 184, 75, 0.12));
  color: var(--warn-ink, #9a6011);
  font-size: var(--fs-xs);
  line-height: 1.65;
}

.report-dlg__error {
  margin: 0;
  color: var(--danger-ink);
  font-size: var(--fs-sm);
  font-weight: 600;
}

@media (max-width: 768px) {
  .report-dlg__options {
    grid-template-columns: 1fr;
  }
}
</style>
