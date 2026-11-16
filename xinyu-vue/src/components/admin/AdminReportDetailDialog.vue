<script setup>
import BaseDialog from '../ui/BaseDialog.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseInput from '../ui/BaseInput.vue'
import SkeletonCard from '../ui/SkeletonCard.vue'
import {
  HIDE_REASONS,
  MAX_HANDLE_NOTE_LENGTH,
  adminReportResultMeta,
  formatFullDateTime,
  formatReportTime,
  reasonLabel
} from '../../utils/communityReport'

defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  loading: {
    type: Boolean,
    default: false
  },
  detail: {
    type: Object,
    default: null
  },
  isPendingTab: {
    type: Boolean,
    default: true
  },
  submitting: {
    type: Boolean,
    default: false
  },
  submittingAction: {
    type: String,
    default: ''
  },
  selectedHideReason: {
    type: String,
    default: 'ILLEGAL'
  },
  handleNote: {
    type: String,
    default: ''
  }
})

const emit = defineEmits([
  'update:modelValue',
  'update:selectedHideReason',
  'update:handleNote',
  'close',
  'submit'
])
</script>

<template>
  <BaseDialog
    :model-value="modelValue"
    title="举报详情"
    width="lg"
    :persistent="submitting"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('close')"
  >
    <SkeletonCard v-if="loading" rows="4" />

    <div v-else-if="detail" class="admin-report-dialog__detail">
      <div class="admin-report-dialog__detail-grid">
        <div>
          <span>举报对象</span>
          <strong>
            {{ detail.targetType === 'REPLY' ? '回复' : '帖子' }} #{{ detail.targetId }}
          </strong>
        </div>
        <div>
          <span>作者</span>
          <strong>
            用户 #{{ detail.targetUserId }} · 此前被隐藏 {{ detail.authorHiddenCount || 0 }} 次
          </strong>
        </div>
        <div>
          <span>举报人数</span>
          <strong>{{ detail.reporterCount }} 人</strong>
        </div>
        <div>
          <span>首次举报</span>
          <strong>{{ formatFullDateTime(detail.firstReportTime) }}</strong>
        </div>
      </div>

      <div v-if="detail.hasSafety" class="admin-report-dialog__safety-banner">
        这是「担心发帖人的安全」举报。隐藏不一定是最好的处理：内容本身不违规时可以选「不违规」，并留意这位用户之后的帖子。
      </div>

      <section class="admin-report-dialog__detail-section">
        <h3>举报时的内容</h3>
        <p class="admin-report-dialog__snapshot">{{ detail.snapshotContent || '-' }}</p>
        <p v-if="detail.currentStatus === 0" class="admin-report-dialog__diff-note">
          当前内容已被作者删除。
        </p>
        <p v-else-if="detail.contentChanged" class="admin-report-dialog__diff-note">
          作者在被举报后改过内容。现在是：{{ detail.currentContent }}
        </p>
        <p v-if="detail.currentStatus === 2" class="admin-report-dialog__diff-note">
          这条内容已被隐藏，原因：{{ reasonLabel(detail.currentHideReason) }}。
        </p>
      </section>

      <section class="admin-report-dialog__detail-section">
        <h3>举报记录</h3>
        <ul class="admin-report-dialog__record-list">
          <li v-for="rec in detail.records" :key="rec.id" class="admin-report-dialog__record">
            <div class="admin-report-dialog__record-head">
              <strong>{{ reasonLabel(rec.reason) }}</strong>
              <span>举报人 #{{ rec.reporterId }} · {{ formatReportTime(rec.createTime) }}</span>
            </div>
            <p v-if="rec.detail" class="admin-report-dialog__record-detail">{{ rec.detail }}</p>
          </li>
        </ul>
      </section>

      <section v-if="!isPendingTab && detail.resultStatus" class="admin-report-dialog__detail-section">
        <h3>处理结果</h3>
        <p>
          {{ adminReportResultMeta(detail.resultStatus, detail.hideReason).text }}
          · 处理人 {{ detail.handlerName || '-' }}
          · {{ formatFullDateTime(detail.handleTime) }}
        </p>
        <p v-if="detail.handleNote" class="admin-report-dialog__handled-note">
          内部备注：{{ detail.handleNote }}
        </p>
      </section>
    </div>

    <template #footer>
      <div v-if="isPendingTab && detail" class="admin-report-dialog__footer">
        <div class="admin-report-dialog__footer-inputs">
          <BaseSelect
            v-if="detail.currentStatus !== 0"
            :model-value="selectedHideReason"
            label="隐藏原因"
            size="sm"
            :options="HIDE_REASONS"
            @update:model-value="emit('update:selectedHideReason', $event)"
          />
          <BaseInput
            :model-value="handleNote"
            label="内部备注"
            placeholder="选填，只有管理员看得到"
            :maxlength="MAX_HANDLE_NOTE_LENGTH"
            @update:model-value="emit('update:handleNote', $event)"
          />
        </div>
        <div class="admin-report-dialog__footer-btns">
          <BaseButton variant="plain" :disabled="submitting" @click="emit('close')">
            关闭
          </BaseButton>
          <BaseButton
            variant="secondary"
            :disabled="submitting"
            :loading="submittingAction === 'DISMISS'"
            @click="emit('submit', 'DISMISS')"
          >
            {{ detail.currentStatus === 0 ? '关闭举报' : '不违规' }}
          </BaseButton>
          <BaseButton
            v-if="detail.currentStatus !== 0"
            variant="danger"
            :disabled="submitting"
            :loading="submittingAction === 'HIDE'"
            @click="emit('submit', 'HIDE')"
          >
            隐藏内容
          </BaseButton>
        </div>
      </div>
      <BaseButton v-else variant="plain" @click="emit('close')">
        关闭
      </BaseButton>
    </template>
  </BaseDialog>
</template>

<style scoped>
.admin-report-dialog__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-report-dialog__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-report-dialog__detail-grid > div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-soft);
}

.admin-report-dialog__detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.admin-report-dialog__detail-grid strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
  word-break: break-word;
}

.admin-report-dialog__safety-banner {
  padding: 12px 14px;
  border: 1px solid var(--warn-line, rgba(212, 147, 57, 0.35));
  border-radius: var(--radius-2);
  background: var(--warn-soft, rgba(242, 184, 75, 0.12));
  color: var(--warn-ink, #9a6011);
  font-size: var(--fs-sm);
  line-height: 1.65;
}

.admin-report-dialog__detail-section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

.admin-report-dialog__detail-section h3 {
  margin: 0 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-report-dialog__snapshot {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-report-dialog__diff-note {
  margin: 10px 0 0;
  padding: 8px 10px;
  border-radius: var(--radius-1);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: var(--fs-xs);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-report-dialog__record-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.admin-report-dialog__record {
  padding-top: 8px;
  border-top: 1px dashed var(--line);
}

.admin-report-dialog__record:first-child {
  padding-top: 0;
  border-top: none;
}

.admin-report-dialog__record-head {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.admin-report-dialog__record-head strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
}

.admin-report-dialog__record-detail {
  margin: 4px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.6;
  white-space: pre-wrap;
}

.admin-report-dialog__handled-note {
  margin: 6px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-report-dialog__footer {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.admin-report-dialog__footer-inputs {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 10px;
}

.admin-report-dialog__footer-btns {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 768px) {
  .admin-report-dialog__detail-grid,
  .admin-report-dialog__footer-inputs {
    grid-template-columns: 1fr;
  }
}
</style>
