<script setup>
import { useRouter } from 'vue-router'
import { ArrowDown, Bell, Download, User } from '@element-plus/icons-vue'
import { useProfileStore } from '@/stores/profile'
import { initials } from '@/utils/format'

const learner = useProfileStore()
const router = useRouter()

function onCommand(command) {
  if (command === 'profile') router.push({ name: 'profile' })
  if (command === 'notifications') router.push({ name: 'notifications' })
  if (command === 'backup') router.push({ name: 'profile', hash: '#data' })
}
</script>

<template>
  <el-dropdown trigger="click" placement="bottom-end" @command="onCommand">
    <button class="user-menu" type="button" :aria-label="`Hồ sơ ${learner.user?.fullName ?? ''}`">
      <el-avatar :size="36" class="user-menu__avatar">{{ initials(learner.user?.fullName) }}</el-avatar>
      <span class="user-menu__name">{{ learner.user?.fullName }}</span>
      <el-icon class="user-menu__caret"><ArrowDown /></el-icon>
    </button>
    <template #dropdown>
      <el-dropdown-menu>
        <div class="user-menu__header">
          <strong>{{ learner.user?.fullName }}</strong>
          <span>{{ learner.user?.currentLevel?.name ?? 'Chưa xếp lớp' }}</span>
        </div>
        <el-dropdown-item command="profile" :icon="User">Hồ sơ của tôi</el-dropdown-item>
        <el-dropdown-item command="notifications" :icon="Bell">Thông báo</el-dropdown-item>
        <el-dropdown-item divided command="backup" :icon="Download">Sao lưu dữ liệu</el-dropdown-item>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<style scoped>
.user-menu {
  display: flex; align-items: center; gap: 10px;
  padding: 4px 8px 4px 4px; border: 0; border-radius: 999px;
  background: transparent; cursor: pointer; font: inherit; color: inherit;
}
.user-menu:hover { background: var(--eh-bg); }
.user-menu__avatar { background: var(--eh-primary); color: #fff; font-weight: 600; font-size: 14px; }
.user-menu__name { font-weight: 500; max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.user-menu__caret { color: var(--eh-text-3); }
.user-menu__header {
  display: flex; flex-direction: column; gap: 2px;
  padding: 8px 16px 10px; border-bottom: 1px solid var(--eh-border); margin-bottom: 4px;
}
.user-menu__header span { font-size: 12px; color: var(--eh-text-3); }
@media (max-width: 1199.98px) {
  .user-menu__name, .user-menu__caret { display: none; }
}
</style>
