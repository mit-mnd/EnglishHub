<script setup>
import { Check } from '@element-plus/icons-vue'
import AppLogo from '@/components/common/AppLogo.vue'
import ThemeToggle from '@/components/common/ThemeToggle.vue'

const features = [
  'Không cần tài khoản - khai báo vài thông tin là bắt đầu học ngay',
  'Lộ trình cá nhân hóa theo trình độ và mục tiêu Giao tiếp, TOEIC, IELTS',
  '4.000+ từ vựng, 270+ mẫu câu và gần 800 câu luyện thi TOEIC',
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
      <ThemeToggle class="auth__theme" />
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
/* Lưới mờ + các nội dung nằm trên lớp trang trí */
.auth__brand::after {
  content: ''; position: absolute; inset: 0; pointer-events: none;
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.07) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.07) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: radial-gradient(circle at 75% 25%, #000 0%, transparent 70%);
}
.auth__brand > * { position: relative; z-index: 1; }
.auth__pitch h1 { font-size: 32px; line-height: 1.25; max-width: 420px; margin-bottom: 28px; }
.auth__pitch ul { list-style: none; padding: 0; margin: 0; display: grid; gap: 14px; }
.auth__pitch li { display: flex; gap: 12px; align-items: flex-start; font-size: 15px; color: #e0e7ff; }
.auth__pitch li .el-icon {
  flex-shrink: 0; margin-top: 1px; padding: 3px; border-radius: 50%;
  background: rgba(255, 255, 255, 0.18); color: #fff; font-size: 14px; box-sizing: content-box;
}
.auth__foot { font-size: 13px; color: #c7d2fe; }

.auth__main { position: relative; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 32px 24px; }
.auth__panel { width: 100%; max-width: 420px; animation: rise 0.5s var(--eh-ease) both; }
.auth__theme { position: absolute; top: 16px; right: 16px; }
@keyframes rise { from { opacity: 0; transform: translateY(14px); } to { opacity: 1; transform: none; } }
.auth__mobile-logo { display: none; }

@media (max-width: 899.98px) {
  .auth { grid-template-columns: 1fr; }
  .auth__brand { display: none; }
  .auth__main { justify-content: flex-start; padding: 32px 16px; }
  .auth__mobile-logo { display: block; margin-bottom: 28px; }
}
</style>
