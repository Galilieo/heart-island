<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import request from '../utils/request'
import MoodCard from '../components/MoodCard.vue'
import MoodDetailDialog from '../components/MoodDetailDialog.vue'
import MoodForm from '../components/MoodForm.vue'
import logoUrl from '../assets/logo-xinyu.png'

const router = useRouter()

const loginUser = ref(null)
const moodType = ref('')
const content = ref('')
const moodList = ref([])

const message = ref('')
const messageType = ref('success')
const messageTimer = ref(null)

const selectedMood = ref(null)
const adding = ref(false)
const listLoading = ref(false)
const trendLoading = ref(false)
const detailLoadingId = ref(null)
const deletingId = ref(null)

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
const moodTrend = ref({
  total: 0,
  mainMoodType: '',
  lastMoodType: '',
  lastRecordTime: '',
  moodCounts: []
})

const moodOptions = [
  { label: '开心', value: '开心' },
  { label: '平静', value: '平静' },
  { label: '焦虑', value: '焦虑' },
  { label: '难过', value: '难过' },
  { label: '疲惫', value: '疲惫' },
  { label: '其他', value: '其他' }
]

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
  loadMoodTrend()
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
        loadMoodTrend()
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

  if (listLoading.value) {
    return
  }

  if (startDate.value && endDate.value && startDate.value > endDate.value) {
    showMessage('开始日期不能晚于结束日期', 'warning')
    return
  }

  listLoading.value = true

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
    .finally(() => {
      listLoading.value=false
    })
}

function deleteMood(id) {
  if (deletingId.value) {
    return
  }

  const ok = confirm('确定要删除这条心情记录吗？')

  if (!ok) {
    return
  }

  deletingId.value = id

  request
    .delete('/mood/delete/' + id)
    .then((res) => {
      console.log('删除返回结果：', res.data)

      if (res.data.code === 200) {
        showMessage(res.data.message || '删除成功', 'success')

        if (selectedMood.value && selectedMood.value.id === id) {
          closeDetail()
        }
        loadMoodList()
        loadMoodTrend()
      } else {
        showMessage(res.data.message||'删除失败', 'error')
      }
    })
    .catch((err) => {
      console.log('删除失败：', err)
      showMessage('删除失败，请检查后端是否启动', 'error')
    })
    .finally(() => {
      deletingId.value = null
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

  if (messageTimer.value) {
    clearTimeout(messageTimer.value)
  }

  messageTimer.value = setTimeout(() => {
    message.value = ''
    messageTimer.value = null
  }, 2200)

}

onBeforeUnmount(() => {
  if (messageTimer.value) {
    clearTimeout(messageTimer.value)
  }
})

function loadMoodTrend() {
  if (!loginUser.value) {
    return
  }

  if (trendLoading.value) {
    return
  }

  trendLoading.value = true

  request
    .get('/mood/trend/recent7')
    .then((res) => {
      if (res.data.code === 200) {
        moodTrend.value = res.data.data || {
          total: 0,
          mainMoodType: '',
          lastMoodType: '',
          lastRecordTime: '',
          moodCounts: []
        }
      } else {
        showMessage(res.data.message || '心情趋势加载失败', 'error')
      }
    })
    .catch((err) => {
      console.log('查询心情趋势失败：', err)
      showMessage('心情趋势加载失败，请检查后端是否启动', 'error')
    })
    .finally(() => {
      trendLoading.value = false
    })
}

function getMoodCountPercent(count) {
  if (!moodTrend.value.total) {
    return '0%'
  }

  return Math.round((count / moodTrend.value.total) * 100) + '%'
}

function loadMoodDetail(id) {
  if (detailLoadingId.value) {
    return
  }

  detailLoadingId.value = id

  request
    .get('/mood/detail/' + id)
    .then((res) => {
      console.log('心情详情：', res.data)

      if (res.data.code === 200) {
        selectedMood.value = res.data.data
      } else {
        showMessage(res.data.message || '详情加载失败', 'error')
      }
    })
    .catch((err) => {
      console.log('查询详情失败：', err)
      showMessage('查询详情失败，请检查后端是否启动', 'error')
    })
    .finally(() => {
      detailLoadingId.value = null
    })
}

function closeDetail() {
  if (updating.value) {
    showMessage('心屿正在更新回复，请稍等一下', 'warning')
    return
  }

  if (editingMoodId.value) {
    const ok = confirm('正在编辑中，确定要放弃这次修改吗？')

    if (!ok) {
      return
    }
  }
  selectedMood.value = null
  resetEditState()
}

function startEdit(mood) {
  editingMoodId.value = mood.id
  editMoodType.value = mood.moodType
  editContent.value = mood.content
}

function cancelEdit() {
  if (updating.value) {
      showMessage('正在保存修改，请稍等一下', 'warning')
      return
    }

    const ok = confirm('确定要取消这次编辑吗？')

    if (!ok) {
      return
    }

    resetEditState()
}

function resetEditState() {
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

        resetEditState()
        loadMoodList()
        loadMoodTrend()

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
        <div class="brand-mark">
          <img :src="logoUrl" alt="心屿 logo" />
        </div>
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
      <MoodForm
        v-model:moodType="moodType"
        v-model:content="content"
        :mood-options="moodOptions"
        :adding="adding"
        @submit="addMood"
      />
      <section class="panel list-panel">
        <div class="list-header">
          <div>
            <div class="section-kicker">记录列表</div>
            <h2>我的心情记录</h2>
            <p class="panel-desc">用更轻的方式回看最近的情绪变化。</p>
          </div>

          <button class="ghost-btn" :disabled="listLoading" @click="loadMoodList">{{ listLoading?'刷新中': '刷新' }}</button>
        </div>

        <div class="trend-panel">
          <div class="trend-summary">
            <div>
              <div class="trend-label">最近 7 天</div>
              <strong>{{ trendLoading ? '读取中' : moodTrend.total }}</strong>
              <span>条记录</span>
            </div>

            <div>
              <div class="trend-label">主要心情</div>
              <strong>{{ moodTrend.mainMoodType || '暂无' }}</strong>
              <span>出现最多</span>
            </div>

            <div>
              <div class="trend-label">最近一次</div>
              <strong>{{ moodTrend.lastMoodType || '暂无' }}</strong>
              <span>{{ formatTime(moodTrend.lastRecordTime) || '还没有记录' }}</span>
            </div>
          </div>

          <div v-if="trendLoading" class="trend-empty">正在读取最近的情绪变化...</div>
          <div v-else-if="!moodTrend.moodCounts.length" class="trend-empty">
            最近 7 天还没有记录，写下一条后这里会慢慢有变化。
          </div>
          <div v-else class="trend-bars">
            <div v-for="item in moodTrend.moodCounts" :key="item.moodType" class="trend-row">
              <div class="trend-row-top">
                <span>{{ item.moodType }}</span>
                <span>{{ item.count }} 次</span>
              </div>
              <div class="trend-track">
                <div class="trend-fill" :style="{ width: getMoodCountPercent(item.count) }"></div>
              </div>
            </div>
          </div>
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

          <button class="soft-btn" :disabled="listLoading" @click="searchMoodList">{{ listLoading ? '查询中' : '查询' }}</button>
          <button class="plain-btn" :disabled="listLoading" @click="resetFilter">重置</button>
        </div>

        <div v-if="listLoading" class="state-box">
          <div class="state-title">正在整理你的心情记录</div>
          <div class="state-desc">稍等一下，心屿正在读取最近的记录。</div>
        </div>

        <div v-else-if="moodList.length === 0" class="empty">
          <div class="empty-title">这里还很安静</div>
          <div class="empty-desc">写下第一条心情记录，心屿会陪你慢慢整理。</div>
        </div>

        <div v-else class="record-list">
          <MoodCard
            v-for="item in moodList"
            :key="item.id"
            :mood="item"
            :mood-class="getMoodClass(item.moodType)"
            :formatted-time="formatTime(item.createTime)"
            :deleting="deletingId === item.id" 
            :detail-loading="detailLoadingId===item.id"
            @detail="loadMoodDetail"
            @delete="deleteMood"
          />
        </div>

        <div v-if="total > 0" class="pagination">
          <button class="page-btn" :disabled="listLoading || pageNum <= 1" @click="prevPage">
            上一页
          </button>

          <span class="page-info">第 {{ pageNum }} / {{ pages }} 页，共 {{ total }} 条</span>

          <button class="page-btn" :disabled="listLoading || pageNum >= pages" @click="nextPage">
            下一页
          </button>
        </div>
      </section>
    </main>

    <MoodDetailDialog
      v-if="selectedMood"
      v-model:editMoodType="editMoodType"
      v-model:editContent="editContent"
      :mood="selectedMood"
      :mood-options="moodOptions"
      :editing="editingMoodId === selectedMood.id"
      :updating="updating"
      :mood-class="getMoodClass(selectedMood.moodType)"
      :format-time="formatTime"
      @close="closeDetail"
      @start-edit="startEdit"
      @update="updateMood"
      @cancel-edit="cancelEdit"
    />
  </div>
</template>

<style scoped>
.home-page {
  min-height: 100vh;
  padding: 32px;
  color: #2f3a3d;
  background:
    radial-gradient(circle at 12% 16%, rgba(95, 158, 160, 0.18), transparent 28%),
    radial-gradient(circle at 86% 10%, rgba(238, 234, 248, 0.78), transparent 28%),
    radial-gradient(circle at 72% 84%, rgba(216, 169, 109, 0.12), transparent 26%),
    linear-gradient(135deg, #faf8f3 0%, #f6f3ea 48%, #fffdf8 100%);
  background-size: 120% 120%;
  animation: ambientShift 20s ease-in-out infinite alternate;
}

.header,
.main {
  max-width: 1280px;
  margin: 0 auto;
}

.header {
  margin-bottom: 22px;
  padding: 18px 4px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand,
.user-area,
.edit-actions {
  display: flex;
  align-items: center;
}

.brand {
  gap: 14px;
}

.brand-mark {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 12px 28px rgba(95, 158, 160, 0.24);
}

.brand-mark img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
}

.header h1,
.panel h2 {
  margin: 0;
  color: #2f3a3d;
  letter-spacing: 0;
}

.header h1 {
  font-size: 28px;
}

.header p,
.panel-desc {
  margin: 6px 0 0;
  color: #778184;
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

.list-panel {
  padding: 28px;
  min-width: 0;
}

.section-kicker {
  margin-bottom: 8px;
  color: #3f7378;
  font-size: 13px;
  font-weight: 700;
}

.panel h2 {
  font-size: 28px;
}

input,
select,
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
}

input:focus,
select:focus,
textarea:focus {
  border-color: #5f9ea0;
  box-shadow: 0 0 0 3px rgba(95, 158, 160, 0.14);
}

button {
  cursor: pointer;
  font-size: 14px;
  transition: background 0.18s ease, color 0.18s ease, transform 0.18s ease;
}

button:hover:not(:disabled) {
  transform: translateY(-1px);
}

textarea:disabled,
select:disabled,
input:disabled {
  cursor: not-allowed;
  background: #f4f6fa;
  color: #98a2b3;
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
  background: linear-gradient(135deg, #4f8f91, #6caeb0);
  color: #fff;
  font-weight: 700;
  box-shadow: 0 10px 22px rgba(95, 158, 160, 0.2);
}

.primary-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, #3f7378, #5f9ea0);
}

.soft-btn,
.ghost-btn,
.page-btn,
.detail-btn {
  background: #e8f1ef;
  color: #3f7378;
}

.soft-btn:hover,
.ghost-btn:hover,
.page-btn:hover,
.detail-btn:hover {
  background: #dbe9e7;
}

.plain-btn {
  background: #f1f3f6;
  color: #596273;
}

.plain-btn:hover {
  background: #e6e9ef;
}

.delete-btn {
  background: transparent;
  color: #b85b5b;
}

.delete-btn:hover {
  background: #fff1f1;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.filter-area {
  margin: 22px 0;
  padding: 12px;
  display: grid;
  grid-template-columns: 150px minmax(150px, 1fr) minmax(150px, 1fr) auto auto;
  gap: 10px;
  align-items: center;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: #fffdf8;
}

.trend-panel {
  margin-top: 22px;
  padding: 16px;
  border: 1px solid #e6e1d8;
  border-radius: 8px;
  background: linear-gradient(135deg, #fffdf8, #f7fbfa);
}

.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.trend-summary > div {
  padding: 12px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.78);
}

.trend-label {
  margin-bottom: 6px;
  color: #7b817c;
  font-size: 12px;
}

.trend-summary strong {
  display: block;
  color: #2f3a3d;
  font-size: 22px;
}

.trend-summary span {
  color: #7b817c;
  font-size: 13px;
}

.trend-empty {
  margin-top: 14px;
  color: #7b817c;
  font-size: 14px;
}

.trend-bars {
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.trend-row-top {
  margin-bottom: 6px;
  display: flex;
  justify-content: space-between;
  color: #4f5d5f;
  font-size: 13px;
}

.trend-track {
  height: 8px;
  overflow: hidden;
  border-radius: 999px;
  background: #edf0ec;
}

.trend-fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #5f9ea0, #9b8ac7);
}


.filter-area select,
.filter-area input {
  height: 40px;
  padding-top: 0;
  padding-bottom: 0;
}

.empty {
  padding: 56px 24px;
  border: 1px dashed #e2d9ca;
  border-radius: 8px;
  background: linear-gradient(180deg, #fffdf8, #f7f3ea);
  text-align: center;
}

.state-box {
  padding: 56px 24px;
  border: 1px dashed #d9e5e2;
  border-radius: 8px;
  background: linear-gradient(180deg, #ffffff, #f4faf8);
  text-align: center;
}

.state-title {
  margin-bottom: 8px;
  color: #354244;
  font-size: 17px;
  font-weight: 700;
}

.state-desc {
  color: #7b817c;
}

.empty-title {
  margin-bottom: 8px;
  color: #354244;
  font-size: 17px;
  font-weight: 700;
}

.empty-desc {
  color: #7b817c;
}

.record-list {
  display: grid;
  gap: 12px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  color: #778184;
  font-size: 14px;
}

.page-info {
  min-width: 150px;
  text-align: center;
  color: #7b817c;
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

  .filter-area {
    grid-template-columns: 1fr 1fr;
  }

  .trend-summary {
    grid-template-columns: 1fr;
  }

  .filter-area .soft-btn,
  .filter-area .plain-btn {
    width: 100%;
  }
}

@media (max-width: 560px) {
  .filter-area {
    grid-template-columns: 1fr;
  }

  .user-area {
    width: 100%;
    justify-content: space-between;
  }

  .pagination {
    flex-wrap: wrap;
  }

  .page-info {
    order: -1;
    width: 100%;
  }
}

@keyframes ambientShift {
  0% {
    background-position: 0% 0%;
  }

  100% {
    background-position: 100% 70%;
  }
}
</style>
