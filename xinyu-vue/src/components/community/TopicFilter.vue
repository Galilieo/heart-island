<script setup>
// 话题胶囊筛选器。第一个"全部"对应 topicId=null，
// 点击发 @select(topicId)，自身不知道当前的列表，纯展示。
// 窄屏单行横滑时通过 is-scrollable / is-at-end 暴露滚动状态，
// 由外层（PostList）决定右缘渐隐/peek 提示，滚到底提示消失。
import { ref, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  topics: { type: Array, default: () => [] },
  selectedTopicId: { type: [String, Number, null], default: null },
  loading: Boolean
})

defineEmits(['select'])

const listRef = ref(null)
const canScroll = ref(false)
const atEnd = ref(true)

function updateScrollState() {
  const el = listRef.value
  if (!el) return
  canScroll.value = el.scrollWidth > el.clientWidth + 1
  atEnd.value = el.scrollLeft + el.clientWidth >= el.scrollWidth - 4
}

// 话题异步到达后宽度才确定，需要重新测量
watch(() => props.topics.length, () => nextTick(updateScrollState))

onMounted(() => {
  updateScrollState()
  window.addEventListener('resize', updateScrollState)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateScrollState)
})
</script>

<template>
  <div
    class="topics-wrap"
    :class="{ 'is-scrollable': canScroll, 'is-at-end': atEnd }"
  >
    <div ref="listRef" class="topics" @scroll.passive="updateScrollState">
      <button
        type="button"
        class="topic-chip motion-press"
        :class="{ 'is-active': selectedTopicId === null || selectedTopicId === '' }"
        @click="$emit('select', null)"
      >全部</button>

      <span v-if="loading && !topics.length" class="topics__loading">话题加载中…</span>

      <button
        v-for="topic in topics"
        :key="topic.id"
        type="button"
        class="topic-chip motion-press"
        :class="{ 'is-active': selectedTopicId === topic.id }"
        @click="$emit('select', topic.id)"
      >
        <span class="topic-chip__dot" aria-hidden="true" />
        {{ topic.name }}
      </button>
    </div>
  </div>
</template>

<style scoped>
.topics-wrap {
  position: relative;
  min-width: 0;
}

.topics {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.topics__loading {
  color: var(--ink-3);
  font-size: var(--fs-sm);
  padding: 8px;
}

.topic-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: var(--radius-pill);
  background: var(--bg-card);
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft),
    border-color var(--t-fast) var(--ease-soft),
    box-shadow var(--t-fast) var(--ease-soft),
    transform var(--t-fast) var(--ease-bounce);
}

.topic-chip:hover {
  border-color: var(--brand-soft);
  background: var(--brand-soft);
  color: var(--brand-deep);
}

.topic-chip.is-active {
  border-color: transparent;
  background: var(--brand);
  color: #fff;
  box-shadow: 0 6px 14px rgba(111, 169, 164, 0.28);
}

.topic-chip__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
}
</style>
