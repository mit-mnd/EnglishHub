import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { profileApi } from '@/api'

/**
 * Hồ sơ người học (thay cho phiên đăng nhập). Dữ liệu nằm trong localStorage của trình duyệt,
 * nên đọc đồng bộ ngay khi khởi động - không cần chờ mạng.
 */
export const useProfileStore = defineStore('profile', () => {
  const user = ref(null)
  const loaded = ref(false)

  const hasProfile = computed(() => !!user.value)

  async function load() {
    user.value = await profileApi.get()
    loaded.value = true
    return user.value
  }

  /** Đọc lại hồ sơ (vd. sau khi làm bài xếp lớp - trình độ thay đổi). */
  const refresh = load

  async function ensureLoaded() {
    if (!loaded.value) await load()
  }

  async function create(payload) {
    user.value = await profileApi.create(payload)
    return user.value
  }

  async function update(payload) {
    user.value = await profileApi.update(payload)
    return user.value
  }

  /** Sau khi xóa / khôi phục dữ liệu. */
  function reset() {
    user.value = null
    loaded.value = false
  }

  return { user, loaded, hasProfile, load, refresh, ensureLoaded, create, update, reset }
})
