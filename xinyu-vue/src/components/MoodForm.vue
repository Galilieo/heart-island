<script setup>
defineProps({
  moodOptions: {
    type: Array,
    required: true
  },
  moodType: {
    type: String,
    required: true
  },
  content: {
    type: String,
    required: true
  },
  adding: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:moodType', 'update:content', 'submit'])

function selectMood(value) {
  emit('update:moodType', value)
}

function updateContent(event) {
  emit('update:content', event.target.value)
}

function submitForm() {
  emit('submit')
}
</script>

<template>
  <section class="panel add-panel">
    <div class="section-kicker">今日记录</div>
    <h2>把此刻放下来</h2>
    <p class="panel-desc">写下一点真实感受，心屿会给你一段轻轻的回应。</p>

    <div class="form">
      <div class="mood-options">
        <button
          v-for="item in moodOptions"
          :key="item.value"
          type="button"
          class="mood-option"
          :class="{ active: moodType === item.value }"
          :disabled="adding"
          @click="selectMood(item.value)"
        >
          {{ item.label }}
        </button>
      </div>

      <textarea
        :value="content"
        :disabled="adding"
        placeholder="今天发生了什么？你现在是什么感受？"
        @input="updateContent"
      ></textarea>

      <button class="primary-btn" :disabled="adding" @click="submitForm">
        {{ adding ? '心屿正在生成回复...' : '新增心情记录' }}
      </button>
    </div>
  </section>
</template>

<style scoped>
.panel {
  border: 1px solid rgba(90, 110, 150, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 18px 40px rgba(46, 64, 100, 0.08);
}

.add-panel {
  padding: 28px;
  align-self: start;
  position: sticky;
  top: 24px;
}

.section-kicker {
  margin-bottom: 8px;
  color: #3f7378;
  font-size: 13px;
  font-weight: 700;
}

h2 {
  margin: 0;
  color: #2f3a3d;
  font-size: 28px;
  letter-spacing: 0;
}

.panel-desc {
  margin: 6px 0 0;
  color: #778184;
  line-height: 1.6;
}

.form {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.mood-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 10px;
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
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  padding: 12px 14px;
  outline: none;
  background: #fff;
  color: #1f2937;
  font-size: 15px;
  min-height: 180px;
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

.primary-btn {
  height: 40px;
  border: none;
  border-radius: 8px;
  padding: 0 16px;
  white-space: nowrap;
  background: linear-gradient(135deg, #4f8f91, #6caeb0);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(95, 158, 160, 0.2);
}

.primary-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3f7378, #5f9ea0);
}

@media (max-width: 980px) {
  .add-panel {
    position: static;
  }
}
</style>
