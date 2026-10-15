<script setup>
import AppLogo from '@/components/common/AppLogo.vue'
import { useProfileStore } from '@/stores/profile'

const learner = useProfileStore()
</script>

<template>
  <div class="nf">
    <AppLogo />
    <p class="nf__code">404</p>
    <h1>Không tìm thấy trang</h1>
    <p class="eh-muted">Đường dẫn có thể đã thay đổi hoặc không tồn tại.</p>
    <RouterLink :to="learner.hasProfile ? { name: 'dashboard' } : { name: 'welcome' }">
      <el-button type="primary">Về trang chủ</el-button>
    </RouterLink>
  </div>
</template>

<style scoped>
.nf { min-height: 100dvh; display: grid; place-content: center; justify-items: center; gap: 12px; padding: 24px; text-align: center; }
.nf__code { font-size: 96px; font-weight: 700; line-height: 1; color: var(--el-color-primary-light-7); margin-top: 24px; }
.nf h1 { font-size: 24px; }
.nf a { margin-top: 8px; }
</style>
