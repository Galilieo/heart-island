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
const selectedMood =ref(null)

const adding =ref(false)

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

  if (startDate.value && endDate.value && startDate.value > endDate.value) {
    showMessage('开始日期不能晚于结束日期', 'warning')
    return
  }

  request.get('/mood/list', {
    params: {
      moodType: filterMoodType.value,
      startDate: startDate.value,
      endDate: endDate.value,
      pageNum: pageNum.value,
      pageSize:pageSize.value
    }
  })
    .then(res => {
      console.log('心情记录列表：', res.data)

      if (res.data.code === 200) {
        const pageData = res.data.data

        moodList.value = pageData.records
        total.value = pageData.total
        pages.value = pageData.pages
        pageNum.value = pageData.current
        pageSize.value=pageData.size
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

function loadMoodDetail(id) {
  request.get('/mood/detail/' + id)
    .then(res => {
      console.log('心情详情：', res.data)

      if (res.data.code === 200) {
        selectedMood.value = res.data.data
        showMessage('详情加载成功', 'success')
      } else {
        showMessage(res.data.message, 'error')
      }
    })
    .catch(err => {
      console.log('查询详情失败：', err)
      showMessage('查询详情失败，请检查后端是否启动','error')
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
  editContent.value=''
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

  request.put('/mood/update/' + editingMoodId.value,{
    moodType: editMoodType.value,
    content:editContent.value.trim()
  })
    .then(res => {
      console.log('修改心情返回结果', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message, 'success')

        const updatedId = editingMoodId.value

        cancelEdit()
        loadMoodList()

        if (selectedMood.value && selectedMood.value.id === updatedId) {
          loadMoodDetail(updatedId)
        }
      }else {
        showMessage(res.data.message, 'error')
      }
    }).catch(err => {
      console.log('修改心情失败：', err)
      showMessage('修改失败，请检查后端是否启动', 'error')
    }).finally(() => {
      updating.value = false
    })
}
function resetFilter() {
  filterMoodType.value = ''
  startDate.value = ''
  endDate.value = ''
  pageNum.value=1
  loadMoodList()
}
function searchMoodList(){
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
          <div class="list-info">
            <h2>我的心情记录</h2>
            <p class="panel-desc">查看你最近记录下来的情绪变化。</p>
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
            
            <input v-model="startDate" type="date"/>
            <input v-model="endDate" type="date"/>

            <button class="refresh-btn" @click="searchMoodList">查询</button>
            <button class="reset-btn" @click="resetFilter">重置</button>
            <button class="refresh-btn" @click="loadMoodList">刷新</button>
          </div>
        </div>
        <div v-if="selectedMood" class="detail-box">
          <div class="detail-header">
            <h3>心情记录详情</h3>
            <div class="detail-actions">
              <button class="edit-btn" @click="startEdit(selectedMood)">编辑</button>
              <button class="close-btn" @click="closeDetail">关闭</button>
            </div>
            
          </div>

          <p>
            <strong>心情类型：</strong>
            <span class="mood-tag" :class="getMoodClass(selectedMood.moodType)">
              {{ selectedMood.moodType }}
            </span>
          </p>

          <p>
            <strong>心情内容：</strong>
            {{ selectedMood.content }}
          </p>

          <p>
            <strong>创建时间：</strong>
            {{ formatTime(selectedMood.createTime) }}
          </p>

          <p>
            <strong>更新时间：</strong>
            {{ formatTime(selectedMood.updateTime) }}
          </p>
          
          <div v-if="editingMoodId===selectedMood.id" class="edit-box">
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

            <textarea v-model="editContent" placeholder="请输入修改后的心情内容"></textarea>

            <div class="edit-actions">
              <button class="primary-btn" :disabled="updating" @click="updateMood">
                {{ updating ? '保存中...' : '保存修改' }}
              </button>
              <button class="cancel-btn" @click="cancelEdit">取消</button>
            </div>
          </div>
        </div>
        <div v-if="moodList.length === 0" class="empty">
          <div class="empty-title">暂无心情记录</div>
          <div class="empty-desc">写下第一条记录，给今天的情绪一个出口。</div>
        </div>

        <div v-else class="record-list">
          <div v-for="item in moodList" :key="item.id" class="record-card">
            <div class="record-top">
              <span class="mood-tag" :class="getMoodClass(item.moodType)">{{ item.moodType }}</span>
              <div class="record-actions">
                <button class="detail-btn" @click="loadMoodDetail(item.id)">查看详情</button>
                <button class="delete-btn" @click="deleteMood(item.id)">删除</button>
              </div>
            </div>

            <p class="record-content">{{ item.content }}</p>
            <p class="record-time">{{ formatTime(item.createTime) }}</p>
          </div>
        </div>
          <div v-if="total > 0" class="pagination">
            <button class="page-btn" :disabled="pageNum <= 1" @click="prevPage">
              上一页
            </button>

            <span>
              第 {{ pageNum }} / {{ pages }} 页，共 {{ total }} 条
            </span>

            <button class="page-btn" :disabled="pageNum >= pages" @click="nextPage">
              下一页
            </button>
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
  flex-wrap: wrap;
}

.list-info {
  flex: 1;
  min-width: 220px;
}

.list-info h2 {
  margin: 0;
  white-space: nowrap;
}

.list-info .panel-desc {
  margin: 8px 0 0;
  color: #888;
  font-size: 14px;
  line-height: 1.6;
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

.record-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.detail-btn {
  border: none;
  background: transparent;
  color: #4b6cb7;
  font-size: 14px;
}

.detail-btn:hover {
  color: #2f55b7;
}

.detail-box {
  margin: 18px 0;
  padding: 18px;
  border-radius: 14px;
  background: #f8f9ff;
  border: 1px solid #edf0ff;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-header h3 {
  margin: 0;
  font-size: 18px;
  color: #333;
}

.close-btn {
  border: none;
  background: #eef2ff;
  color: #4b6cb7;
  border-radius: 8px;
  padding: 6px 12px;
}

.close-btn:hover {
  background: #e0e7ff;
}

.detail-box p {
  margin: 10px 0;
  color: #444;
  line-height: 1.7;
}

.detail-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.edit-btn {
  border: none;
  background: #eef2ff;
  color: #4b6cb7;
  border-radius: 8px;
  padding: 6px 12px;
}

.edit-btn:hover {
  background: #e0e7ff;
}

.edit-box {
  margin-top: 18px;
  padding: 16px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #edf0ff;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.edit-box h4 {
  margin: 0;
  color: #333;
  font-size: 16px;
}

.edit-actions {
  display: flex;
  gap: 12px;
}

.cancel-btn {
  height: 42px;
  padding: 0 16px;
  border: none;
  border-radius: 10px;
  background: #f2f4ff;
  color: #4b6cb7;
}

.cancel-btn:hover {
  background: #e4e8ff;
}

.filter-area {
  display: flex;
  gap: 10px;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  flex: 0 0 auto;
}

.filter-area select,
.filter-area input {
  height: 36px;
  padding: 0 12px;
  border: 1px solid #dcdfe6;
  border-radius: 9px;
  background: #fff;
  color: #333;
  outline: none;
}

.reset-btn {
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 9px;
  background: #f5f5f5;
  color: #666;
}

.reset-btn:hover {
  background: #eaeaea;
}

.pagination {
  margin-top: 18px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  color: #666;
  font-size: 14px;
}

.page-btn {
  height: 34px;
  padding: 0 14px;
  border: none;
  border-radius: 8px;
  background: #f2f4ff;
  color: #4b6cb7;
}

.page-btn:hover {
  background: #e4e8ff;
}

.page-btn:disabled {
  background: #f1f1f1;
  color: #aaa;
  cursor: not-allowed;
}
</style>
