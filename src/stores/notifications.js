import { defineStore } from 'pinia'
import { ref } from 'vue'
import { notificationApi } from '@/api'

const POLL_MS = 60_000

/**
 * Thông báo trong ứng dụng: số chưa đọc (chuông) được cập nhật định kỳ 60 giây,
 * tạm dừng khi tab bị ẩn và cập nhật ngay khi người dùng quay lại tab.
 */
export const useNotificationStore = defineStore('notifications', () => {
  const unread = ref(0)
  const recent = ref([])
  const loadingRecent = ref(false)
  let timer = null

  async function refreshCount() {
    try {
      unread.value = (await notificationApi.unreadCount()).count
    } catch {
      /* lỗi mạng tạm thời: giữ số cũ, lần poll sau thử lại */
    }
  }

  async function loadRecent() {
    loadingRecent.value = true
    try {
      recent.value = (await notificationApi.list({ page: 0, size: 6 })).items
      await refreshCount()
    } finally {
      loadingRecent.value = false
    }
  }

  function patchRecent(id, changes) {
    const n = recent.value.find((x) => x.id === id)
    if (n) Object.assign(n, changes)
  }

  /** notification có thể đến từ popover hoặc trang đầy đủ (danh sách riêng) - cập nhật cả hai. */
  async function markRead(notification) {
    if (notification.read) return
    await notificationApi.markRead(notification.id)
    notification.read = true
    patchRecent(notification.id, { read: true })
    unread.value = Math.max(0, unread.value - 1)
  }

  async function markAllRead() {
    await notificationApi.markAllRead()
    recent.value.forEach((n) => { n.read = true })
    unread.value = 0
  }

  async function remove(notification) {
    await notificationApi.remove(notification.id)
    recent.value = recent.value.filter((n) => n.id !== notification.id)
    if (!notification.read) unread.value = Math.max(0, unread.value - 1)
  }

  const onVisibility = () => {
    if (document.visibilityState === 'visible') refreshCount()
  }

  function startPolling() {
    if (timer) return
    refreshCount()
    timer = setInterval(() => {
      if (document.visibilityState === 'visible') refreshCount()
    }, POLL_MS)
    document.addEventListener('visibilitychange', onVisibility)
  }

  function stopPolling() {
    clearInterval(timer)
    timer = null
    document.removeEventListener('visibilitychange', onVisibility)
  }

  function reset() {
    stopPolling()
    unread.value = 0
    recent.value = []
  }

  return { unread, recent, loadingRecent, refreshCount, loadRecent, markRead, markAllRead, remove, startPolling, stopPolling, reset }
})
