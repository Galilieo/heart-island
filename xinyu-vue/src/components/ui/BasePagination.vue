<script setup>
import { computed } from 'vue'

const props = defineProps({
  pageNum: { type: Number, required: true },
  pages: { type: Number, required: true },
  total: { type: Number, default: 0 },
  pageSize: { type: Number, default: 0 },
  showTotal: { type: Boolean, default: true }
})

const emit = defineEmits(['update:pageNum', 'change'])

const visiblePages = computed(() => {
  const out = []
  const total = props.pages
  if (total <= 7) {
    for (let i = 1; i <= total; i++) out.push(i)
    return out
  }
  const cur = props.pageNum
  out.push(1)
  if (cur > 4) out.push('…')
  const start = Math.max(2, cur - 1)
  const end = Math.min(total - 1, cur + 1)
  for (let i = start; i <= end; i++) out.push(i)
  if (cur < total - 3) out.push('…')
  out.push(total)
  return out
})

function goto(p) {
  if (typeof p !== 'number') return
  if (p < 1 || p > props.pages || p === props.pageNum) return
  emit('update:pageNum', p)
  emit('change', p)
}
</script>

<template>
  <div v-if="pages > 1 || showTotal" class="pgn">
    <span v-if="showTotal && total" class="pgn__total">共 {{ total }} 条</span>
    <button
      class="pgn__btn"
      :disabled="pageNum <= 1"
      type="button"
      @click="goto(pageNum - 1)"
    >‹</button>

    <button
      v-for="(p, idx) in visiblePages"
      :key="`${p}-${idx}`"
      class="pgn__btn"
      :class="{ 'is-active': p === pageNum, 'is-gap': p === '…' }"
      :disabled="p === '…'"
      type="button"
      @click="goto(p)"
    >{{ p }}</button>

    <button
      class="pgn__btn"
      :disabled="pageNum >= pages"
      type="button"
      @click="goto(pageNum + 1)"
    >›</button>
  </div>
</template>

<style scoped>
.pgn {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.pgn__total {
  margin-right: 8px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.pgn__btn {
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border: 1px solid var(--line);
  background: var(--bg-card);
  color: var(--ink-2);
  border-radius: var(--radius-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft),
    border-color var(--t-fast) var(--ease-soft);
}

.pgn__btn:not(:disabled):hover {
  background: var(--brand-soft);
  color: var(--brand-deep);
  border-color: var(--brand-soft);
}

.pgn__btn.is-active {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}

.pgn__btn:disabled {
  cursor: not-allowed;
  color: var(--ink-3);
  opacity: 0.6;
}

.pgn__btn.is-gap {
  border-color: transparent;
  background: transparent;
  cursor: default;
}
</style>
