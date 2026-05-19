<script setup>
// 通用卡片容器。padding 三档 + 4 种 tone（白/奶油/品牌色/强调色），
// hoverable 时鼠标悬停会浮起；tilt 可以让卡片轻微旋转出"手绘感"。
defineProps({
  padding: {
    type: String,
    default: 'md',
    validator: (v) => ['none', 'sm', 'md', 'lg'].includes(v)
  },
  tone: {
    type: String,
    default: 'plain',
    validator: (v) => ['plain', 'cream', 'brand', 'accent'].includes(v)
  },
  tilt: {
    type: [Number, String],
    default: 0
  },
  hoverable: Boolean,
  flat: Boolean
})
</script>

<template>
  <section
    class="card"
    :class="[
      `card--p-${padding}`,
      `card--${tone}`,
      { 'card--hoverable': hoverable, 'card--flat': flat }
    ]"
    :style="tilt ? { transform: `rotate(${tilt}deg)` } : null"
  >
    <header v-if="$slots.header" class="card__header"><slot name="header" /></header>
    <div class="card__body"><slot /></div>
    <footer v-if="$slots.footer" class="card__footer"><slot name="footer" /></footer>
  </section>
</template>

<style scoped>
.card {
  position: relative;
  border-radius: var(--radius-3);
  background: var(--bg-card);
  border: 1px solid var(--line);
  box-shadow: var(--shadow-sm);
  transition: transform var(--t-base) var(--ease-soft),
    box-shadow var(--t-base) var(--ease-soft);
}

.card--cream  { background: var(--bg-soft); }
.card--brand  { background: var(--brand-soft); border-color: #d6e8e5; }
.card--accent { background: var(--accent-soft); border-color: #f1cdc2; }

.card--flat { box-shadow: none; }

.card--hoverable {
  cursor: pointer;
}
.card--hoverable:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.card--p-none .card__body,
.card--p-none .card__header,
.card--p-none .card__footer {
  padding: 0;
}
.card--p-sm .card__body { padding: 16px; }
.card--p-md .card__body { padding: 24px; }
.card--p-lg .card__body { padding: 32px; }

.card--p-sm .card__header,
.card--p-sm .card__footer {
  padding: 12px 16px;
}
.card--p-md .card__header,
.card--p-md .card__footer {
  padding: 16px 24px;
}
.card--p-lg .card__header,
.card--p-lg .card__footer {
  padding: 20px 32px;
}

.card__header {
  border-bottom: 1px dashed var(--line-strong);
  font-weight: 600;
  color: var(--ink-1);
}

.card__footer {
  border-top: 1px dashed var(--line-strong);
  color: var(--ink-2);
}
</style>
