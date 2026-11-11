<script setup>
// 话题筛选器。第一个"全部"对应 topicId=null，
// 点击发 @select(topicId)，自身不知道当前的列表，纯展示。
// 所有宽度下都自动换行展示——话题永远全部可见、可点，
// 不做横向滚动隐藏（鼠标用户滚不了横排，等于选不到）。
// 胶囊样式与首页 MoodFilterBar 的范围按钮一致（无边框、选中品牌柔底）；
// vertical 供社区侧栏竖排整行使用，仅在 ≥1100px（社区主栏 + 侧栏网格的
// 断点，与 Home.vue 保持一致）生效，窄屏自动回到横排换行。
defineProps({
  topics: { type: Array, default: () => [] },
  selectedTopicId: { type: [String, Number, null], default: null },
  loading: Boolean,
  vertical: Boolean
})

defineEmits(['select'])
</script>

<template>
  <div class="topics-wrap">
    <div class="topics" :class="{ 'topics--vertical': vertical }">
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
      >{{ topic.name }}</button>
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
  gap: 4px;
}

.topics__loading {
  color: var(--ink-3);
  font-size: var(--fs-sm);
  padding: 8px;
}

.topic-chip {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.topic-chip:not(.is-active):hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.topic-chip:focus-visible {
  outline: none;
  box-shadow: var(--shadow-focus);
}

.topic-chip.is-active {
  background: var(--brand-soft);
  color: var(--brand-deep);
  font-weight: 700;
}

/* 侧栏竖排：整行左对齐、可点高度加大（仅在社区宽屏网格下生效） */
@media (min-width: 1100px) {
  .topics--vertical {
    flex-direction: column;
    flex-wrap: nowrap;
    align-items: stretch;
    gap: 2px;
  }

  .topics--vertical .topic-chip {
    justify-content: flex-start;
    min-height: 40px;
    padding: 0 12px;
    text-align: left;
  }
}

/* 窄屏触控目标补足到 44px，话题保持全部可见可点 */
@media (max-width: 768px) {
  .topic-chip {
    min-height: 44px;
  }
}
</style>
