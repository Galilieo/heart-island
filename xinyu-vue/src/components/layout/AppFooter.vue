<script setup>
// 页脚：一行 slogan 加隐私政策链接。
</script>

<template>
  <footer class="app-footer">
    <div class="container app-footer__inner">
      <span>心屿 · 记心情，聊日常</span>
      <RouterLink to="/privacy">隐私政策</RouterLink>
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
