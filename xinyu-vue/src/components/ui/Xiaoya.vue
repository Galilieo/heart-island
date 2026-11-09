<script setup>
// 小芽仅作装饰，语义由旁边文字提供；减少动态效果时直接使用静态素材。
import { computed } from 'vue'

const props = defineProps({
  expression: { type: String, default: 'default' },
  animated: Boolean,
  size: { type: Number, default: 48 }
})

const stills = import.meta.glob('../../assets/xiaoya/moods/*.svg', {
  eager: true, query: '?url', import: 'default'
})
const animations = import.meta.glob('../../assets/xiaoya/animated/*.svg', {
  eager: true, query: '?url', import: 'default'
})
const still = computed(() => stills[`../../assets/xiaoya/moods/xiaoya-${props.expression}.svg`]
  || stills['../../assets/xiaoya/moods/xiaoya-default.svg'])
const source = computed(() => props.animated
  ? animations[`../../assets/xiaoya/animated/xiaoya-${props.expression}.svg`] || still.value
  : still.value)
</script>

<template>
  <picture class="xiaoya" aria-hidden="true" :style="{ width: `${size}px`, height: `${size}px` }">
    <source media="(prefers-reduced-motion: reduce)" :srcset="still">
    <img :src="source" alt="" :width="size" :height="size" decoding="async">
  </picture>
</template>

<style scoped>
.xiaoya { display: inline-flex; flex-shrink: 0; vertical-align: middle; }
.xiaoya img { display: block; width: 100%; height: 100%; object-fit: contain; }
</style>
