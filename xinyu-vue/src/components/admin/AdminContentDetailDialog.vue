<script setup>
import { RouterLink } from 'vue-router'
import BaseDialog from '../ui/BaseDialog.vue'
import BaseButton from '../ui/BaseButton.vue'
import BaseSelect from '../ui/BaseSelect.vue'
import BaseTag from '../ui/BaseTag.vue'
import SkeletonCard from '../ui/SkeletonCard.vue'
import { HIDE_REASONS, contentStatusMeta, reasonLabel } from '../../utils/communityReport'

defineProps({
  modelValue: {
    type: Boolean,
    required: true
  },
  title: {
    type: String,
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
  gridItems: {
    type: Array,
    default: () => []
  },
  sections: {
    type: Array,
    default: () => []
  },
  selectedHideReason: {
    type: String,
    default: 'ILLEGAL'
  },
  savingStatus: {
    type: Boolean,
    default: false
  },
  readonlyTip: {
    type: String,
    required: true
  }
})

const emit = defineEmits([
  'update:modelValue',
  'update:selectedHideReason',
  'close',
  'status-action'
])

function formatPendingReasons(reasons) {
  if (!reasons?.length) {
    return ''
  }
  return `（${reasons.map(reasonLabel).join('、')}）`
}
</script>

<template>
  <BaseDialog
    :model-value="modelValue"
    :title="title"
    width="lg"
    :persistent="savingStatus"
    @update:model-value="emit('update:modelValue', $event)"
    @close="emit('close')"
  >
    <SkeletonCard v-if="loading" rows="4" />

    <div v-else-if="detail" class="admin-content-dialog__detail">
      <div class="admin-content-dialog__detail-grid">
        <div v-for="item in gridItems" :key="item.label">
          <span>{{ item.label }}</span>
          <BaseTag v-if="item.isStatus" :tone="contentStatusMeta(detail.status).tagTone">
            {{ contentStatusMeta(detail.status).text }}
          </BaseTag>
          <strong v-else>{{ item.value }}</strong>
        </div>
      </div>

      <section
        v-for="section in sections"
        :key="section.title"
        class="admin-content-dialog__detail-section"
      >
        <h3>{{ section.title }}</h3>
        <p>{{ section.content || '-' }}</p>
      </section>

      <section class="admin-content-dialog__detail-section">
        <h3>相关举报</h3>
        <p v-if="detail.pendingReportCount > 0">
          {{ detail.pendingReportCount }} 条待处理{{ formatPendingReasons(detail.pendingReasons) }}
          ·
          <RouterLink to="/admin/reports" class="admin-content-dialog__report-link">
            去举报处理
          </RouterLink>
        </p>
        <p v-else>-</p>
      </section>
    </div>

    <template #footer>
      <div v-if="detail" class="admin-content-dialog__footer">
        <div v-if="detail.status === 1" class="admin-content-dialog__footer-row">
          <BaseSelect
            :model-value="selectedHideReason"
            label="隐藏原因"
            size="sm"
            :options="HIDE_REASONS"
            @update:model-value="emit('update:selectedHideReason', $event)"
          />
          <div class="admin-content-dialog__footer-btns">
            <BaseButton variant="plain" :disabled="savingStatus" @click="emit('close')">
              关闭
            </BaseButton>
            <BaseButton
              variant="danger"
              :disabled="savingStatus"
              :loading="savingStatus"
              @click="emit('status-action', 2)"
            >
              隐藏
            </BaseButton>
          </div>
        </div>

        <div v-else-if="detail.status === 2" class="admin-content-dialog__footer-row">
          <span class="admin-content-dialog__hidden-reason">
            隐藏原因：{{ reasonLabel(detail.hideReason) }}（作者看得到）
          </span>
          <div class="admin-content-dialog__footer-btns">
            <BaseButton variant="plain" :disabled="savingStatus" @click="emit('close')">
              关闭
            </BaseButton>
            <BaseButton
              variant="primary"
              :disabled="savingStatus"
              :loading="savingStatus"
              @click="emit('status-action', 1)"
            >
              恢复显示
            </BaseButton>
          </div>
        </div>

        <div v-else class="admin-content-dialog__footer-row">
          <span class="admin-content-dialog__readonly-tip">{{ readonlyTip }}</span>
          <BaseButton variant="plain" @click="emit('close')">关闭</BaseButton>
        </div>
      </div>
      <BaseButton v-else variant="plain" @click="emit('close')">关闭</BaseButton>
    </template>
  </BaseDialog>
</template>

<style scoped>
.admin-content-dialog__detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.admin-content-dialog__detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.admin-content-dialog__detail-grid > div {
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-soft);
}

.admin-content-dialog__detail-grid span {
  display: block;
  margin-bottom: 6px;
  color: var(--ink-2);
  font-size: var(--fs-xs);
  font-weight: 700;
}

.admin-content-dialog__detail-grid strong {
  color: var(--ink-1);
  font-size: var(--fs-sm);
  word-break: break-word;
}

.admin-content-dialog__detail-section {
  padding: 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  background: var(--bg-card);
}

.admin-content-dialog__detail-section h3 {
  margin: 0 0 8px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.admin-content-dialog__detail-section p {
  margin: 0;
  color: var(--ink-1);
  font-size: var(--fs-sm);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.admin-content-dialog__report-link {
  color: var(--brand-deep);
  font-weight: 600;
  text-decoration: none;
}

.admin-content-dialog__footer {
  width: 100%;
}

.admin-content-dialog__footer-row {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.admin-content-dialog__footer-btns {
  display: flex;
  gap: 10px;
}

.admin-content-dialog__hidden-reason,
.admin-content-dialog__readonly-tip {
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

@media (max-width: 768px) {
  .admin-content-dialog__detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
