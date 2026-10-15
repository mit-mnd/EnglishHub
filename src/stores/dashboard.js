import { defineStore } from 'pinia'
import { ref } from 'vue'
import { dashboardApi } from '@/api'

/** Số liệu dashboard; header (streak, chuông thông báo) và trang Tổng quan dùng chung. */
export const useDashboardStore = defineStore('dashboard', () => {
  const data = ref(null)
  const range = ref('7d')
  const loading = ref(false)
  const error = ref(null)

  // Layout và trang Tổng quan cùng gọi load() khi mở trang -> dùng chung 1 request đang chạy
  let inFlight = null
  let inFlightRange = null

  function load(newRange = range.value) {
    if (inFlight && inFlightRange === newRange) return inFlight
    inFlightRange = newRange
    loading.value = true
    error.value = null
    const request = dashboardApi.get(newRange)
      .then((res) => {
        if (inFlight === request) {
          data.value = res
          range.value = newRange
        }
      })
      .catch((e) => {
        if (inFlight === request) error.value = e
      })
      .finally(() => {
        if (inFlight === request) {
          loading.value = false
          inFlight = null
        }
      })
    inFlight = request
    return request
  }

  function reset() {
    data.value = null
    range.value = '7d'
    inFlight = null
  }

  return { data, range, loading, error, load, reset }
})
