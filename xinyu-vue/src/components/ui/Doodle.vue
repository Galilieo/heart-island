<script setup>
// 手绘风 SVG 小图。8 种图案：cloud/star/leaf/wave/cup/plant/sun/heart。
// 用 currentColor 描边，可被父级的 color 样式直接控制颜色。
defineProps({
  name: {
    type: String,
    default: 'cloud',
    validator: (v) =>
      ['cloud', 'star', 'leaf', 'wave', 'cup', 'plant', 'sun', 'heart'].includes(v)
  },
  size: { type: [Number, String], default: 48 },
  color: { type: String, default: 'currentColor' },
  strokeWidth: { type: [Number, String], default: 2 }
})
</script>

<template>
  <svg
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    fill="none"
    :stroke="color"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    class="doodle"
  >
    <!-- 云 -->
    <g v-if="name === 'cloud'">
      <path d="M16 40c-5 0-8-3-8-7s3-7 8-7c1-5 5-9 11-9 7 0 12 5 12 11h2c5 0 9 4 9 9s-4 9-9 9H16z" />
      <path d="M24 48c0 1.5 1 3 2.5 3M36 50c0 1.5 1 3 2.5 3" />
    </g>
    <!-- 星 -->
    <g v-else-if="name === 'star'">
      <path d="M32 10l5 12 13 1.5-10 9 3 13L32 38l-11 7.5 3-13-10-9 13-1.5z" />
      <circle cx="50" cy="14" r="1.5" />
      <circle cx="14" cy="50" r="1.5" />
    </g>
    <!-- 叶 -->
    <g v-else-if="name === 'leaf'">
      <path d="M14 50C14 28 28 14 50 14c0 22-14 36-36 36z" />
      <path d="M18 46c8-8 18-18 28-28" />
    </g>
    <!-- 波浪 -->
    <g v-else-if="name === 'wave'">
      <path d="M6 36c4-6 8-6 12 0s8 6 12 0 8-6 12 0 8 6 12 0" />
      <path d="M6 46c4-6 8-6 12 0s8 6 12 0 8-6 12 0 8 6 12 0" opacity="0.6" />
    </g>
    <!-- 杯子 -->
    <g v-else-if="name === 'cup'">
      <path d="M16 22h28v22c0 6-4 10-10 10h-8c-6 0-10-4-10-10z" />
      <path d="M44 28h6c3 0 6 3 6 6s-3 6-6 6h-6" />
      <path d="M24 10c0 3 2 3 2 6M32 10c0 3 2 3 2 6M40 10c0 3 2 3 2 6" />
    </g>
    <!-- 植物 -->
    <g v-else-if="name === 'plant'">
      <path d="M22 50h20l-3 8H25z" />
      <path d="M32 50V28" />
      <path d="M32 36c-6 0-12-6-10-14 8-2 14 4 14 14" />
      <path d="M32 28c6 0 12-4 12-12-8 0-14 4-14 12" />
    </g>
    <!-- 太阳 -->
    <g v-else-if="name === 'sun'">
      <circle cx="32" cy="32" r="10" />
      <path d="M32 10v6M32 48v6M10 32h6M48 32h6M16 16l4 4M44 44l4 4M48 16l-4 4M16 48l4-4" />
    </g>
    <!-- 心 -->
    <g v-else-if="name === 'heart'">
      <path d="M32 52S12 40 12 25c0-7 6-13 13-13 4 0 7 2 7 6 0-4 3-6 7-6 7 0 13 6 13 13 0 15-20 27-20 27z" />
    </g>
  </svg>
</template>

<style scoped>
.doodle {
  display: inline-block;
  vertical-align: middle;
}
</style>
