// 回顾页统计 store：只负责拉取周报/近 30 天的只读统计。
// 报告生成与轮询在后续步骤接入，这里不创建任务、不调用模型。
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { moodApi } from '../api'

export const useReviewStore = defineStore('review', () => {
  const weekStats = ref(null)
  const recent30Stats = ref(null)
  const weekLoading = ref(false)
  const recent30Loading = ref(false)

  async function fetchWeek(start = '') {
    weekLoading.value = true
    try {
      const res = await moodApi.reviewWeek(start)
      if (res.ok) {
        weekStats.value = res.data
      }
      return res
    } finally {
      weekLoading.value = false
    }
  }

  async function fetchRecent30() {
    recent30Loading.value = true
    try {
      const res = await moodApi.reviewRecent30()
      if (res.ok) {
        recent30Stats.value = res.data
      }
      return res
    } finally {
      recent30Loading.value = false
    }
  }

  return {
    weekStats,
    recent30Stats,
    weekLoading,
    recent30Loading,
    fetchWeek,
    fetchRecent30
  }
})
