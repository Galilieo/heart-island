<script setup>
// 周报历史信封列：只在周报模式且有历史记录时显示，支持切换周次与「更早的 ›」翻页。
import { formatRangeShort } from '../../utils/review'

defineProps({
  items: {
    type: Array,
    default: () => []
  },
  activeStart: {
    type: String,
    default: ''
  },
  hasMore: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['select', 'loadMore'])

function statusLabel(item) {
  if (item.status === 'succeeded') {
    return `${item.total} 条记录`
  }
  if (item.status === 'queued' || item.status === 'running') {
    return '生成中'
  }
  return '没有生成'
}
</script>

<template>
  <section v-if="items.length" class="review-history" aria-label="过往周报">
    <h4 class="review-history__title">过往周报</h4>

    <div class="review-history__strip">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        class="review-envelope"
        :class="{
          'is-active': item.rangeStart === activeStart,
          'is-pending': item.status === 'queued' || item.status === 'running'
        }"
        @click="emit('select', item.rangeStart)"
      >
        <span class="review-envelope__flap" aria-hidden="true" />
        <span class="review-envelope__range">{{ formatRangeShort(item.rangeStart, item.rangeEnd) }}</span>
        <span class="review-envelope__meta">{{ statusLabel(item) }}</span>
      </button>

      <button
        v-if="hasMore"
        type="button"
        class="review-history__more"
        :disabled="loading"
        @click="emit('loadMore')"
      >
        {{ loading ? '加载中…' : '更早的 ›' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.review-history {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.review-history__title {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--ink-2);
}

.review-history__strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.review-envelope {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  min-width: 126px;
  padding: 12px 14px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  color: var(--ink-1);
  cursor: pointer;
  transition: border-color var(--t-fast) var(--ease-soft), background var(--t-fast) var(--ease-soft);
}

.review-envelope:hover {
  border-color: var(--line-strong);
  background: var(--bg-soft);
}

.review-envelope.is-active {
  border-color: var(--brand);
  background: var(--brand-soft);
}

.review-envelope__flap {
  width: 24px;
  height: 3px;
  border-radius: 2px;
  background: var(--brand);
  opacity: 0.65;
}

.review-envelope.is-active .review-envelope__flap {
  opacity: 1;
}

.review-envelope__range {
  font-size: var(--fs-sm);
  font-weight: 500;
}

.review-envelope__meta {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.review-history__more {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 12px 16px;
  background: transparent;
  border: 1px dashed var(--line-strong);
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
  color: var(--brand-deep);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft);
}

.review-history__more:hover:not(:disabled) {
  background: var(--bg-soft);
}

.review-history__more:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}
</style>
