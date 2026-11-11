<script setup>
// 回复列表：虚线分隔的条目（匿名名 · 时间一行，下面正文），不逐条做卡。
// 楼主才能看到的"编辑/删除"按钮 inline 工作：
//   - 编辑：在条目内切到 textarea，保存通过 @edit-submit 给父级，
//     父级在成功后调用回调 done() 让本组件退出编辑态。
//   - 删除：直接 @delete，由父级走 useConfirm + store。
import { ref, computed } from 'vue'
import BaseTextarea from '../ui/BaseTextarea.vue'
import BaseButton from '../ui/BaseButton.vue'
import EmptyState from '../ui/EmptyState.vue'
import SkeletonCard from '../ui/SkeletonCard.vue'
import { formatTime } from '../../utils/mood'

const props = defineProps({
  replies: { type: Array, default: () => [] },
  loading: Boolean,
  loginUserId: { type: [String, Number, null], default: null },
  updatingId: { type: [String, Number, null], default: null },
  deletingId: { type: [String, Number, null], default: null }
})

const emit = defineEmits(['edit-submit', 'delete'])

const editingId = ref(null)
const editContent = ref('')

/* ───── 前端分页：后端一次返回全部回复，列表太长时先收起来 ───── */
const INITIAL_COUNT = 5
const PAGE_STEP = 10
const visibleCount = ref(INITIAL_COUNT)

const visibleReplies = computed(() => props.replies.slice(0, visibleCount.value))
const remaining = computed(() => props.replies.length - visibleCount.value)
const collapsible = computed(
  () => remaining.value <= 0 && props.replies.length > INITIAL_COUNT
)

function loadMore() {
  visibleCount.value += PAGE_STEP
}

function collapse() {
  visibleCount.value = INITIAL_COUNT
}

const canSave = computed(
  () => editContent.value.trim().length > 0 && editContent.value.length <= 500
)

function isOwner(reply) {
  return props.loginUserId && reply.userId === props.loginUserId
}

function startEdit(reply) {
  editingId.value = reply.id
  editContent.value = reply.content
}

function cancelEdit() {
  editingId.value = null
  editContent.value = ''
}

async function saveEdit(reply) {
  if (!canSave.value) return
  emit('edit-submit', { id: reply.id, content: editContent.value.trim() }, () => {
    editingId.value = null
    editContent.value = ''
  })
}
</script>

<template>
  <div class="rl">
    <header class="rl__head">
      <h3 class="rl__title">回复</h3>
      <span class="rl__count">共 {{ replies.length }} 条</span>
    </header>

    <template v-if="loading && !replies.length">
      <SkeletonCard v-for="i in 2" :key="i" rows="2" />
    </template>

    <template v-else-if="replies.length">
      <article v-for="r in visibleReplies" :key="r.id" class="rl__item">
        <header class="rl__item-head">
          <p class="rl__meta">
            <span class="rl__name">{{ r.anonymousName || r.nickname || '匿名朋友' }}</span>
            <span class="rl__meta-sep" aria-hidden="true">·</span>
            <span class="rl__time">{{ formatTime(r.createTime) }}</span>
          </p>

          <div v-if="isOwner(r) && editingId !== r.id" class="rl__owner-ops">
            <button
              type="button"
              class="rl__op"
              :disabled="updatingId === r.id || deletingId === r.id"
              @click="startEdit(r)"
            >编辑</button>
            <button
              type="button"
              class="rl__op rl__op--danger"
              :disabled="deletingId === r.id"
              @click="emit('delete', r)"
            >{{ deletingId === r.id ? '删除中…' : '删除' }}</button>
          </div>
        </header>

        <div v-if="editingId === r.id" class="rl__edit">
          <BaseTextarea
            v-model="editContent"
            :rows="3"
            :maxlength="500"
            :disabled="updatingId === r.id"
            placeholder="编辑回复内容"
          />
          <div class="rl__edit-actions">
            <BaseButton variant="plain" size="sm" @click="cancelEdit">取消</BaseButton>
            <BaseButton
              variant="primary"
              size="sm"
              :loading="updatingId === r.id"
              :disabled="!canSave"
              @click="saveEdit(r)"
            >保存</BaseButton>
          </div>
        </div>

        <p v-else class="rl__content">{{ r.content }}</p>
      </article>

      <div v-if="remaining > 0 || collapsible" class="rl__more">
        <BaseButton
          v-if="remaining > 0"
          variant="ghost"
          size="sm"
          block
          @click="loadMore"
        >展开更多回复（还有 {{ remaining }} 条）</BaseButton>
        <BaseButton
          v-else
          variant="ghost"
          size="sm"
          block
          @click="collapse"
        >收起回复</BaseButton>
      </div>
    </template>

    <EmptyState
      v-else
      doodle="leaf"
      title="还没有人回复"
      hint="有什么想对作者说的，可以写一条回复。"
    />
  </div>
</template>

<style scoped>
.rl {
  display: flex;
  flex-direction: column;
}

.rl__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 4px;
}

.rl__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--fs-xl);
  font-weight: 400;
}

.rl__count {
  color: var(--ink-3);
  font-size: var(--fs-sm);
}

/* 条目之间用虚线分隔，同一回复的头与正文之间不再加线 */
.rl__item {
  padding: 14px 0;
  border-top: 1px dashed var(--line-strong);
}

.rl__item-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.rl__meta {
  display: flex;
  align-items: baseline;
  gap: 6px;
  min-width: 0;
  margin: 0;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.rl__name {
  color: var(--ink-2);
  font-weight: 600;
}

.rl__owner-ops {
  display: flex;
  gap: 4px;
}

/* 自己回复的操作：纯文字按钮，与帖子卡操作区一致 */
.rl__op {
  display: inline-flex;
  align-items: center;
  min-height: 44px;
  padding: 0 10px;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  cursor: pointer;
  transition: color var(--t-fast) var(--ease-soft);
}

.rl__op:hover { color: var(--ink-1); }

.rl__op:disabled { opacity: 0.55; cursor: not-allowed; }

.rl__op--danger:hover {
  color: var(--danger-ink);
}

.rl__content {
  margin: 0;
  color: var(--ink-1);
  line-height: 1.8;
  font-size: var(--fs-md);
  white-space: pre-wrap;
}

.rl__more {
  display: flex;
  flex-direction: column;
}

.rl__edit {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 4px;
}

.rl__edit-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
</style>
