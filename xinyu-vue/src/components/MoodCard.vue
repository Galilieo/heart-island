<script setup>
defineProps({
  mood: {
    type: Object,
    required: true
  },
  moodClass: {
    type: String,
    required: true
  },
  formattedTime: {
    type: String,
    required: true
  },
  deleting: {
    type: Boolean,
    default: false
  },
  detailLoading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['detail', 'delete'])
</script>

<template>
  <article class="record-card">
    <div class="record-main">
      <div class="record-top">
        <span class="mood-tag" :class="moodClass">
          {{ mood.moodType }}
        </span>
        <span class="record-time">{{ formattedTime }}</span>
      </div>

      <p class="record-content">{{ mood.content }}</p>

      <div class="ai-reply-box">
        <div class="ai-reply-title">心屿回复</div>
        <p class="ai-reply-content">
          {{ mood.aiReply || '暂无心屿回复' }}
        </p>
      </div>
    </div>

    <div class="record-actions">
      <button
        class="detail-btn"
        :disabled="detailLoading || deleting"
        @click="emit('detail', mood.id)"
      >
        {{ detailLoading ? '加载中' : '详情' }}
      </button>
      <button
        class="delete-btn"
        :disabled="deleting"
        @click="emit('delete', mood.id)"
      >
        {{ deleting ? '删除中' : '删除' }}
      </button>
    </div>
  </article>
</template>

<style scoped>
.record-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  padding: 18px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: #fffdf8;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, transform 0.18s ease;
}

.record-card:hover {
  border-color: #d9cdbc;
  box-shadow: 0 12px 28px rgba(46, 64, 100, 0.08);
  transform: translateY(-1px);
}

.record-main {
  min-width: 0;
}

.record-top,
.record-actions {
  display: flex;
  align-items: center;
}

.record-top {
  justify-content: space-between;
  gap: 12px;
}

.record-actions {
  gap: 8px;
  align-self: end;
}

.mood-tag {
  display: inline-flex;
  align-items: center;
  height: 28px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 700;
}

.mood-happy {
  background: #fff2cf;
  color: #9a6a1f;
}

.mood-calm {
  background: #e8f1ee;
  color: #3f7066;
}

.mood-anxious {
  background: #fbe8d8;
  color: #a65e2e;
}

.mood-sad {
  background: #edece7;
  color: #63635d;
}

.mood-default {
  background: #e8f1ef;
  color: #3f7378;
}

.record-content {
  margin: 12px 0;
  color: #263244;
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

.record-time {
  color: #8a94a6;
  font-size: 13px;
  white-space: nowrap;
}

.ai-reply-box {
  padding: 12px 14px;
  border-left: 3px solid #9b8ac7;
  border-radius: 8px;
  background: #f3effa;
}

.ai-reply-title {
  margin-bottom: 4px;
  color: #6f6294;
  font-size: 13px;
  font-weight: 700;
}

.ai-reply-content {
  margin: 0;
  color: #5f5b70;
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
}

button {
  cursor: pointer;
  font-size: 14px;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
  transform: none;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
}

.detail-btn,
.delete-btn {
  height: 40px;
  border: none;
  border-radius: 8px;
  padding: 0 16px;
  white-space: nowrap;
}

.detail-btn {
  background: #e8f1ef;
  color: #3f7378;
}

.detail-btn:hover {
  background: #dbe9e7;
}

.delete-btn {
  background: transparent;
  color: #b85b5b;
}

.delete-btn:hover {
  background: #fff1f1;
}

@media (max-width: 980px) {
  .record-card {
    grid-template-columns: 1fr;
  }

  .record-actions {
    justify-content: flex-end;
  }
}
</style>
