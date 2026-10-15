<script setup>
import { Check } from '@element-plus/icons-vue'
import AppLogo from '@/components/common/AppLogo.vue'

const features = [
  'Không cần tài khoản - khai báo vài thông tin là bắt đầu học ngay',
  'Lộ trình cá nhân hóa theo trình độ và mục tiêu Giao tiếp, TOEIC, IELTS',
  '1.500+ từ vựng, 270+ mẫu câu và gần 800 câu luyện thi TOEIC',
  'Dữ liệu học lưu ngay trên máy bạn, sao lưu ra file JSON bất cứ lúc nào',
]
</script>

<template>
  <div class="auth">
    <aside class="auth__brand">
      <AppLogo light />
      <div class="auth__pitch">
        <h1>Học tiếng Anh đúng trình độ, đúng mục tiêu.</h1>
        <ul>
          <li v-for="f in features" :key="f">
            <el-icon><Check /></el-icon>
            <span>{{ f }}</span>
          </li>
        </ul>
      </div>
      <p class="auth__foot">© {{ new Date().getFullYear() }} EnglishHub</p>
    </aside>

    <main class="auth__main">
      <div class="auth__mobile-logo"><AppLogo /></div>
      <div class="auth__panel">
        <RouterView />
      </div>
    </main>
  </div>
</template>

<style scoped>
.auth { display: grid; grid-template-columns: minmax(360px, 5fr) 7fr; min-height: 100dvh; }

.auth__brand {
  position: relative; overflow: hidden;
  display: flex; flex-direction: column; justify-content: space-between;
  padding: 40px 48px;
  color: #fff;
  background:
    radial-gradient(circle at 85% 15%, rgba(251, 191, 36, 0.35), transparent 40%),
    radial-gradient(circle at 10% 90%, rgba(14, 165, 233, 0.35), transparent 45%),
    linear-gradient(160deg, #4338ca 0%, #4f46e5 45%, #6366f1 100%);
}
.auth__pitch h1 { font-size: 32px; line-height: 1.25; max-width: 420px; margin-bottom: 28px; }
.auth__pitch ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
.auth__pitch li { display: flex; gap: 12px; align-items: flex-start; font-size: 15px; color: #e0e7ff; }
.auth__pitch li .el-icon {
  flex-shrink: 0; margin-top: 1px; padding: 3px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.18); color: #fff; font-size: 14px; box-sizing: content-box;
}
.auth__foot { font-size: 13px; color: #c7d2fe; }

.auth__main { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px 24px; }
.auth__panel { width: 100%; max-width: 420px; }
.auth__mobile-logo { display: none; }

@media (max-width: 899.98px) {
  .auth { grid-template-columns: 1fr; }
  .auth__brand { display: none; }
  .auth__main { justify-content: flex-start; padding: 32px 16px; }
  .auth__mobile-logo { display: block; margin-bottom: 28px; }
}
</style>
