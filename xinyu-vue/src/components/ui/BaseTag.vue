<script setup>
// 标签。tone 决定配色（含 5 种心情色 joy/calm/sad/angry/tired），
// variant 决定填充方式（soft 柔底 / solid 实色 / outline 描边）。
defineProps({
  tone: {
    type: String,
    default: 'neutral',
    validator: (v) =>
      [
        'neutral',
        'brand',
        'accent',
        'warn',
        'danger',
        'success',
        'joy',
        'calm',
        'sad',
        'angry',
        'tired'
      ].includes(v)
  },
  size: {
    type: String,
    default: 'md',
    validator: (v) => ['sm', 'md', 'lg'].includes(v)
  },
  variant: {
    type: String,
    default: 'soft',
    validator: (v) => ['soft', 'solid', 'outline'].includes(v)
  }
})
</script>

<template>
  <span
    class="tag"
    :class="[`tag--${tone}`, `tag--${size}`, `tag--${variant}`]"
  >
    <slot />
  </span>
</template>

<style scoped>
.tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border-radius: var(--radius-pill);
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  border: 1px solid transparent;
}

.tag--sm { padding: 3px 9px; font-size: 11px; }
.tag--md { padding: 5px 12px; font-size: var(--fs-xs); }
.tag--lg { padding: 6px 14px; font-size: var(--fs-sm); }

/* tone color tables */
.tag--neutral.tag--soft { background: var(--bg-soft); color: var(--ink-2); }
.tag--brand.tag--soft   { background: var(--brand-soft); color: var(--brand-deep); }
.tag--accent.tag--soft  { background: var(--accent-soft); color: #a45a4a; }
.tag--warn.tag--soft    { background: var(--warn-soft); color: #946200; }
.tag--danger.tag--soft  { background: var(--danger-soft); color: #a83a3a; }
.tag--success.tag--soft { background: var(--success-soft); color: #2f6a45; }
.tag--joy.tag--soft     { background: var(--joy-soft); color: #a06b1e; }
.tag--calm.tag--soft    { background: var(--calm-soft); color: #3f6f6a; }
.tag--sad.tag--soft     { background: var(--sad-soft); color: #45597a; }
.tag--angry.tag--soft   { background: var(--angry-soft); color: #8b4a3d; }
.tag--tired.tag--soft   { background: var(--tired-soft); color: #6e6557; }

/* solid */
.tag--solid { color: #fff; }
.tag--brand.tag--solid   { background: var(--brand); }
.tag--accent.tag--solid  { background: var(--accent); }
.tag--warn.tag--solid    { background: var(--warn); }
.tag--danger.tag--solid  { background: var(--danger); }
.tag--success.tag--solid { background: var(--success); }
.tag--joy.tag--solid     { background: var(--joy); }
.tag--calm.tag--solid    { background: var(--calm); }
.tag--sad.tag--solid     { background: var(--sad); }
.tag--angry.tag--solid   { background: var(--angry); }
.tag--tired.tag--solid   { background: var(--tired); }
.tag--neutral.tag--solid { background: var(--ink-2); }

/* outline */
.tag--outline { background: transparent; }
.tag--neutral.tag--outline { color: var(--ink-2); border-color: var(--line-strong); }
.tag--brand.tag--outline   { color: var(--brand-deep); border-color: var(--brand-soft); }
.tag--accent.tag--outline  { color: var(--accent); border-color: var(--accent-soft); }
.tag--joy.tag--outline     { color: #a06b1e; border-color: var(--joy-soft); }
.tag--calm.tag--outline    { color: #3f6f6a; border-color: var(--calm-soft); }
.tag--sad.tag--outline     { color: #45597a; border-color: var(--sad-soft); }
.tag--angry.tag--outline   { color: #8b4a3d; border-color: var(--angry-soft); }
.tag--tired.tag--outline   { color: #6e6557; border-color: var(--tired-soft); }
</style>
