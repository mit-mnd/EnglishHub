<script setup>
import { onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import NotificationItem from '@/components/notification/NotificationItem.vue'
import { notificationApi } from '@/api'
import { useNotificationStore } from '@/stores/notifications'

const store = useNotificationStore()
const router = useRouter()

const filter = ref('all')
const page = reactive({ number: 1, size: 15, total: 0 })
const items = ref([])
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const res = await notificationApi.list({
      page: page.number - 1, size: page.size, unreadOnly: filter.value === 'unread' || undefined,
    })
    items.value = res.items
    page.total = res.totalElements
    store.refreshCount()
  } catch {
    ElMessage.error('Không tải được thông báo')
  } finally {
    loading.value = false
  }
}

async function open(notification) {
  await store.markRead(notification).catch(() => {})
  if (notification.link) router.push(notification.link)
}

async function remove(notification) {
  try {
    await store.remove(notification)
    // Trang hiện tại trống sau khi xóa -> lùi một trang
    if (items.value.length === 1 && page.number > 1) page.number--
    await load()
  } catch {
    ElMessage.error('Không thể xóa thông báo')
  }
}

async function readAll() {
  try {
    await store.markAllRead()
    await load()
  } catch {
    ElMessage.error('Không thể đánh dấu đã đọc')
  }
}

watch(filter, () => {
  page.number = 1
  load()
})
onMounted(load)
</script>

<template>
  <div class="eh-page notifications">
    <div class="notifications__bar">
      <el-radio-group v-model="filter">
        <el-radio-button value="all">Tất cả</el-radio-button>
        <el-radio-button value="unread">Chưa đọc{{ store.unread ? ` (${store.unread})` : '' }}</el-radio-button>
      </el-radio-group>
      <el-button :disabled="!store.unread" @click="readAll">Đánh dấu tất cả đã đọc</el-button>
    </div>

    <el-card shadow="never" class="notifications__card">
      <div v-loading="loading" class="notifications__list">
        <NotificationItem v-for="n in items" :key="n.id" :notification="n" @open="open" @remove="remove" />
        <el-empty v-if="!loading && !items.length"
                  :description="filter === 'unread' ? 'Bạn đã đọc hết thông báo' : 'Chưa có thông báo nào'" />
      </div>
      <el-pagination v-if="page.total > page.size" v-model:current-page="page.number" class="notifications__pager"
                     :page-size="page.size" :total="page.total" layout="prev, pager, next" @current-change="load" />
    </el-card>

    <p class="eh-subtle">
      Nhắc học hằng ngày được gửi theo giờ bạn chọn trong
      <RouterLink :to="{ name: 'profile' }">Tài khoản</RouterLink> nếu hôm đó bạn chưa đạt mục tiêu.
    </p>
  </div>
</template>

<style scoped>
.notifications { display: grid; gap: 16px; max-width: 760px; }
.notifications__bar { display: flex; flex-wrap: wrap; gap: 12px; justify-content: space-between; align-items: center; }
.notifications__list { display: grid; gap: 6px; min-height: 120px; }
.notifications__pager { justify-content: center; margin-top: 12px; }
</style>
