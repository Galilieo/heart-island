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
const selectedMood = ref(null)
const adding = ref(false)
const editingMoodId = ref(null)
const editMoodType = ref('')
const editContent = ref('')
const updating = ref(false)
const filterMoodType = ref('')
const startDate = ref('')
const endDate = ref('')
const pageNum = ref(1)
const pageSize = ref(5)
const total = ref(0)
const pages = ref(0)

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
    showMessage('请选择心情类型', 'warning')
    return
  }

  if (!content.value || content.value.trim() === '') {
    showMessage('请输入心情内容', 'warning')
    return
  }

  if (adding.value) {
    return
  }

  adding.value = true
  showMessage('心屿正在生成回复，请稍等...', 'warning')

  request
    .post('/mood/add', {
      moodType: moodType.value,
      content: content.value
    })
    .then((res) => {
      console.log('新增心情返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage('记录成功，心屿回复已生成', 'success')
        moodType.value = ''
        content.value = ''
        loadMoodList()
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch((err) => {
      console.log('新增心情失败：', err)
      showMessage('新增失败，请检查后端或 AI 服务是否正常', 'error')
    })
    .finally(() => {
      adding.value = false
    })
}

function loadMoodList() {
  if (!loginUser.value) {
    return
  }

  if (startDate.value && endDate.value && startDate.value > endDate.value) {
    showMessage('开始日期不能晚于结束日期', 'warning')
    return
  }

  request
    .get('/mood/list', {
      params: {
        moodType: filterMoodType.value,
        startDate: startDate.value,
        endDate: endDate.value,
        pageNum: pageNum.value,
        pageSize: pageSize.value
      }
    })
    .then((res) => {
      console.log('心情记录列表：', res.data)

      if (res.data.code === 200) {
        const pageData = res.data.data

        moodList.value = pageData.records
        total.value = pageData.total
        pages.value = pageData.pages
        pageNum.value = pageData.current
        pageSize.value = pageData.size
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch((err) => {
      console.log('查询心情记录失败：', err)
      showMessage('查询失败，请检查后端是否启动', 'error')
    })
}

function deleteMood(id) {
  const ok = confirm('确定要删除这条心情记录吗？')

  if (!ok) {
    return
  }

  request
    .delete('/mood/delete/' + id)
    .then((res) => {
      console.log('删除返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message, 'success')
        loadMoodList()
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch((err) => {
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

function getMoodClass(currentMoodType) {
  if (currentMoodType === '开心') {
    return 'mood-happy'
  }

  if (currentMoodType === '平静') {
    return 'mood-calm'
  }

  if (currentMoodType === '焦虑') {
    return 'mood-anxious'
  }

  if (currentMoodType === '难过') {
    return 'mood-sad'
  }

  return 'mood-default'
}

function formatTime(time) {
  if (!time) {
    return ''
  }

  return time.replace('T', ' ').substring(0, 16)
}

function showMessage(text, type = 'success') {
  message.value = text
  messageType.value = type

  setTimeout(() => {
    message.value = ''
  }, 2000)
}

function loadMoodDetail(id) {
  request
    .get('/mood/detail/' + id)
    .then((res) => {
      console.log('心情详情：', res.data)

      if (res.data.code === 200) {
        selectedMood.value = res.data.data
        showMessage('详情加载成功', 'success')
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch((err) => {
      console.log('查询详情失败：', err)
      showMessage('查询详情失败，请检查后端是否启动', 'error')
    })
}

function closeDetail() {
  selectedMood.value = null
  cancelEdit()
}

function startEdit(mood) {
  editingMoodId.value = mood.id
  editMoodType.value = mood.moodType
  editContent.value = mood.content
}

function cancelEdit() {
  editingMoodId.value = null
  editMoodType.value = ''
  editContent.value = ''
}

function updateMood() {
  if (!editingMoodId.value) {
    showMessage('请选择要修改的记录', 'warning')
    return
  }

  if (!editMoodType.value) {
    showMessage('请选择心情类型', 'warning')
    return
  }

  if (!editContent.value || editContent.value.trim() === '') {
    showMessage('请输入心情内容', 'warning')
    return
  }

  if (updating.value) {
    return
  }

  updating.value = true
  showMessage('心屿正在重新生成回复，请稍等...', 'warning')

  request
    .put('/mood/update/' + editingMoodId.value, {
      moodType: editMoodType.value,
      content: editContent.value.trim()
    })
    .then((res) => {
      console.log('修改心情返回结果', res.data)

      if (res.data.code === 200) {
        showMessage('修改成功，心屿回复已更新', 'success')

        const updatedId = editingMoodId.value

        cancelEdit()
        loadMoodList()

        if (selectedMood.value && selectedMood.value.id === updatedId) {
          loadMoodDetail(updatedId)
        }
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch((err) => {
      console.log('修改心情失败：', err)
      showMessage('修改失败，请检查后端或 AI 服务是否正常', 'error')
    })
    .finally(() => {
      updating.value = false
    })
}

function resetFilter() {
  filterMoodType.value = ''
  startDate.value = ''
  endDate.value = ''
  pageNum.value = 1
  loadMoodList()
}

function searchMoodList() {
  pageNum.value = 1
  loadMoodList()
}

function prevPage() {
  if (pageNum.value <= 1) {
    return
  }

  pageNum.value--
  loadMoodList()
}

function nextPage() {
  if (pageNum.value >= pages.value) {
    return
  }

  pageNum.value++
  loadMoodList()
}
</script>

<template>
  <div class="home-page">
    <div v-if="message" class="message" :class="messageType">
      {{ message }}
    </div>

    <header class="header">
      <div class="brand">
        <div class="brand-mark">心</div>
        <div>
          <h1>心屿</h1>
          <p>AI 情绪陪伴系统</p>
        </div>
      </div>

      <div class="user-area" v-if="loginUser">
        <span>欢迎你，{{ loginUser.nickname || loginUser.username }}</span>
        <button class="ghost-btn" @click="logout">退出登录</button>
      </div>
    </header>

    <main class="main">
      <section class="panel add-panel">
        <div class="section-kicker">今日记录</div>
        <h2>把此刻放下来</h2>
        <p class="panel-desc">写下一点真实感受，心屿会给你一段轻轻的回应。</p>

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
            placeholder="今天发生了什么？你现在是什么感受？"
          ></textarea>

          <button class="primary-btn" :disabled="adding" @click="addMood">
            {{ adding ? '心屿正在生成回复...' : '新增心情记录' }}
          </button>
        </div>
      </section>

      <section class="panel list-panel">
        <div class="list-header">
          <div>
            <div class="section-kicker">记录列表</div>
            <h2>我的心情记录</h2>
            <p class="panel-desc">用更轻的方式回看最近的情绪变化。</p>
          </div>

          <button class="ghost-btn" @click="loadMoodList">刷新</button>
        </div>

        <div class="filter-area">
          <select v-model="filterMoodType">
            <option value="">全部心情</option>
            <option value="开心">开心</option>
            <option value="平静">平静</option>
            <option value="焦虑">焦虑</option>
            <option value="难过">难过</option>
            <option value="疲惫">疲惫</option>
            <option value="其他">其他</option>
          </select>

          <input v-model="startDate" type="date" />
          <input v-model="endDate" type="date" />

          <button class="soft-btn" @click="searchMoodList">查询</button>
          <button class="plain-btn" @click="resetFilter">重置</button>
        </div>

        <div v-if="moodList.length === 0" class="empty">
          <div class="empty-title">暂无心情记录</div>
          <div class="empty-desc">写下第一条记录，给今天的情绪一个出口。</div>
        </div>

        <div v-else class="record-list">
          <article v-for="item in moodList" :key="item.id" class="record-card">
            <div class="record-main">
              <div class="record-top">
                <span class="mood-tag" :class="getMoodClass(item.moodType)">
                  {{ item.moodType }}
                </span>
                <span class="record-time">{{ formatTime(item.createTime) }}</span>
              </div>

              <p class="record-content">{{ item.content }}</p>

              <div class="ai-reply-box">
                <div class="ai-reply-title">心屿回复</div>
                <p class="ai-reply-content">
                  {{ item.aiReply || '暂无心屿回复' }}
                </p>
              </div>
            </div>

            <div class="record-actions">
              <button class="detail-btn" @click="loadMoodDetail(item.id)">详情</button>
              <button class="delete-btn" @click="deleteMood(item.id)">删除</button>
            </div>
          </article>
        </div>

        <div v-if="total > 0" class="pagination">
          <button class="page-btn" :disabled="pageNum <= 1" @click="prevPage">
            上一页
          </button>

          <span>第 {{ pageNum }} / {{ pages }} 页，共 {{ total }} 条</span>

          <button class="page-btn" :disabled="pageNum >= pages" @click="nextPage">
            下一页
          </button>
        </div>
      </section>
    </main>

    <div v-if="selectedMood" class="detail-overlay" @click.self="closeDetail">
      <section class="detail-dialog">
        <div class="detail-header">
          <div>
            <div class="section-kicker">记录详情</div>
            <h3>心情记录详情</h3>
          </div>

          <div class="detail-actions">
            <button class="soft-btn" @click="startEdit(selectedMood)">编辑</button>
            <button class="plain-btn" @click="closeDetail">关闭</button>
          </div>
        </div>

        <div class="detail-meta">
          <span class="mood-tag" :class="getMoodClass(selectedMood.moodType)">
            {{ selectedMood.moodType }}
          </span>
          <span>创建于 {{ formatTime(selectedMood.createTime) }}</span>
          <span>更新于 {{ formatTime(selectedMood.updateTime) }}</span>
        </div>

        <div class="detail-block">
          <h4>心情内容</h4>
          <p>{{ selectedMood.content }}</p>
        </div>

        <div class="detail-block ai-detail">
          <h4>心屿回复</h4>
          <p>{{ selectedMood.aiReply || '暂无心屿回复' }}</p>
        </div>

        <div v-if="editingMoodId === selectedMood.id" class="edit-box">
          <h4>编辑心情记录</h4>

          <select v-model="editMoodType">
            <option value="">请选择心情类型</option>
            <option value="开心">开心</option>
            <option value="平静">平静</option>
            <option value="焦虑">焦虑</option>
            <option value="难过">难过</option>
            <option value="疲惫">疲惫</option>
            <option value="其他">其他</option>
          </select>

          <textarea
            v-model="editContent"
            placeholder="请输入修改后的心情内容"
          ></textarea>

          <div class="edit-actions">
            <button class="primary-btn" :disabled="updating" @click="updateMood">
              {{ updating ? '心屿正在重新生成回复...' : '保存修改' }}
            </button>
            <button class="plain-btn" @click="cancelEdit">取消</button>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 32px;
  color: #243044;
  background:
    linear-gradient(135deg, rgba(89, 126, 247, 0.12), rgba(88, 166, 137, 0.1)),
    #f6f8fc;
}

.header,
.main {
  max-width: 1280px;
  margin: 0 auto;
}

.header {
  margin-bottom: 24px;
  padding: 24px 28px;
  border: 1px solid rgba(90, 110, 150, 0.12);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18px 40px rgba(46, 64, 100, 0.08);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand,
.user-area,
.record-top,
.record-actions,
.detail-actions,
.edit-actions {
  display: flex;
  align-items: center;
}

.brand {
  gap: 14px;
}

.brand-mark {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: grid;
  place-items: center;
  background: #4f72e8;
  color: #fff;
  font-size: 22px;
  font-weight: 700;
}

.header h1,
.panel h2,
.detail-dialog h3 {
  margin: 0;
  color: #182235;
  letter-spacing: 0;
}

.header h1 {
  font-size: 32px;
}

.header p,
.panel-desc {
  margin: 6px 0 0;
  color: #6f7787;
  line-height: 1.6;
}

.user-area {
  gap: 14px;
  color: #4b5568;
}

.main {
  display: grid;
  grid-template-columns: 390px minmax(0, 1fr);
  gap: 24px;
}

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

.list-panel {
  padding: 28px;
  min-width: 0;
}

.section-kicker {
  margin-bottom: 8px;
  color: #4f72e8;
  font-size: 13px;
  font-weight: 700;
}

.panel h2 {
  font-size: 28px;
}

.form {
  margin-top: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

input,
select,
textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #d8deea;
  border-radius: 8px;
  padding: 12px 14px;
  outline: none;
  background: #fff;
  color: #1f2937;
  font-size: 15px;
}

input:focus,
select:focus,
textarea:focus {
  border-color: #4f72e8;
  box-shadow: 0 0 0 3px rgba(79, 114, 232, 0.12);
}

textarea {
  min-height: 180px;
  resize: vertical;
  line-height: 1.7;
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
.ghost-btn,
.plain-btn,
.page-btn,
.detail-btn,
.delete-btn {
  height: 40px;
  border: none;
  border-radius: 8px;
  padding: 0 16px;
  white-space: nowrap;
}

.primary-btn {
  background: #4f72e8;
  color: #fff;
  font-weight: 700;
}

.primary-btn:hover:not(:disabled) {
  background: #3f61d3;
}

.soft-btn,
.ghost-btn,
.page-btn,
.detail-btn {
  background: #eef3ff;
  color: #3f61d3;
}

.soft-btn:hover,
.ghost-btn:hover,
.page-btn:hover,
.detail-btn:hover {
  background: #e0e9ff;
}

.plain-btn {
  background: #f1f3f6;
  color: #596273;
}

.plain-btn:hover {
  background: #e6e9ef;
}

.delete-btn {
  background: #fff1f1;
  color: #c95151;
}

.delete-btn:hover {
  background: #ffe3e3;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.filter-area {
  margin: 22px 0;
  display: grid;
  grid-template-columns: 150px 170px 170px auto auto;
  gap: 10px;
  align-items: center;
}

.empty {
  padding: 52px 20px;
  border: 1px dashed #ccd5e7;
  border-radius: 8px;
  background: #f8faff;
  text-align: center;
}

.empty-title {
  margin-bottom: 8px;
  color: #344054;
  font-size: 17px;
  font-weight: 700;
}

.empty-desc {
  color: #7a8494;
}

.record-list {
  display: grid;
  gap: 12px;
}

.record-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  padding: 18px;
  border: 1px solid #e5ebf5;
  border-radius: 8px;
  background: #fbfcff;
}

.record-main {
  min-width: 0;
}

.record-top {
  justify-content: space-between;
  gap: 12px;
}

.record-actions {
  gap: 8px;
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
  color: #946200;
}

.mood-calm {
  background: #e6f5ed;
  color: #28724d;
}

.mood-anxious {
  background: #fff0e4;
  color: #b65f18;
}

.mood-sad {
  background: #e9eef7;
  color: #50627d;
}

.mood-default {
  background: #eef3ff;
  color: #3f61d3;
}

.record-content {
  margin: 12px 0;
  color: #263244;
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.record-time {
  color: #8a94a6;
  font-size: 13px;
  white-space: nowrap;
}

.ai-reply-box {
  padding: 12px 14px;
  border-left: 3px solid #4f72e8;
  border-radius: 8px;
  background: #f2f6ff;
}

.ai-reply-title {
  margin-bottom: 4px;
  color: #3f61d3;
  font-size: 13px;
  font-weight: 700;
}

.ai-reply-content {
  margin: 0;
  color: #566174;
  line-height: 1.7;
  display: -webkit-box;
  overflow: hidden;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  color: #667085;
  font-size: 14px;
}

.message {
  max-width: 1280px;
  margin: 0 auto 16px;
  padding: 12px 16px;
  border-radius: 8px;
  font-size: 14px;
}

.message.success {
  background: #e6f5ed;
  color: #28724d;
}

.message.error {
  background: #fff1f1;
  color: #c95151;
}

.message.warning {
  background: #fff4dc;
  color: #946200;
}

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
  width: min(760px, 100%);
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

.detail-meta {
  margin: 18px 0;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px 14px;
  color: #7a8494;
  font-size: 14px;
}

.detail-block {
  margin-top: 14px;
  padding: 16px;
  border: 1px solid #e5ebf5;
  border-radius: 8px;
  background: #fbfcff;
}

.detail-block h4,
.edit-box h4 {
  margin: 0 0 10px;
  color: #263244;
  font-size: 16px;
}

.detail-block p {
  margin: 0;
  color: #344054;
  line-height: 1.8;
}

.ai-detail {
  border-left: 3px solid #4f72e8;
  background: #f2f6ff;
}

.edit-box {
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #dfe6f2;
  border-radius: 8px;
  display: grid;
  gap: 12px;
}

@media (max-width: 980px) {
  .home-page {
    padding: 18px;
  }

  .header {
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .main {
    grid-template-columns: 1fr;
  }

  .add-panel {
    position: static;
  }

  .filter-area {
    grid-template-columns: 1fr 1fr;
  }

  .record-card {
    grid-template-columns: 1fr;
  }

  .record-actions {
    justify-content: flex-end;
  }
}

@media (max-width: 560px) {
  .filter-area {
    grid-template-columns: 1fr;
  }

  .detail-overlay {
    padding: 14px;
  }

  .detail-header {
    flex-direction: column;
  }

  .user-area {
    width: 100%;
    justify-content: space-between;
  }
}
</style>
