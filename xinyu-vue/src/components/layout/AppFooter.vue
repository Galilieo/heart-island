<script setup>
// 页脚：一行 slogan 加两片叶子涂鸦。
import Doodle from '../ui/Doodle.vue'
</script>

<template>
  <footer class="app-footer">
    <div class="container app-footer__inner">
      <Doodle name="leaf" :size="20" color="var(--ink-3)" />
      <span>心屿 · 记心情，聊日常</span>
      <Doodle name="leaf" :size="20" color="var(--ink-3)" style="transform: scaleX(-1)" />
    </div>
    <div class="container app-footer__beian">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">鄂ICP备2026025921号-1</a>
      <span aria-hidden="true">·</span>
      <a href="https://beian.mps.gov.cn/#/query/webSearch?code=42028102000306" target="_blank" rel="noopener noreferrer"><img src="/beian-ghs.png" alt="" class="beian-badge" />鄂公网安备42028102000306号</a>
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

.app-footer__beian {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 4px 10px;
  margin-top: 10px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.app-footer__beian a {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  white-space: nowrap;
  color: inherit;
  text-decoration: none;
}

.app-footer__beian a:hover {
  text-decoration: underline;
}

.beian-badge {
  width: 13px;
  height: 13px;
  flex-shrink: 0;
}
</style>
