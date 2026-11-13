<script setup>
// 后台筛选字段：把 BaseInput / BaseSelect 的标签重排成左侧前缀，控件压到 34px 高。
// 只通过 :deep 覆盖后台范围内的外观，不改 Base* 组件在前台的默认表现；
// 标签仍是 Base* 自己的 label，语义和焦点行为不变。
defineProps({
  // wide 关键词 / md ID / sm 状态；手机上默认各占半行，full 时独占一行
  size: { type: String, default: 'md' },
  full: Boolean
})
</script>

<template>
  <div
    class="admin-filter-field"
    :class="[`admin-filter-field--${size}`, { 'admin-filter-field--full': full }]"
  >
    <slot />
  </div>
</template>

<style scoped>
.admin-filter-field {
  display: flex;
  flex: none;
  align-items: stretch;
  height: 34px;
  border: 1px solid var(--line-strong);
  border-radius: 8px;
  background: var(--bg-card);
  overflow: hidden;
}

.admin-filter-field--wide {
  width: 280px;
}

.admin-filter-field--md {
  width: 170px;
}

.admin-filter-field--sm {
  width: 150px;
}

/* 标签作为左侧前缀：label.field 变成两列网格，标签列压底色、加右分隔线。 */
.admin-filter-field :deep(label.field),
.admin-filter-field :deep(div.field) {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  grid-template-rows: 32px;
  width: 100%;
  height: 32px;
  gap: 0;
}

.admin-filter-field :deep(.field .field__label) {
  display: flex;
  align-items: center;
  grid-area: 1 / 1;
  padding: 0 10px;
  border-right: 1px solid var(--line);
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0;
  white-space: nowrap;
}

.admin-filter-field :deep(.field .field__control) {
  display: flex;
  align-items: center;
  grid-area: 1 / 2;
  height: 32px;
  min-height: 0;
  padding: 0 10px;
  border: none;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.admin-filter-field :deep(.field .field__control:focus-within) {
  border: none;
  box-shadow: none;
}

.admin-filter-field :deep(.field .field__msg) {
  display: none;
}

.admin-filter-field :deep(.field .field__value) {
  font-size: 13px;
}

.admin-filter-field:focus-within {
  border-color: var(--brand);
  box-shadow: var(--shadow-focus);
}

@media (max-width: 768px) {
  .admin-filter-field {
    width: auto;
    min-width: 0;
  }

  .admin-filter-field--full {
    grid-column: 1 / -1;
  }

  .admin-filter-field :deep(.field .field__label) {
    padding: 0 8px;
  }
}
</style>
