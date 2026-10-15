<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Bell } from '@element-plus/icons-vue'
import NotificationItem from './NotificationItem.vue'
import { useNotificationStore } from '@/stores/notifications'

const store = useNotificationStore()
const router = useRouter()
const visible = ref(false)

function onShow() {
  store.loadRecent().catch(() => ElMessage.error('Không tải được thông báo'))
}

/** Bấm vào thông báo: đánh dấu đã đọc rồi mở trang liên quan (nếu có). */
async function open(notification) {
  visible.value = false
  store.markRead(notification).catch(() => {})
  if (notification.link) router.push(notification.link)
}

async function readAll() {
  try {
    await store.markAllRead()
  } catch {
    ElMessage.error('Không thể đánh dấu đã đọc')
  }
}

function viewAll() {
  visible.value = false
  router.push({ name: 'notifications' })
}
</script>

<template>
  <el-popover v-model:visible="visible" placement="bottom-end" :width="360" trigger="click"
              popper-class="noti-popper" @show="onShow">
    <template #reference>
      <button class="icon-btn" type="button" :aria-label="`Thông báo (${store.unread} chưa đọc)`">
        <el-badge :value="store.unread" :hidden="!store.unread" :max="9">
          <el-icon :size="20"><Bell /></el-icon>
        </el-badge>
      </button>
    </template>

    <div class="bell">
      <div class="bell__head">
        <strong>Thông báo</strong>
        <el-button v-if="store.unread" link type="primary" size="small" @click="readAll">Đánh dấu tất cả đã đọc</el-button>
      </div>

      <div v-loading="store.loadingRecent && !store.recent.length" class="bell__list">
        <NotificationItem v-for="n in store.recent" :key="n.id" :notification="n" compact @open="open" />
        <el-empty v-if="!store.loadingRecent && !store.recent.length" :image-size="70" description="Chưa có thông báo nào" />
      </div>

      <el-button class="bell__all" text type="primary" @click="viewAll">Xem tất cả thông báo</el-button>
    </div>
  </el-popover>
</template>

<style scoped>
.icon-btn {
  display: inline-grid; place-items: center; width: 40px; height: 40px;
  border: 0; border-radius: 50%; background: transparent; color: var(--eh-text-2); cursor: pointer;
}
.icon-btn:hover { background: var(--eh-surface); color: var(--eh-text); }

.bell { display: grid; gap: 6px; }
.bell__head { display: flex; align-items: center; justify-content: space-between; padding: 2px 4px 6px; }
.bell__list { display: grid; gap: 4px; max-height: min(420px, 60dvh); overflow-y: auto; min-height: 80px; }
.bell__all { width: 100%; }
</style>

<style>
/* Màn hình hẹp: popover không vượt khỏi viewport */
.noti-popper { max-width: calc(100vw - 16px); }
</style>
