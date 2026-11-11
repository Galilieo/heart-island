<script setup>
// 小芽批注（AI 回复）的几种候选样式；当前样式由设计对比页通过 provide('noteStyle') 统一切换。
import { inject, ref } from 'vue'
import Xiaoya from '../components/ui/Xiaoya.vue'

defineProps({
  text: { type: String, required: true }
})

// 单独使用（没有设计对比页提供样式）时默认使用页边手写
const noteStyle = inject('noteStyle', ref('margin'))
</script>

<template>
  <!-- 提示框：此前的样式，保留用于对比 -->
  <aside v-if="noteStyle === 'callout'" class="note note--callout">
    <span class="note__who"><Xiaoya expression="default" :size="20" />小芽</span>
    <p>{{ text }}</p>
  </aside>

  <!-- 页边手写：无底色，点线 + 标题字体，像用笔写在旁边 -->
  <aside v-else-if="noteStyle === 'margin'" class="note note--margin">
    <span class="note__who"><Xiaoya expression="default" :size="22" />小芽写道</span>
    <p>{{ text }}</p>
  </aside>

  <!-- 便签：贴在页面上的小纸条，顶部一条胶带，结尾署名 -->
  <aside v-else-if="noteStyle === 'sticky'" class="note note--sticky">
    <p>{{ text }}</p>
    <span class="note__sign">—— 小芽</span>
  </aside>

  <!-- 气泡：小芽头像在左，像在对话里回应 -->
  <aside v-else-if="noteStyle === 'bubble'" class="note note--bubble">
    <Xiaoya expression="default" :size="32" />
    <div class="note__bubble">
      <span class="note__name">小芽</span>
      <p>{{ text }}</p>
    </div>
  </aside>

  <!-- 回信：虚线分隔后另起一段，结尾署名 -->
  <aside v-else class="note note--letter">
    <span class="note__divider">小芽的回信</span>
    <p>{{ text }}</p>
    <span class="note__sign">—— 小芽</span>
  </aside>
</template>

<style scoped>
.note {
  margin-top: 12px;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  line-height: 1.8;
}

.note__who {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand-deep);
  font-family: var(--font-display);
}

.note__sign {
  display: block;
  margin-top: 4px;
  color: var(--brand-deep);
  font-family: var(--font-display);
  text-align: right;
}

/* 提示框 */
.note--callout {
  padding: 10px 14px;
  border-left: 3px solid var(--brand);
  border-radius: 0 var(--radius-2) var(--radius-2) 0;
  background: var(--brand-soft);
}

/* 页边手写 */
.note--margin {
  padding: 2px 0 2px 14px;
  border-left: 2px dotted var(--brand);
}

.note--margin p {
  margin-top: 2px;
  color: var(--brand-deep);
  font-family: var(--font-display);
  font-size: var(--fs-base);
}

/* 便签 */
.note--sticky {
  position: relative;
  max-width: 92%;
  margin-top: 18px;
  padding: 16px 18px 10px;
  background: var(--joy-soft);
  box-shadow: 0 6px 14px rgba(120, 98, 60, 0.10);
  transform: rotate(-0.6deg);
}

/* 半透明胶带 */
.note--sticky::before {
  content: "";
  position: absolute;
  top: -9px;
  left: 50%;
  width: 72px;
  height: 18px;
  /* 淡米色半透明，白色卡片上也能看出胶带 */
  background: rgba(214, 196, 160, 0.45);
  box-shadow: 0 1px 2px rgba(120, 98, 60, 0.10);
  transform: translateX(-50%) rotate(2deg);
}

/* 气泡 */
.note--bubble {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}

.note__bubble {
  padding: 8px 14px 10px;
  border-radius: 4px 16px 16px 16px;
  background: var(--bg-soft);
}

.note__name {
  color: var(--brand-deep);
  font-size: var(--fs-xs);
  font-weight: 700;
}

/* 回信 */
.note--letter {
  margin-top: 16px;
}

.note__divider {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.note__divider::before,
.note__divider::after {
  content: "";
  flex: 1;
  border-top: 1px dashed var(--line-strong);
}

.note--letter p {
  font-family: var(--font-display);
  font-size: var(--fs-base);
}
</style>
