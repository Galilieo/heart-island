<script setup>
// 帖子卡。两种 variant：
//   - card：列表中使用，3 行截断，整卡可点跳详情
//   - detail：详情页使用，全文展示 + 楼主才显示的编辑/删除
// 点赞 / 收藏 / 编辑 / 删除都只发事件，逻辑在父级走 communityStore。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import BaseTag from '../ui/BaseTag.vue'
import { moodTone, formatTime } from '../../utils/mood'

const props = defineProps({
  post: { type: Object, required: true },
  topics: { type: Array, default: () => [] },
  loginUserId: { type: [String, Number, null], default: null },
  variant: {
    type: String,
    default: 'card', // card | detail
    validator: (v) => ['card', 'detail'].includes(v)
  }
})

defineEmits(['open', 'toggle-like', 'toggle-favorite', 'edit', 'delete'])

const topicName = computed(() => {
  const t = props.topics.find((x) => x.id === props.post.topicId)
  return t ? t.name : '话题'
})

const authorName = computed(() => {
  return props.post.anonymousName || props.post.nickname || '匿名朋友'
})

const isOwner = computed(
  () => props.loginUserId && props.post.userId === props.loginUserId
)
</script>

<template>
  <BaseCard
    :padding="variant === 'detail' ? 'lg' : 'md'"
    :hoverable="variant === 'card'"
    class="pc"
    :class="`pc--${variant}`"
    @click="variant === 'card' ? $emit('open', post.id) : null"
  >
    <header class="pc__head">
      <div class="pc__author">
        <span class="pc__avatar">{{ authorName[0]?.toUpperCase() || '心' }}</span>
        <div class="pc__author-meta">
          <p class="pc__author-name">{{ authorName }}</p>
          <p class="pc__author-time">{{ formatTime(post.createTime) }}</p>
        </div>
      </div>
      <div class="pc__tags">
        <BaseTag tone="brand" variant="soft" size="sm">{{ topicName }}</BaseTag>
        <BaseTag :tone="moodTone(post.moodType)" variant="soft" size="sm">
          {{ post.moodType }}
        </BaseTag>
      </div>
    </header>

    <p
      class="pc__content"
      :class="variant === 'card' ? 'clamp-3' : ''"
    >{{ post.content }}</p>

    <footer class="pc__actions" @click.stop>
      <button
        type="button"
        class="pc__action"
        :class="{ 'is-active': post.liked }"
        @click="$emit('toggle-like', post)"
      >
        <span class="pc__action-ico" aria-hidden="true">{{ post.liked ? '♥' : '♡' }}</span>
        {{ post.liked ? '已共情' : '共情' }}
        <span v-if="post.likeCount" class="pc__action-num">{{ post.likeCount }}</span>
      </button>

      <button
        type="button"
        class="pc__action"
        :class="{ 'is-active is-fav': post.favorited }"
        @click="$emit('toggle-favorite', post)"
      >
        <span class="pc__action-ico" aria-hidden="true">{{ post.favorited ? '★' : '☆' }}</span>
        {{ post.favorited ? '已收藏' : '收藏' }}
      </button>

      <span v-if="post.replyCount" class="pc__action pc__action--ghost">
        <span class="pc__action-ico" aria-hidden="true">💬</span>
        {{ post.replyCount }} 条回复
      </span>

      <span class="pc__spacer" />

      <template v-if="isOwner && variant === 'detail'">
        <button type="button" class="pc__action pc__action--soft" @click="$emit('edit', post)">
          编辑
        </button>
        <button type="button" class="pc__action pc__action--danger" @click="$emit('delete', post)">
          删除
        </button>
      </template>
    </footer>
  </BaseCard>
</template>

<style scoped>
.pc {
  transition: transform var(--t-base) var(--ease-soft),
    box-shadow var(--t-base) var(--ease-soft);
}

.pc--card { cursor: pointer; }

.pc__head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  margin-bottom: 12px;
}

.pc__author {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.pc__avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--brand), var(--accent));
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: var(--fs-sm);
  font-weight: 700;
  flex-shrink: 0;
}

.pc__author-meta { min-width: 0; }

.pc__author-name {
  margin: 0;
  font-size: var(--fs-md);
  font-weight: 600;
  color: var(--ink-1);
}

.pc__author-time {
  margin: 2px 0 0;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.pc__tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.pc__content {
  margin: 8px 0 14px;
  color: var(--ink-1);
  line-height: 1.85;
  font-size: var(--fs-md);
  white-space: pre-wrap;
}

.pc--detail .pc__content {
  font-size: var(--fs-lg);
}

.pc__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.pc__action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 32px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--ink-2);
  font-size: var(--fs-sm);
  font-weight: 600;
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition: background var(--t-fast) var(--ease-soft),
    color var(--t-fast) var(--ease-soft);
}

.pc__action:hover {
  background: var(--bg-soft);
  color: var(--ink-1);
}

.pc__action.is-active {
  background: var(--accent-soft);
  color: #a45a4a;
}

.pc__action.is-fav {
  background: var(--joy-soft);
  color: #a06b1e;
}

.pc__action--ghost {
  cursor: default;
  color: var(--ink-3);
}
.pc__action--ghost:hover {
  background: transparent;
  color: var(--ink-3);
}

.pc__action--soft {
  background: var(--brand-soft);
  color: var(--brand-deep);
}
.pc__action--soft:hover { background: #d6e8e5; }

.pc__action--danger:hover {
  background: var(--danger-soft);
  color: #a83a3a;
}

.pc__action-ico {
  font-size: 14px;
  line-height: 1;
}

.pc__action-num {
  font-variant-numeric: tabular-nums;
  margin-left: 1px;
}

.pc__spacer { flex: 1; }
</style>
