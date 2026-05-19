<script setup>
defineProps({
  topics: { type: Array, default: () => [] },
  selectedTopicId: { type: [String, Number, null], default: null },
  loading: Boolean
})

defineEmits(['select'])
</script>

<template>
  <div class="topics">
    <button
      type="button"
      class="topic-chip"
      :class="{ 'is-active': selectedTopicId === null || selectedTopicId === '' }"
      @click="$emit('select', null)"
    >全部</button>

    <span v-if="loading && !topics.length" class="topics__loading">话题加载中…</span>

    <button
      v-for="topic in topics"
      :key="topic.id"
      type="button"
      class="topic-chip"
      :class="{ 'is-active': selectedTopicId === topic.id }"
      @click="$emit('select', topic.id)"
    >
      <span class="topic-chip__dot" aria-hidden="true" />
      {{ topic.name }}
    </button>
  </div>
</template>

<style scoped>
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
