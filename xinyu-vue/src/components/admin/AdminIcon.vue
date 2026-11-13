<script setup>
// 后台导航图标：内联 SVG 线性图标，描边 1.6、颜色跟随 currentColor。
// 不引入图标库，避免为 7 个图标增加依赖；路径取自后台设计预览。
defineProps({
  name: { type: String, required: true }
})

const iconPaths = {
  dashboard: ['M3 13h8V3H3z', 'M13 21h8V11h-8z', 'M3 21h8v-6H3z', 'M13 3v6h8V3z'],
  users: ['M4 21c0-4 4-6 8-6s8 2 8 6'],
  posts: ['M4 4h16v12H8l-4 4z'],
  replies: ['M9 14l-5-5 5-5', 'M4 9h10a6 6 0 0 1 6 6v5'],
  topics: ['M4 9h16M4 15h16M10 3L8 21M16 3l-2 18'],
  ai: ['M12 3v3M12 18v3M3 12h3M18 12h3'],
  back: ['M15 18l-6-6 6-6']
}

const iconCircles = {
  users: [{ cx: 12, cy: 8, r: 4 }],
  ai: [{ cx: 12, cy: 12, r: 5 }]
}
</script>

<template>
  <svg
    class="admin-icon"
    viewBox="0 0 24 24"
    width="16"
    height="16"
    fill="none"
    stroke="currentColor"
    stroke-width="1.6"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="d in iconPaths[name] || []" :key="d" :d="d" />
    <circle
      v-for="circle in iconCircles[name] || []"
      :key="`${circle.cx}-${circle.cy}-${circle.r}`"
      :cx="circle.cx"
      :cy="circle.cy"
      :r="circle.r"
    />
  </svg>
</template>

<style scoped>
.admin-icon {
  width: 16px;
  height: 16px;
  flex: none;
}
</style>
