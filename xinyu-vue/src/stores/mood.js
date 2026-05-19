// 心情 store：列表 / 筛选 / 分页 / 趋势 / 选中详情。
// 关键约定：add/update/remove 成功后会自动刷新列表和趋势，
// view 只需要 await store.add()，不用再手动 reload。
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import { moodApi } from '../api'

export const useMoodStore = defineStore('mood', () => {
  const list = ref([])
  const total = ref(0)
  const pages = ref(0)
  const selected = ref(null)

  const filters = reactive({
    moodType: '',
    startDate: '',
    endDate: ''
  })

  const pagination = reactive({
    pageNum: 1,
    pageSize: 5
  })

  const trend = ref({
    total: 0,
    mainMoodType: '',
    lastMoodType: '',
    lastRecordTime: '',
    moodCounts: []
  })

  // 把 store 内的 filters + pagination 拼成后端能识别的 query 参数
  function buildListParams() {
    return {
      moodType: filters.moodType || undefined,
      startDate: filters.startDate || undefined,
      endDate: filters.endDate || undefined,
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize
    }
  }

  async function fetchList() {
    const res = await moodApi.list(buildListParams())
    if (res.ok && res.data) {
      list.value = res.data.records || res.data.list || []
      total.value = res.data.total ?? list.value.length
      pages.value = res.data.pages ?? 0
    }
    return res
  }

  async function fetchTrend() {
    const res = await moodApi.trendRecent7()
    if (res.ok && res.data) {
      trend.value = { ...trend.value, ...res.data }
    }
    return res
  }

  async function add(payload) {
    const res = await moodApi.create(payload)
    if (res.ok) {
      pagination.pageNum = 1
      await Promise.all([fetchList(), fetchTrend()])
    }
    return res
  }

  async function update(id, payload) {
    const res = await moodApi.update(id, payload)
    if (res.ok) {
      await Promise.all([fetchList(), fetchTrend()])
      // 若详情打开的是这一条，刷新它
      if (selected.value && selected.value.id === id) {
        const refreshed = await moodApi.detail(id)
        if (refreshed.ok) selected.value = refreshed.data
      }
    }
    return res
  }

  async function remove(id) {
    const res = await moodApi.remove(id)
    if (res.ok) {
      if (selected.value && selected.value.id === id) selected.value = null
      await Promise.all([fetchList(), fetchTrend()])
    }
    return res
  }

  async function openDetail(id) {
    const res = await moodApi.detail(id)
    if (res.ok) selected.value = res.data
    return res
  }

  function closeDetail() {
    selected.value = null
  }

  function setFilter(patch) {
    Object.assign(filters, patch)
    pagination.pageNum = 1
  }

  function setPage(pageNum) {
    pagination.pageNum = pageNum
  }

  function resetFilters() {
    filters.moodType = ''
    filters.startDate = ''
    filters.endDate = ''
    pagination.pageNum = 1
  }

  return {
    list,
    total,
    pages,
    selected,
    filters,
    pagination,
    trend,
    fetchList,
    fetchTrend,
    add,
    update,
    remove,
    openDetail,
    closeDetail,
    setFilter,
    setPage,
    resetFilters
  }
})
