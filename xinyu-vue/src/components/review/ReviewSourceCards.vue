<script setup>
// 回信引用来源总览卡片：按正文引用顺序展示记录全文，1000 字长内容默认折叠三行并可展开。
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import BaseTag from '../ui/BaseTag.vue'
import { formatShortDate } from '../../utils/review'
import { moodTone } from '../../utils/mood'

const props = defineProps({
  sources: {
    type: Array,
    default: () => []
  }
})

const expandedIds = ref(new Set())
const overflowIds = ref(new Set())
const contentRefs = new Map()

let resizeObserver = null

function setContentRef(recordId, el) {
  const key = String(recordId)
  if (el) {
    contentRefs.set(key, el)
    return
  }
  contentRefs.delete(key)
}

function isExpanded(recordId) {
  return expandedIds.value.has(String(recordId))
}

function canExpand(recordId) {
  return overflowIds.value.has(String(recordId)) || isExpanded(recordId)
}

function toggleExpand(recordId) {
  const key = String(recordId)
  const next = new Set(expandedIds.value)
  if (next.has(key)) {
    next.delete(key)
  } else {
    next.add(key)
  }
  expandedIds.value = next
}

function measureOverflows() {
  const next = new Set(overflowIds.value)
  for (const [key, el] of contentRefs.entries()) {
    if (!isExpanded(key)) {
      if (el.scrollHeight > el.clientHeight + 1) {
        next.add(key)
      } else {
        next.delete(key)
      }
    }
  }
  overflowIds.value = next
}

function syncObservers() {
  measureOverflows()
  if (!resizeObserver) {
    return
  }
  resizeObserver.disconnect()
  for (const el of contentRefs.values()) {
    resizeObserver.observe(el)
  }
}

watch(
  () => props.sources,
  async () => {
    expandedIds.value = new Set()
    overflowIds.value = new Set()
    await nextTick()
    syncObservers()
  },
  { deep: false }
)

onMounted(async () => {
  await nextTick()
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      measureOverflows()
    })
  }
  syncObservers()
})

onBeforeUnmount(() => {
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>

<template>
  <section v-if="sources.length" class="review-sources" aria-label="信里提到的那几条记录">
    <h4 class="review-sources__title">信里提到的那几条记录</h4>

    <div class="review-sources__grid">
      <article
        v-for="source in sources"
        :key="source.recordId"
        class="review-source-card"
        :data-source-id="String(source.recordId)"
      >
        <header class="review-source-card__head">
          <span class="review-source-card__date">
            {{ formatShortDate(source.date) }} {{ source.weekday }}
          </span>
          <BaseTag :tone="moodTone(source.moodType)" variant="soft" size="sm">
            {{ source.moodType }}
          </BaseTag>
        </header>

        <p
          :ref="(el) => setContentRef(source.recordId, el)"
          class="review-source-card__content"
          :class="{ 'is-clamped': !isExpanded(source.recordId) }"
        >
          {{ source.content }}
        </p>

        <button
          v-if="canExpand(source.recordId)"
          type="button"
          class="review-source-card__toggle"
          @click="toggleExpand(source.recordId)"
        >
          {{ isExpanded(source.recordId) ? '收起 ▴' : '展开 ▾' }}
        </button>
      </article>
    </div>
  </section>
</template>

<style scoped>
.review-sources {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.review-sources__title {
  margin: 0;
  font-size: var(--fs-sm);
  font-weight: 500;
  color: var(--ink-2);
}

.review-sources__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.review-source-card {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  padding: 14px 16px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-2);
  box-shadow: var(--shadow-sm);
}

.review-source-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.review-source-card__date {
  font-size: var(--fs-xs);
  color: var(--ink-3);
}

.review-source-card__content {
  margin: 0;
  font-size: var(--fs-sm);
  line-height: 1.65;
  color: var(--ink-1);
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.review-source-card__content.is-clamped {
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.review-source-card__toggle {
  align-self: flex-start;
  padding: 0;
  border: 0;
  background: transparent;
  font-size: var(--fs-xs);
  color: var(--brand-deep);
  cursor: pointer;
}

.review-source-card__toggle:hover {
  text-decoration: underline;
}

@media (max-width: 900px) {
  .review-sources__grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .review-sources__grid {
    grid-template-columns: 1fr;
  }
}
</style>
