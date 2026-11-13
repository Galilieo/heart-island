<script setup>
// 心情次数横向条：按次数降序展示；并列时顺序已由后端固定，前端不再挑“最多”。
// 默认只展开前 4 种，其余收起，用户可展开查看。
import { computed, ref } from 'vue'
import { moodTone } from '../../utils/mood'

const props = defineProps({
  items: { type: Array, default: () => [] },
  limit: { type: Number, default: 4 }
})

const expanded = ref(false)

const visibleItems = computed(() =>
  expanded.value ? props.items : props.items.slice(0, props.limit)
)

const remaining = computed(() => Math.max(0, props.items.length - props.limit))

const maxCount = computed(() => props.items[0]?.count || 0)

function barWidth(count) {
  if (!maxCount.value) return '0%'
  return `${(count / maxCount.value) * 100}%`
}

function toggle() {
  expanded.value = !expanded.value
}
</script>

<template>
  <div class="mood-bars">
    <div
      v-for="item in visibleItems"
      :key="item.moodType"
      class="mood-bars__row"
      :class="`tone--${moodTone(item.moodType)}`"
    >
      <span class="mood-bars__label">{{ item.moodType }}</span>
      <span class="mood-bars__track" aria-hidden="true">
        <i :style="{ width: barWidth(item.count) }" />
      </span>
      <span class="mood-bars__count">{{ item.count }}</span>
    </div>

    <button
      v-if="remaining > 0"
      type="button"
      class="mood-bars__more"
      :aria-expanded="expanded"
      @click="toggle"
    >
      {{ expanded ? '收起 ▴' : `还有 ${remaining} 种 ▾` }}
    </button>
  </div>
</template>

<style scoped>
.mood-bars {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mood-bars__row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) 22px;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.mood-bars__label {
  color: var(--ink-1);
  white-space: nowrap;
}

.mood-bars__track {
  height: 10px;
  overflow: hidden;
  border-radius: 6px;
  background: var(--bg-soft);
}

.mood-bars__track i {
  display: block;
  height: 100%;
  border-radius: 6px;
  background: var(--tone);
}

.mood-bars__count {
  color: var(--ink-2);
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.mood-bars__more {
  align-self: flex-start;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-family: inherit;
  font-size: 12px;
  font-weight: 600;
}

.mood-bars__more:hover {
  color: var(--brand-deep);
}

.mood-bars__more:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}

.tone--joy { --tone: var(--joy); }
.tone--calm { --tone: var(--calm); }
.tone--tension { --tone: var(--tension); }
.tone--sad { --tone: var(--sad); }
.tone--tired { --tone: var(--tired); }
.tone--neutral { --tone: var(--brand); }
</style>
