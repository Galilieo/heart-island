<script setup>
// Logo 对比：每个字标版本在日间、深夜两种底色上，各展示大尺寸、顶栏尺寸和小尺寸。
import { LOGO_OPTIONS } from './styleOptions'

// 字标高度（px）：大图用于登录 / 介绍页，32px 对应顶栏，22px 对应手机顶栏与页脚
const SIZES = [
  { label: '大尺寸', height: 96 },
  { label: '顶栏', height: 32 },
  { label: '小尺寸', height: 22 }
]

// 两种底色固定写死，不随预览主题切换，方便同时比较
const BACKGROUNDS = [
  { id: 'light', label: '日间底色', color: '#fcfaf4' },
  { id: 'dark', label: '深夜底色', color: '#122a30' }
]
</script>

<template>
  <div class="lc">
    <section v-for="logo in LOGO_OPTIONS" :key="logo.id" class="lc-row">
      <header class="lc-row__head">
        <h2>{{ logo.name }}</h2>
        <p>{{ logo.trait }}</p>
      </header>

      <div class="lc-boards">
        <div
          v-for="background in BACKGROUNDS"
          :key="background.id"
          class="lc-board"
          :style="{ background: background.color }"
        >
          <span class="lc-board__label" :class="`lc-board__label--${background.id}`">{{ background.label }}</span>
          <div v-for="size in SIZES" :key="size.label" class="lc-sample">
            <img
              :src="background.id === 'dark' ? logo.dark : logo.light"
              :alt="`心屿字标（${logo.name}）`"
              :style="{ height: `${size.height}px` }"
            >
            <span class="lc-sample__size" :class="`lc-board__label--${background.id}`">{{ size.label }} {{ size.height }}px</span>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.lc {
  display: grid;
  gap: 20px;
  padding: 20px;
}

.lc-row__head h2 {
  font-size: var(--fs-lg);
  font-weight: 400;
}

.lc-row__head p {
  margin-top: 2px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
}

.lc-boards {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 10px;
}

.lc-board {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 16px 18px;
  border: 1px solid var(--line);
  border-radius: var(--radius-3);
}

.lc-board__label {
  font-size: var(--fs-xs);
}

.lc-board__label--light { color: #7d7668; }

.lc-board__label--dark { color: #879b94; }

.lc-sample {
  display: flex;
  align-items: center;
  gap: 14px;
}

.lc-sample img {
  display: block;
  width: auto;
}

@container (max-width: 760px) {
  .lc { padding: 12px; }
  .lc-boards { grid-template-columns: 1fr; }
}
</style>
