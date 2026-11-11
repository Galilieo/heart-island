<script setup>
// 小芽批注：AI 回复按「页边手写」展示（左侧点线 + 文楷），不使用整块底色。
// 危机流程回复（resultKind 为 SUPPORT）不显示卡通表情，改由「心屿」署名、正文用系统黑体；
// 判定逻辑在 utils/mood.js 的 isSupportReply，由调用方算好传 support，本组件不读取记录字段。
import { computed } from 'vue'
import Xiaoya from './Xiaoya.vue'

const props = defineProps({
  text: { type: String, required: true },
  support: Boolean,
  // 列表里截断显示的行数；0 表示不截断。
  clamp: { type: Number, default: 0 }
})

const signature = computed(() => (props.support ? '心屿' : '小芽写道'))

const isClamped = computed(() => props.clamp > 0)

// 用自定义属性传行数，模板里不必按行数拼样式。
const clampStyle = computed(
  () => (isClamped.value ? { '--note-clamp': String(props.clamp) } : null)
)
</script>

<template>
  <aside class="note" :class="{ 'note--support': support }">
    <span class="note__who">
      <Xiaoya v-if="!support" expression="reply" :size="22" />
      {{ signature }}
    </span>
    <p class="note__text" :class="{ 'note__text--clamp': isClamped }" :style="clampStyle">
      {{ text }}
    </p>
  </aside>
</template>

<style scoped>
.note {
  margin-top: 12px;
  padding: 2px 0 2px 14px;
  border-left: 2px dotted var(--brand);
}

.note__who {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--brand-deep);
  font-family: var(--font-display);
  font-size: var(--fs-sm);
  font-weight: 400;
}

.note__text {
  margin: 2px 0 0;
  color: var(--brand-deep);
  font-family: var(--font-display);
  font-size: var(--fs-base);
  font-weight: 400;
  line-height: 1.8;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

/* 危机回复：不出现小芽，正文改用系统黑体，避免卡通化。 */
.note--support .note__text {
  font-family: var(--font-sans);
}

.note__text--clamp {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: var(--note-clamp);
  overflow: hidden;
}
</style>
