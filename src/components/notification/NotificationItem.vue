<script setup>
import { computed } from 'vue'
import { AlarmClock, Bell, Collection, Delete, Guide, Medal, Warning } from '@element-plus/icons-vue'
import { formatDateTime, formatRelative } from '@/utils/format'

const props = defineProps({
  notification: { type: Object, required: true },
  compact: { type: Boolean, default: false },
})
const emit = defineEmits(['open', 'remove'])

const TYPE_STYLE = {
  STUDY_REMINDER: { icon: AlarmClock, color: 'color-mix(in srgb, #4f46e5 72%, var(--eh-text))', bg: 'color-mix(in srgb, #4f46e5 14%, transparent)' },
  REVIEW_DUE: { icon: Collection, color: 'color-mix(in srgb, #0891b2 72%, var(--eh-text))', bg: 'color-mix(in srgb, #0891b2 14%, transparent)' },
  STREAK: { icon: Warning, color: 'color-mix(in srgb, #d97706 72%, var(--eh-text))', bg: 'color-mix(in srgb, #d97706 14%, transparent)' },
  ACHIEVEMENT: { icon: Medal, color: 'color-mix(in srgb, #059669 72%, var(--eh-text))', bg: 'color-mix(in srgb, #059669 14%, transparent)' },
  PATH_UPDATED: { icon: Guide, color: 'color-mix(in srgb, #7c3aed 72%, var(--eh-text))', bg: 'color-mix(in srgb, #7c3aed 14%, transparent)' },
  SYSTEM: { icon: Bell, color: 'color-mix(in srgb, #475569 72%, var(--eh-text))', bg: 'color-mix(in srgb, #475569 14%, transparent)' },
}
const style = computed(() => TYPE_STYLE[props.notification.type] ?? TYPE_STYLE.SYSTEM)
</script>

<template>
  <div class="noti" :class="{ 'noti--unread': !notification.read, 'noti--compact': compact }">
    <button type="button" class="noti__main" @click="emit('open', notification)">
      <span class="noti__icon" :style="{ color: style.color, background: style.bg }">
        <el-icon :size="18"><component :is="style.icon" /></el-icon>
      </span>
      <span class="noti__body">
        <span class="noti__title">{{ notification.title }}</span>
        <span class="noti__message">{{ notification.message }}</span>
        <span class="noti__time" :title="formatDateTime(notification.createdAt)">{{ formatRelative(notification.createdAt) }}</span>
      </span>
      <span v-if="!notification.read" class="noti__dot" aria-label="Chưa đọc" />
    </button>
    <el-tooltip v-if="!compact" content="Xóa thông báo">
      <el-button class="noti__remove" :icon="Delete" circle text aria-label="Xóa thông báo" @click="emit('remove', notification)" />
    </el-tooltip>
  </div>
</template>

<style scoped>
.noti { display: flex; align-items: flex-start; gap: 4px; border-radius: 10px; }
.noti--unread { background: var(--eh-primary-soft); }
.noti__main {
  flex: 1; min-width: 0; display: flex; gap: 12px; align-items: flex-start;
  padding: 12px; border: 0; background: transparent; border-radius: 10px;
  text-align: left; font: inherit; color: inherit; cursor: pointer;
}
.noti__main:hover { background: var(--eh-primary-soft); }
.noti__icon { flex-shrink: 0; display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; }
.noti__body { flex: 1; min-width: 0; display: grid; gap: 2px; }
.noti__title { font-weight: 600; font-size: 14px; }
.noti__message { font-size: 13px; color: var(--eh-text-2); line-height: 1.45; }
.noti--compact .noti__message {
  display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden;
}
.noti__time { font-size: 12px; color: var(--eh-text-3); }
.noti__dot { flex-shrink: 0; width: 8px; height: 8px; margin-top: 6px; border-radius: 50%; background: var(--eh-primary); }
.noti__remove { margin: 10px 6px 0 0; }
.noti--compact .noti__main { padding: 10px; }
</style>
