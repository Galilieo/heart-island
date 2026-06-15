<script setup>
// 加载占位卡片。列表请求中显示几个占位，避免空白闪烁。
import { onMounted, onUnmounted, shallowRef } from 'vue'

const props = defineProps({
  rows: { type: [Number, String], default: 3 },
  avatar: Boolean,
  delay: { type: [Number, String], default: 180 }
})

const visible = shallowRef(false)
let timer = null

onMounted(() => {
  timer = window.setTimeout(() => {
    visible.value = true
    timer = null
  }, Number(props.delay))
})

onUnmounted(() => {
  if (timer) window.clearTimeout(timer)
})
</script>

<template>
  <div class="skeleton" :class="{ 'is-visible': visible }" aria-hidden="true">
    <div v-if="avatar" class="skeleton__avatar motion-shimmer" />
    <div class="skeleton__lines">
      <div class="skeleton__line motion-shimmer skeleton__line--title" />
      <div
        v-for="i in Number(rows)"
        :key="i"
        class="skeleton__line motion-shimmer"
        :style="{ width: `${85 - i * 6}%` }"
      />
    </div>
  </div>
</template>

<style scoped>
.skeleton {
  display: flex;
  gap: 16px;
  padding: 20px;
  background: var(--bg-card);
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
  opacity: 0;
  transform: translateY(var(--motion-distance-xs));
  transition:
    opacity var(--t-base) var(--ease-soft),
    transform var(--t-base) var(--ease-soft);
}

.skeleton.is-visible {
  opacity: 1;
  transform: translateY(0);
}

.skeleton:not(.is-visible) .motion-shimmer {
  animation-play-state: paused;
}

.skeleton__avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  flex-shrink: 0;
}

.skeleton__lines {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.skeleton__line {
  height: 12px;
  border-radius: var(--radius-pill);
  width: 100%;
}

.skeleton__line--title {
  width: 40%;
  height: 16px;
}

</style>
