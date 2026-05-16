<script setup>
defineProps({
  mood: {
    type: Object,
    required: true
  },
  moodOptions: {
    type: Array,
    required: true
  },
  editMoodType: {
    type: String,
    required: true
  },
  editContent: {
    type: String,
    required: true
  },
  editing: {
    type: Boolean,
    default: false
  },
  updating: {
    type: Boolean,
    default: false
  },
  moodClass: {
    type: String,
    required: true
  },
  formatTime: {
    type: Function,
    required: true
  }
})

const emit = defineEmits([
  'close',
  'start-edit',
  'update',
  'cancel-edit',
  'update:editMoodType',
  'update:editContent'
])

function selectEditMood(value) {
  emit('update:editMoodType', value)
}

function updateEditContent(event) {
  emit('update:editContent', event.target.value)
}
</script>

<template>
  <div class="detail-overlay" @click.self="emit('close')">
    <section class="detail-dialog">
      <div class="detail-header">
        <div>
          <div class="section-kicker">记录详情</div>
          <h3>心情记录详情</h3>
        </div>

        <div class="detail-actions">
          <button class="soft-btn" :disabled="updating" @click="emit('start-edit', mood)">编辑</button>
          <button class="plain-btn" :disabled="updating" @click="emit('close')">关闭</button>
        </div>
      </div>

      <div class="detail-meta">
        <span class="mood-tag" :class="moodClass">
          {{ mood.moodType }}
        </span>
        <span>创建于 {{ formatTime(mood.createTime) }}</span>
        <span>更新于 {{ formatTime(mood.updateTime) }}</span>
      </div>

      <div class="detail-block">
        <h4>心情内容</h4>
        <p>{{ mood.content }}</p>
      </div>

      <div class="detail-block ai-detail">
        <h4>心屿回复</h4>
        <p>{{ mood.aiReply || '暂无心屿回复' }}</p>
      </div>

      <div v-if="editing" class="edit-box">
        <h4>编辑心情记录</h4>

        <div class="mood-options compact">
          <button
            v-for="item in moodOptions"
            :key="item.value"
            type="button"
            class="mood-option"
            :disabled="updating"
            :class="{ active: editMoodType === item.value }"
            @click="selectEditMood(item.value)"
          >
            {{ item.label }}
          </button>
        </div>

        <textarea
          :value="editContent"
          :disabled="updating"
          placeholder="请输入修改后的心情内容"
          @input="updateEditContent"
        ></textarea>

        <div class="edit-actions">
          <button class="primary-btn" :disabled="updating" @click="emit('update')">
            {{ updating ? '心屿正在重新生成回复...' : '保存修改' }}
          </button>
          <button class="plain-btn" :disabled="updating" @click="emit('cancel-edit')">
            取消
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-overlay {
  position: fixed;
  inset: 0;
  z-index: 20;
  padding: 32px;
  background: rgba(21, 30, 48, 0.42);
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-dialog {
  width: min(700px, 100%);
  max-height: calc(100vh - 64px);
  overflow: auto;
  padding: 26px;
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.28);
}

.detail-header {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  align-items: flex-start;
}

.detail-actions,
.edit-actions {
  display: flex;
  align-items: center;
}

.detail-actions {
  gap: 8px;
}

.section-kicker {
  margin-bottom: 8px;
  color: #3f7378;
  font-size: 13px;
  font-weight: 700;
}

h3 {
  margin: 0;
  color: #2f3a3d;
  letter-spacing: 0;
}

.detail-meta {
  margin: 18px 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  color: #7b817c;
  font-size: 14px;
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

.detail-block {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: #fffdf8;
}

.detail-block h4,
.edit-box h4 {
  margin: 0 0 10px;
  color: #263244;
  font-size: 16px;
}

.detail-block p {
  margin: 0;
  color: #3d4748;
  line-height: 1.9;
  font-size: 15px;
}

.ai-detail {
  border-left: 3px solid #9b8ac7;
  background: linear-gradient(90deg, #f3effa, #fffdf8);
}

.edit-box {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #dfe6f2;
  border-radius: 8px;
  display: grid;
  gap: 12px;
}

.mood-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
}

.mood-options.compact {
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 8px;
}

.mood-options.compact .mood-option {
  height: 36px;
  padding: 0 8px;
  font-size: 13px;
}

.mood-option {
  height: 42px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: #fff;
  color: #4b5568;
}

.mood-option.active {
  border-color: #5f9ea0;
  background: #e8f1ef;
  color: #3f7378;
  font-weight: 700;
}

textarea {
  width: 100%;
  box-sizing: border-box;
  min-height: 180px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  padding: 12px 14px;
  outline: none;
  background: #fff;
  color: #1f2937;
  font-size: 15px;
  resize: vertical;
  line-height: 1.7;
}

textarea:focus {
  border-color: #5f9ea0;
  box-shadow: 0 0 0 3px rgba(95, 158, 160, 0.14);
}

textarea:disabled {
  cursor: not-allowed;
  background: #f4f6fa;
  color: #98a2b3;
}

button {
  cursor: pointer;
  font-size: 14px;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
}

button:disabled {
  cursor: not-allowed;
  opacity: 0.65;
}

.primary-btn,
.soft-btn,
.plain-btn {
  height: 40px;
  border: none;
  border-radius: 8px;
  padding: 0 16px;
  white-space: nowrap;
}

.primary-btn {
  background: linear-gradient(135deg, #4f8f91, #6caeb0);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(95, 158, 160, 0.2);
}

.primary-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3f7378, #5f9ea0);
}

.soft-btn {
  background: #e8f1ef;
  color: #3f7378;
}

.soft-btn:hover {
  background: #dbe9e7;
}

.plain-btn {
  background: #f1f3f6;
  color: #596273;
}

.plain-btn:hover {
  background: #e6e9ef;
}

.edit-actions {
  gap: 12px;
}

@media (max-width: 560px) {
  .detail-overlay {
    padding: 14px;
  }

  .detail-header {
    flex-direction: column;
  }

  .mood-options.compact {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
