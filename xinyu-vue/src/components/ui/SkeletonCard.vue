<script setup>
defineProps({
  rows: { type: [Number, String], default: 3 },
  avatar: Boolean
})
</script>

<template>
  <div class="skeleton">
    <div v-if="avatar" class="skeleton__avatar shimmer" />
    <div class="skeleton__lines">
      <div class="skeleton__line shimmer skeleton__line--title" />
      <div
        v-for="i in Number(rows)"
        :key="i"
        class="skeleton__line shimmer"
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

.shimmer {
  background: linear-gradient(
    90deg,
    var(--bg-soft) 0%,
    #f0e8d6 50%,
    var(--bg-soft) 100%
  );
  background-size: 200% 100%;
  animation: shimmer 1.4s linear infinite;
}

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
