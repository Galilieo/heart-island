<script setup>
// 后台列表表格：5 个列表页共用。电脑上是紧凑表格（表头吸顶、操作列固定在右侧、
// 内容列自动伸缩并以省略号截断）；≤768px 时同一份列配置渲染成卡片列表。
//
// 列配置字段：
//   key          字段名，同时用于 cell-<key> 插槽名
//   title        表头文字
//   width        固定列宽（px）；不写表示内容列，自动伸缩
//   align        'right' 时数字靠右并使用等宽数字
//   mono         ID / 时间列用 --ink-2
//   mobile       'eyebrow' 顶部行 / 'body' 两行正文 / 'meta' 数字时间行 /
//                'status' 右上角 / 'hidden' 手机不显示
//   mobileLabel  手机 meta 行的前缀文字，空字符串表示只显示值
//   mobilePrefix 手机 eyebrow 行的前缀（如 ID 的 #）
//   format       (row) => 单元格文字；不写时取 row[key]
//   tooltip      (row) => 完整文字，放在 title 属性里（内容被截断时仍可查看）
import { computed } from 'vue'

const props = defineProps({
  columns: { type: Array, required: true },
  rows: { type: Array, default: () => [] },
  rowKey: { type: String, default: 'id' },
  total: { type: Number, default: 0 },
  loading: Boolean,
  orderHint: { type: String, default: '' }
})

const fixedWidth = computed(() =>
  props.columns.reduce((sum, column) => sum + (column.width || 0), 0)
)

const flexibleCount = computed(
  () => props.columns.filter((column) => !column.width).length
)

// 固定列 + 内容列的最小宽度：宽屏（≥1280）放得下，窄屏才出现横向滚动。
const minWidth = computed(() => fixedWidth.value + flexibleCount.value * 160)

const eyebrowColumns = computed(() =>
  props.columns.filter((column) => column.mobile === 'eyebrow')
)

const bodyColumn = computed(() =>
  props.columns.find((column) => column.mobile === 'body')
)

const metaColumns = computed(() =>
  props.columns.filter((column) => column.mobile === 'meta')
)

const statusColumn = computed(() =>
  props.columns.find((column) => column.mobile === 'status')
)

const showLoading = computed(() => props.loading && !props.rows.length)

function cellText(column, row) {
  if (column.format) return column.format(row)

  const value = row[column.key]
  return value === undefined || value === null || value === '' ? '-' : value
}

function cellTooltip(column, row) {
  return column.tooltip ? column.tooltip(row) : undefined
}

function eyebrowText(column, row) {
  const text = cellText(column, row)
  return column.mobilePrefix ? `${column.mobilePrefix}${text}` : text
}

function metaText(column, row) {
  const text = cellText(column, row)

  if (!column.mobileLabel) return text

  return `${column.mobileLabel} ${text}`
}

function cellClass(column) {
  return {
    'is-num': column.align === 'right',
    'is-mono': column.mono,
    'is-sticky': column.key === 'actions'
  }
}
</script>

<template>
  <section class="admin-table">
    <div class="admin-table__toolbar">
      <span>共 <b>{{ total }}</b> 条</span>
      <span v-if="orderHint" class="admin-table__hint">{{ orderHint }}</span>
    </div>

    <div v-if="showLoading" class="admin-table__state">
      <slot name="loading">
        <p class="admin-table__state-text">正在加载…</p>
      </slot>
    </div>

    <div v-else-if="!rows.length" class="admin-table__state">
      <slot name="empty">
        <p class="admin-table__state-text">暂无数据</p>
      </slot>
    </div>

    <template v-else>
      <div class="admin-table__scroll">
        <table class="admin-table__table" :style="{ minWidth: `${minWidth}px` }">
          <colgroup>
            <col
              v-for="column in columns"
              :key="column.key"
              :style="column.width ? { width: `${column.width}px` } : null"
            />
          </colgroup>

          <thead>
            <tr>
              <th
                v-for="column in columns"
                :key="column.key"
                :class="cellClass(column)"
              >
                {{ column.title }}
              </th>
            </tr>
          </thead>

          <tbody>
            <tr v-for="row in rows" :key="row[rowKey]">
              <td
                v-for="column in columns"
                :key="column.key"
                :class="cellClass(column)"
                :title="cellTooltip(column, row)"
              >
                <slot
                  v-if="column.key === 'actions'"
                  name="actions"
                  :row="row"
                />
                <slot
                  v-else-if="$slots[`cell-${column.key}`]"
                  :name="`cell-${column.key}`"
                  :row="row"
                />
                <template v-else>{{ cellText(column, row) }}</template>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <div class="admin-table__cards">
        <article
          v-for="row in rows"
          :key="row[rowKey]"
          class="admin-card-row"
        >
          <div class="admin-card-row__top">
            <span class="admin-card-row__eyebrow">
              <template
                v-for="(column, index) in eyebrowColumns"
                :key="column.key"
              >
                <span v-if="index > 0" class="admin-card-row__separator">·</span>
                <span>{{ eyebrowText(column, row) }}</span>
              </template>
            </span>
            <span v-if="statusColumn" class="admin-card-row__status">
              <slot
                v-if="$slots[`cell-${statusColumn.key}`]"
                :name="`cell-${statusColumn.key}`"
                :row="row"
              />
              <template v-else>{{ cellText(statusColumn, row) }}</template>
            </span>
          </div>

          <p
            v-if="bodyColumn"
            class="admin-card-row__body"
            :title="cellTooltip(bodyColumn, row)"
          >
            {{ cellText(bodyColumn, row) }}
          </p>

          <p v-if="metaColumns.length" class="admin-card-row__meta">
            <template v-for="(column, index) in metaColumns" :key="column.key">
              <span v-if="index > 0" class="admin-card-row__separator">·</span>
              <span>{{ metaText(column, row) }}</span>
            </template>
          </p>

          <div v-if="$slots.actions" class="admin-card-row__actions">
            <slot name="actions" :row="row" />
          </div>
        </article>
      </div>
    </template>

    <div v-if="$slots.pagination" class="admin-table__footer">
      <slot name="pagination" />
    </div>
  </section>
</template>

<style scoped>
.admin-table {
  display: flex;
  flex-direction: column;
  min-width: 0;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--bg-card);
  overflow: hidden;
}

.admin-table__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--line);
  color: var(--ink-2);
  font-size: 12px;
}

.admin-table__toolbar b {
  color: var(--ink-1);
  font-weight: 700;
}

.admin-table__hint {
  color: var(--ink-2);
}

.admin-table__state {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 14px;
}

.admin-table__state-text {
  color: var(--ink-2);
  font-size: 13px;
  text-align: center;
}

.admin-table__scroll {
  overflow: auto;
  max-height: calc(100vh - 280px);
}

.admin-table__table {
  width: 100%;
  table-layout: fixed;
  border-collapse: collapse;
  font-size: 13px;
}

.admin-table__table th,
.admin-table__table td {
  height: 44px;
  padding: 0 12px;
  overflow: hidden;
  border-bottom: 1px solid var(--line);
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.admin-table__table th {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 38px;
  background: var(--bg-soft);
  color: var(--ink-2);
  font-size: 12px;
  font-weight: 700;
}

.admin-table__table tbody tr:last-child td {
  border-bottom: none;
}

.admin-table__table tbody tr:hover td {
  background: var(--brand-soft);
}

.admin-table__table .is-num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.admin-table__table .is-mono {
  color: var(--ink-2);
  font-variant-numeric: tabular-nums;
}

/* 操作列固定在右侧；底色必须不透明，深夜滚动时文字才不会叠在一起。 */
.admin-table__table .is-sticky {
  position: sticky;
  right: 0;
  z-index: 2;
  background: var(--bg-card);
  box-shadow: -1px 0 0 var(--line);
  text-align: right;
}

.admin-table__table th.is-sticky {
  z-index: 3;
  background: var(--bg-soft);
}

.admin-table__table tbody tr:hover td.is-sticky {
  background: var(--brand-soft);
}

.admin-table__cards {
  display: none;
}

.admin-table__footer {
  padding: 10px 14px;
  border-top: 1px solid var(--line);
}

/* 分页沿用 BasePagination 的行为，只在后台范围内收紧成 28px 小按钮。 */
.admin-table__footer :deep(.pgn) {
  width: 100%;
  gap: 4px;
}

.admin-table__footer :deep(.pgn .pgn__total) {
  margin-right: auto;
  color: var(--ink-2);
  font-size: 12px;
}

.admin-table__footer :deep(.pgn .pgn__btn) {
  min-width: 28px;
  height: 28px;
  padding: 0 6px;
  border-radius: 6px;
  color: var(--ink-2);
  font-size: 12px;
}

.admin-table__footer :deep(.pgn .pgn__btn:not(:disabled):hover) {
  border-color: var(--brand-soft);
  background: var(--brand-soft);
  color: var(--ink-1);
}

.admin-table__footer :deep(.pgn .pgn__btn.is-active) {
  border-color: var(--brand-deep);
  background: var(--brand-deep);
  color: var(--bg-card);
  font-weight: 700;
}

/* ≤768px：表格换成卡片列表，列配置里的 mobile 角色决定每列出现的位置。 */
@media (max-width: 768px) {
  .admin-table__toolbar {
    padding: 10px 12px;
  }

  .admin-table__hint {
    display: none;
  }

  .admin-table__scroll {
    display: none;
  }

  .admin-table__cards {
    display: flex;
    flex-direction: column;
  }

  .admin-card-row {
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding: 12px;
    border-bottom: 1px solid var(--line);
  }

  .admin-card-row:last-child {
    border-bottom: none;
  }

  .admin-card-row__top {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    color: var(--ink-3);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }

  .admin-card-row__eyebrow {
    display: flex;
    gap: 4px;
    min-width: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .admin-card-row__status {
    flex: none;
  }

  .admin-card-row__separator {
    margin: 0 4px;
    opacity: 0.7;
  }

  .admin-card-row__body {
    display: -webkit-box;
    overflow: hidden;
    color: var(--ink-1);
    font-size: 14px;
    line-height: 1.5;
    white-space: normal;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
  }

  .admin-card-row__meta {
    color: var(--ink-3);
    font-size: 12px;
    line-height: 1.5;
  }

  .admin-card-row__actions {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
  }

  .admin-table__footer {
    padding: 10px 12px;
  }

  .admin-table__footer :deep(.pgn .pgn__btn) {
    min-width: 32px;
    height: 32px;
  }
}
</style>
