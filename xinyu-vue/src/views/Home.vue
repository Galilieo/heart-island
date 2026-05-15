<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'

const router = useRouter()

const loginUser = ref(null)

const moodType = ref('')
const content = ref('')
const moodList = ref([])
const message = ref('')
const messageType = ref('success')


const adding =ref(false)

onMounted(() => {
  const loginUserStr = localStorage.getItem('loginUser')
  const token = localStorage.getItem('token')

  if (!loginUserStr || !token) {
    alert('请先登录')
    router.push('/')
    return
  }

  loginUser.value = JSON.parse(loginUserStr)
  loadMoodList()
})

function addMood() {
  if (!loginUser.value) {
    showMessage('请先登录', 'warning')
    router.push('/')
    return
  }

  if (!moodType.value) {
    showMessage('请选择心情类型','warning')
    return
  }

  if (!content.value || content.value.trim() === '') {
    showMessage('请输入心情内容', 'warning')
    return
  }

  if (adding.value) {
    return
  }

  adding.value=true

  request.post('/mood/add', {
    moodType: moodType.value,
    content: content.value
  })
    .then(res => {
      console.log('新增心情返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message, 'success')
        moodType.value = ''
        content.value = ''
        loadMoodList()
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch(err => {
      console.log('新增心情失败：', err)
      showMessage('新增失败，请检查后端是否启动', 'error')
    })
    .finally(() => {
      adding.value=false
    })
}

function loadMoodList() {
  if (!loginUser.value) {
    return
  }

  request.get('/mood/list')
    .then(res => {
      console.log('心情记录列表：', res.data)

      if (res.data.code === 200) {
        moodList.value = res.data.data
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch(err => {
      console.log('查询心情记录失败：', err)
      showMessage('查询失败，请检查后端是否启动', 'error')
    })
}

function deleteMood(id) {
  const ok = confirm('确定要删除这条心情记录吗？')

  if (!ok) {
    return
  }

  request.delete('/mood/delete/' + id)
    .then(res => {
      console.log('删除返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message, 'success')
        loadMoodList()
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch(err => {
      console.log('删除失败：', err)
      showMessage('删除失败，请检查后端是否启动', 'error')
    })
}

function logout() {
  localStorage.removeItem('loginUser')
  localStorage.removeItem('token')

  loginUser.value = null
  moodList.value = []

  router.push('/')
}
function getMoodClass(moodType) {
  if (moodType === '开心') {
    return 'mood-happy'
  }

  if (moodType === '平静') {
    return 'mood-calm'
  }

  if (moodType === '焦虑') {
    return 'mood-anxious'
  }

  if (moodType === '难过') {
    return 'mood-sad'
  }

  return 'mood-default'
}

function formatTime(time) {
  if (!time) {
    return ''
  }
  return time.replace('T',' ').substring(0,16)
}

function showMessage(text, type = 'success') {
  message.value = text
  messageType.value = type

  setTimeout(() => {
    message.value=''
  },2000)
}
</script>

<template>
  <div class="home-page">
    <div v-if="message" class="message" :class="messageType">
      {{ message }}</div>
    <header class="header">
      <div>
        <h1>心屿</h1>
        <p>AI 情绪陪伴系统</p>
      </div>

      <div class="user-area" v-if="loginUser">
        <span>欢迎你，{{ loginUser.nickname || loginUser.username }}</span>
        <button class="logout-btn" @click="logout">退出登录</button>
      </div>
    </header>

    <main class="main">
      <section class="panel add-panel">
        <h2>记录今天的心情</h2>
        <p class="panel-desc">写下此刻的感受，让心情有一个安放的地方。</p>

        <div class="form">
          <select v-model="moodType">
            <option value="">请选择心情类型</option>
            <option value="开心">开心</option>
            <option value="平静">平静</option>
            <option value="焦虑">焦虑</option>
            <option value="难过">难过</option>
            <option value="疲惫">疲惫</option>
            <option value="其他">其他</option>
          </select>

          <textarea
            v-model="content"
            placeholder="请输入今天的心情内容"
          ></textarea>

          <button class="primary-btn" :disabled="adding" @click="addMood">{{ adding ? '提交中...':'新增心情记录' }}</button>
        </div>
      </section>

      <section class="panel list-panel">
        <div class="list-header">
          <div>
            <h2>我的心情记录</h2>
            <p class="panel-desc">查看你最近记录下来的情绪变化。</p>
          </div>

          <button class="refresh-btn" @click="loadMoodList">刷新</button>
        </div>

        <div v-if="moodList.length === 0" class="empty">
          <div class="empty-title">暂无心情记录</div>
          <div class="empty-desc">写下第一条记录，给今天的情绪一个出口。</div>
        </div>

        <div v-else class="record-list">
          <div v-for="item in moodList" :key="item.id" class="record-card">
            <div class="record-top">
              <span class="mood-tag" :class="getMoodClass(item.moodType)">{{ item.moodType }}</span>
              <button class="delete-btn" @click="deleteMood(item.id)">删除</button>
            </div>

            <p class="record-content">{{ item.content }}</p>
            <p class="record-time">{{ formatTime(item.createTime) }}</p>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 28px;
}

.header {
  max-width: 1100px;
  margin: 0 auto 24px;
  padding: 22px 28px;
  background: #ffffff;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgba(80, 100, 140, 0.12);
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header h1 {
  margin: 0;
  font-size: 30px;
  color: #4b6cb7;
}

.header p {
  margin: 6px 0 0;
  color: #777;
  font-size: 14px;
}

.user-area {
  display: flex;
  align-items: center;
  gap: 14px;
  color: #555;
}

.logout-btn {
  height: 36px;
  padding: 0 16px;
  border: none;
  border-radius: 9px;
  background: #f2f4ff;
  color: #4b6cb7;
}

.logout-btn:hover {
  background: #e4e8ff;
}

.main {
  max-width: 1100px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 380px 1fr;
  gap: 24px;
}

.panel {
  background: #ffffff;
  border-radius: 18px;
  padding: 24px;
  box-shadow: 0 10px 24px rgba(80, 100, 140, 0.12);
}

.panel h2 {
  margin: 0;
  font-size: 22px;
  color: #333;
}

.panel-desc {
  margin: 8px 0 20px;
  color: #888;
  font-size: 14px;
}

.form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

input,
select,
textarea {
  border: 1px solid #dcdfe6;
  border-radius: 10px;
  padding: 12px 14px;
  font-size: 14px;
  outline: none;
  background: #fff;
}

input:focus,
select:focus,
textarea:focus {
  border-color: #6c8cff;
}

.primary-btn:disabled {
  background: #b8c4ff;
  cursor: not-allowed;
}

input:focus,
textarea:focus {
  border-color: #6c8cff;
}

textarea {
  min-height: 150px;
  resize: vertical;
}

.primary-btn {
  height: 42px;
  border: none;
  border-radius: 10px;
  background: #6c8cff;
  color: white;
  font-size: 15px;
}

.primary-btn:hover {
  background: #5878f0;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.refresh-btn {
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 9px;
  background: #f2f4ff;
  color: #4b6cb7;
}

.refresh-btn:hover {
  background: #e4e8ff;
}

.empty {
  margin-top: 24px;
  padding: 36px 28px;
  border-radius: 14px;
  background: #f8f9ff;
  text-align: center;
}

.empty-title {
  color: #555;
  font-size: 16px;
  margin-bottom: 8px;
}

.empty-desc {
  color: #999;
  font-size: 14px;
}

.record-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.record-card {
  padding: 16px;
  border-radius: 14px;
  background: #f8f9ff;
  border: 1px solid #edf0ff;
}

.record-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mood-tag {
  display: inline-block;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 500;
}

.mood-happy {
  background: #e8edff;
  color: #4b6cb7;
}

.mood-calm {
  background: #e8f7ef;
  color: #2f8f5b;
}

.mood-anxious {
  background: #fff2df;
  color: #d9822b;
}

.mood-sad {
  background: #edf2f7;
  color: #5b6b7a;
}

.mood-default {
  background: #f0f2ff;
  color: #5c6bc0;
}
.delete-btn {
  border: none;
  background: transparent;
  color: #d9534f;
  font-size: 14px;
}

.record-content {
  margin: 14px 0 10px;
  color: #333;
  line-height: 1.7;
}

.record-time {
  margin: 0;
  color: #999;
  font-size: 13px;
}

@media (max-width: 900px) {
  .main {
    grid-template-columns: 1fr;
  }

  .header {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
  }
}

.message {
  max-width: 1100px;
  margin: 0 auto 16px;
  padding: 12px 16px;
  border-radius: 10px;
  font-size: 14px;
}

.message.success {
  background: #e8f7ef;
  color: #2f8f5b;
}

.message.error {
  background: #fff0f0;
  color: #d9534f;
}

.message.warning {
  background: #fff7e6;
  color: #d9822b;
}
</style>
