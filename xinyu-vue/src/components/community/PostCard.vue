<script setup>
// 帖子卡。两种 variant：
//   - card：列表中使用，3 行截断，整卡可点跳详情，小芽批注截 2 行
//   - detail：详情页使用，全文展示 + 楼主才显示的编辑/删除
// 抱抱 / 收藏 / 编辑 / 删除都只发事件，逻辑在父级走 communityStore
// （前台文案叫「抱抱」，事件名与字段沿用 like / liked / likeCount）。
import { computed } from 'vue'
import BaseCard from '../ui/BaseCard.vue'
import MoodTag from '../mood/MoodTag.vue'
import XiaoyaNote from '../ui/XiaoyaNote.vue'
import Xiaoya from '../ui/Xiaoya.vue'
import MoreMenu from './MoreMenu.vue'
import { formatTime } from '../../utils/mood'
import { reasonLabel } from '../../utils/communityReport'

const props = defineProps({
  post: { type: Object, required: true },
  topics: { type: Array, default: () => [] },
  reportedStatus: { type: String, default: '' },
  variant: {
    type: String,
    default: 'card', // card | detail
    validator: (v) => ['card', 'detail'].includes(v)
  }
})

defineEmits(['open', 'toggle-like', 'toggle-favorite', 'edit', 'delete', 'report'])

const topicName = computed(() => {
  const t = props.topics.find((x) => x.id === props.post.topicId)
  return t ? t.name : '话题'
})

const authorName = computed(() => {
  return props.post.anonymousName || props.post.nickname || '匿名朋友'
})

const isOwner = computed(() => Boolean(props.post.mine))

// 有数量时统一写「抱抱 N」，无数量按是否已抱抱区分
const likeLabel = computed(() => {
  if (props.post.likeCount) return `抱抱 ${props.post.likeCount}`
  return props.post.liked ? '已抱抱' : '抱抱'
})
</script>

<template>
  <BaseCard
    :padding="variant === 'detail' ? 'lg' : 'md'"
    :hoverable="variant === 'card'"
    class="pc motion-float-in"
    :class="`pc--${variant}`"
    @click="variant === 'card' ? $emit('open', post.id) : null"
  >
    <div v-if="variant === 'detail' && post.hidden" class="pc__hidden-banner" role="status">
      <Xiaoya expression="thinking" :size="40" />
      <div class="pc__hidden-copy">
        <strong>这条内容因违反社区规范已被隐藏，仅你自己可见。</strong>
        <p>
          违规原因：{{
            reasonLabel(post.hideReason)
          }}。如有异议，请按隐私政策中的联系邮箱与我们联系。
        </p>
      </div>
    </div>

    <header class="pc__meta">
      <MoodTag :mood-type="post.moodType" size="sm" />
      <span class="pc__meta-item">{{ authorName }}</span>
      <span class="pc__meta-sep" aria-hidden="true">·</span>
      <span class="pc__meta-item">{{ topicName }}</span>
      <span class="pc__meta-time">{{ formatTime(post.createTime) }}</span>
      <MoreMenu
        v-if="variant === 'detail' && !isOwner && !post.hidden"
        :reported-status="reportedStatus"
        label="举报或更多操作"
        @report="$emit('report', post)"
      />
    </header>

    <p class="pc__content" :class="variant === 'card' ? 'clamp-3' : ''">{{ post.content }}</p>

    <XiaoyaNote v-if="variant === 'card' && post.aiReply" :text="post.aiReply" :clamp="2" />

    <footer class="pc__actions" @click.stop>
      <button
        type="button"
        class="pc__action motion-press"
        :class="{ 'is-active': post.liked }"
        :disabled="Boolean(post.hidden)"
        :title="post.hidden ? '内容已被隐藏' : undefined"
        @click="$emit('toggle-like', post)"
      >
        <span class="pc__action-ico" aria-hidden="true">{{ post.liked ? '♥' : '♡' }}</span>
        {{ likeLabel }}
      </button>

      <button
        type="button"
        class="pc__action motion-press"
        :class="{ 'is-active is-fav': post.favorited }"
        :disabled="Boolean(post.hidden)"
        :title="post.hidden ? '内容已被隐藏' : undefined"
        @click="$emit('toggle-favorite', post)"
      >
        <span class="pc__action-ico" aria-hidden="true">{{ post.favorited ? '★' : '☆' }}</span>
        {{ post.favorited ? '已收藏' : '收藏' }}
      </button>

      <span v-if="post.replyCount" class="pc__action pc__action--ghost">
        回复 {{ post.replyCount }}
      </span>

      <span class="pc__spacer" />

      <template v-if="isOwner && variant === 'detail'">
        <button
          v-if="!post.hidden"
          type="button"
          class="pc__action pc__action--soft motion-press"
          @click="$emit('edit', post)"
        >
          编辑
        </button>
        <button
          type="button"
          class="pc__action pc__action--danger motion-press"
          @click="$emit('delete', post)"
        >
          删除
        </button>
      </template>
    </footer>
  </BaseCard>
</template>

<style scoped>
.pc {
  transition:
    transform var(--t-base) var(--ease-soft),
    box-shadow var(--t-base) var(--ease-soft);
}

/* 头部一行元信息：心情标签 + 匿名名 · 话题名，时间靠右；放不下时换行 */
.pc__meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px 6px;
  color: var(--ink-3);
  font-size: var(--fs-xs);
}

.pc__meta-time {
  margin-left: auto;
  white-space: nowrap;
}

.pc__content {
  margin: 10px 0 0;
  color: var(--ink-1);
  font-size: var(--fs-md);
  line-height: 1.85;
  white-space: pre-wrap;
}

/* 详情态：全文更松、更大，与首页时间线一致，不加底色装饰 */
.pc--detail .pc__content {
  font-size: var(--fs-lg);
  line-height: 1.95;
}

/* 操作区：上方虚线分隔，纯文字按钮（选中变色加粗） */
.pc__actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 14px;
  padding-top: 4px;
  border-top: 1px dashed var(--line-strong);
}

.pc__action {
  display: inline-flex;
  align-items: center;
  gap: 5px;
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

.pc__action:hover {
  color: var(--ink-1);
}

.pc__action.is-active {
  color: var(--accent-ink);
  font-weight: 700;
}

.pc__action.is-fav {
  color: var(--joy-ink);
}

.pc__action--ghost {
  cursor: default;
  color: var(--ink-3);
}

.pc__action--soft {
  color: var(--brand-deep);
}

.pc__action--danger:hover {
  color: var(--danger-ink);
}

.pc__action-ico {
  font-size: 14px;
  line-height: 1;
}

.pc__action:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.pc__hidden-banner {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
  padding: 12px 14px;
  border: 1px solid var(--warn-line, rgba(212, 147, 57, 0.35));
  border-radius: var(--radius-2);
  background: var(--warn-soft, rgba(242, 184, 75, 0.12));
  color: var(--ink-1);
}

.pc__hidden-copy {
  min-width: 0;
  font-size: var(--fs-sm);
  line-height: 1.6;
}

.pc__hidden-copy strong {
  display: block;
  color: var(--warn-ink, #9a6011);
}

.pc__hidden-copy p {
  margin: 2px 0 0;
  color: var(--ink-2);
  font-size: var(--fs-xs);
}

.pc__spacer {
  flex: 1;
}
</style>
