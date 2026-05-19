<script setup>
import Doodle from '../ui/Doodle.vue'
</script>

<template>
  <footer class="app-footer">
    <div class="container app-footer__inner">
      <Doodle name="leaf" :size="20" color="var(--ink-3)" />
      <span>心屿 · 把今天的情绪放慢一点</span>
      <Doodle name="leaf" :size="20" color="var(--ink-3)" style="transform: scaleX(-1)" />
    </div>
  </footer>
</template>

<style scoped>
.app-footer {
  margin-top: 64px;
  padding: 24px 0 32px;
}

.app-footer__inner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}
</style>
