<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  ChatDotRound, Collection, EditPen, Guide, MoreFilled, Odometer, Reading, Medal, Trophy, User,
} from '@element-plus/icons-vue'
import AppLogo from '@/components/common/AppLogo.vue'
import FlameIcon from '@/components/common/FlameIcon.vue'
import UserMenu from '@/components/common/UserMenu.vue'
import NotificationBell from '@/components/notification/NotificationBell.vue'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useDashboardStore } from '@/stores/dashboard'
import { useNotificationStore } from '@/stores/notifications'

const route = useRoute()
const dashboard = useDashboardStore()
const notifications = useNotificationStore()
const { isMobile, isTablet } = useBreakpoint()

const navItems = [
  { name: 'dashboard', label: 'Tổng quan', icon: Odometer },
  { name: 'placement', label: 'Kiểm tra xếp lớp', short: 'Xếp lớp', icon: EditPen },
  { name: 'learning-path', label: 'Lộ trình học', short: 'Lộ trình', icon: Guide },
  { name: 'lessons', label: 'Bài học', icon: Reading, match: ['lessons', 'lesson', 'course'] },
  { name: 'vocabulary', label: 'Từ vựng', icon: Collection, match: ['vocabulary-notebook', 'vocabulary-set'] },
  { name: 'sentences', label: 'Mẫu câu', icon: ChatDotRound, match: ['sentence-topic'] },
  { name: 'toeic', label: 'Luyện thi TOEIC', short: 'TOEIC', icon: Medal },
  { name: 'practice', label: 'Trò chơi', icon: Trophy, match: ['game'] },
]
const byName = Object.fromEntries(navItems.map((i) => [i.name, i]))
/** Điện thoại: 4 mục chính + "Thêm" (mở danh sách các mục còn lại). */
const mobileNav = [byName.dashboard, byName.lessons, byName.vocabulary, byName.toeic]
const moreItems = [
  byName['learning-path'], byName.placement, byName.sentences, byName.practice,
  { name: 'profile', label: 'Hồ sơ & dữ liệu', icon: User },
]
const moreOpen = ref(false)
const moreActive = computed(() => moreItems.some((i) => isActive(i)))
watch(() => route.fullPath, () => { moreOpen.value = false })

const today = computed(() => dashboard.data?.todayProgress)
const streak = computed(() => dashboard.data?.streak)
const goalPercent = computed(() =>
  today.value ? Math.min(100, Math.round((today.value.studyMinutes / today.value.goalMinutes) * 100)) : 0)

const isActive = (item) => route.matched.some((r) => r.name === item.name)
  || (item.match ?? []).includes(route.name)
  || (item.name === 'placement' && String(route.name).startsWith('placement'))

onMounted(() => {
  if (!dashboard.data) dashboard.load()
  notifications.startPolling()
})
onUnmounted(() => notifications.stopPolling())
</script>

<template>
  <div class="shell" :class="{ 'shell--collapsed': isTablet, 'shell--mobile': isMobile }">
    <aside v-if="!isMobile" class="sidebar">
      <RouterLink :to="{ name: 'dashboard' }" class="sidebar__logo" aria-label="Trang chủ EnglishHub">
        <AppLogo :compact="isTablet" />
      </RouterLink>

      <nav class="sidebar__nav" aria-label="Điều hướng chính">
        <RouterLink
          v-for="item in navItems"
          :key="item.name"
          :to="{ name: item.name }"
          class="nav-item"
          :class="{ 'nav-item--active': isActive(item) }"
          :title="isTablet ? item.label : undefined"
        >
          <el-icon class="nav-item__icon"><component :is="item.icon" /></el-icon>
          <span class="nav-item__label">{{ item.label }}</span>
          <span v-if="item.soon" class="nav-item__soon">Sắp có</span>
        </RouterLink>
      </nav>

      <div v-if="today && !isTablet" class="sidebar__goal">
        <div class="sidebar__goal-head">
          <span>Mục tiêu hôm nay</span>
          <strong>{{ today.studyMinutes }}/{{ today.goalMinutes }} phút</strong>
        </div>
        <el-progress :percentage="goalPercent" :show-text="false" :stroke-width="8"
                     :color="today.goalReached ? '#10b981' : '#4f46e5'" />
        <p class="eh-subtle">{{ today.goalReached ? 'Tuyệt vời! Bạn đã hoàn thành mục tiêu.' : 'Học thêm một chút nữa nhé!' }}</p>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <RouterLink v-if="isMobile" :to="{ name: 'dashboard' }" aria-label="Trang chủ"><AppLogo compact /></RouterLink>
        <h1 class="topbar__title">{{ route.meta.title }}</h1>

        <div class="topbar__actions">
          <el-tooltip v-if="streak" :content="`Chuỗi ${streak.current} ngày học liên tục (kỷ lục: ${streak.longest})`">
            <span class="streak" :class="{ 'streak--off': !streak.activeToday }" aria-label="Chuỗi ngày học">
              <FlameIcon /> {{ streak.current }}
            </span>
          </el-tooltip>

          <NotificationBell />

          <UserMenu />
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>
    </div>

    <nav v-if="isMobile" class="bottom-nav" aria-label="Điều hướng chính">
      <RouterLink
        v-for="item in mobileNav"
        :key="item.name"
        :to="{ name: item.name }"
        class="bottom-nav__item"
        :class="{ 'bottom-nav__item--active': isActive(item) }"
      >
        <el-icon :size="22"><component :is="item.icon" /></el-icon>
        <span>{{ item.short ?? item.label }}</span>
      </RouterLink>
      <button type="button" class="bottom-nav__item" :class="{ 'bottom-nav__item--active': moreActive }"
              aria-label="Thêm mục" @click="moreOpen = true">
        <el-icon :size="22"><MoreFilled /></el-icon>
        <span>Thêm</span>
      </button>
    </nav>

    <el-drawer v-if="isMobile" v-model="moreOpen" direction="btt" size="auto" :with-header="false" class="more-sheet">
      <nav class="more" aria-label="Các mục khác">
        <RouterLink v-for="item in moreItems" :key="item.name" :to="{ name: item.name }" class="more__item"
                    :class="{ 'more__item--active': isActive(item) }">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </RouterLink>
      </nav>
    </el-drawer>
  </div>
</template>

<style scoped>
.shell { display: flex; min-height: 100dvh; }

/* ---------- Sidebar ---------- */
.sidebar {
  position: sticky; top: 0; height: 100dvh;
  width: var(--eh-sidebar-w); flex-shrink: 0;
  display: flex; flex-direction: column; gap: 8px;
  padding: 20px 14px;
  background: var(--eh-surface); border-right: 1px solid var(--eh-border);
}
.sidebar__logo { padding: 4px 10px 18px; text-decoration: none; }
.sidebar__nav { display: flex; flex-direction: column; gap: 4px; }

.nav-item {
  display: flex; align-items: center; gap: 12px;
  min-height: 44px; padding: 0 12px; border-radius: 10px;
  color: var(--eh-text-2); font-weight: 500; text-decoration: none;
  transition: background-color 0.15s, color 0.15s;
}
.nav-item:hover { background: var(--eh-bg); color: var(--eh-text); text-decoration: none; }
.nav-item--active { background: var(--eh-primary-soft); color: var(--eh-primary); font-weight: 600; }
.nav-item__icon { font-size: 20px; }
.nav-item__label { flex: 1; }
.nav-item__soon {
  font-size: 10px; font-weight: 600; padding: 2px 6px; border-radius: 6px;
  background: var(--eh-bg); color: var(--eh-text-3);
}

.sidebar__goal {
  margin-top: auto; padding: 14px; border-radius: 12px;
  background: linear-gradient(135deg, var(--eh-primary-soft), #f5f3ff);
  display: grid; gap: 8px;
}
.sidebar__goal-head { display: flex; justify-content: space-between; font-size: 13px; }

.shell--collapsed .sidebar { width: var(--eh-sidebar-collapsed-w); padding: 20px 10px; align-items: center; }
.shell--collapsed .sidebar__logo { padding: 4px 0 18px; }
.shell--collapsed .nav-item { justify-content: center; width: 52px; padding: 0; }
.shell--collapsed .nav-item__label,
.shell--collapsed .nav-item__soon { display: none; }

/* ---------- Main ---------- */
.main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.topbar {
  position: sticky; top: 0; z-index: 20;
  display: flex; align-items: center; gap: 16px;
  height: var(--eh-header-h); padding: 0 24px;
  background: rgba(245, 246, 251, 0.85); backdrop-filter: blur(10px);
  border-bottom: 1px solid var(--eh-border);
}
.topbar__title { font-size: 18px; font-weight: 600; flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.topbar__actions { display: flex; align-items: center; gap: 8px; }

.streak {
  display: inline-flex; align-items: center; gap: 4px;
  padding: 6px 12px; border-radius: 999px; font-weight: 700;
  background: var(--eh-accent-soft); color: #d97706;
}
.streak--off { background: var(--eh-bg); color: var(--eh-text-3); }


.content { flex: 1; }

/* ---------- Mobile ---------- */
.shell--mobile .topbar { padding: 0 12px; gap: 10px; }
.shell--mobile .topbar__title { font-size: 16px; }
.shell--mobile .content { padding-bottom: calc(var(--eh-bottom-nav-h) + env(safe-area-inset-bottom)); }

.bottom-nav {
  position: fixed; left: 0; right: 0; bottom: 0; z-index: 30;
  display: grid; grid-template-columns: repeat(5, 1fr);
  height: calc(var(--eh-bottom-nav-h) + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: var(--eh-surface); border-top: 1px solid var(--eh-border);
}
.bottom-nav__item {
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2px;
  font-size: 11px; font-weight: 500; color: var(--eh-text-3); text-decoration: none;
}
.bottom-nav__item:hover { text-decoration: none; }
button.bottom-nav__item { border: 0; background: none; font-family: inherit; cursor: pointer; }
.bottom-nav__item--active { color: var(--eh-primary); font-weight: 600; }

/* ---------- "Thêm" trên điện thoại ---------- */
.more { display: grid; gap: 4px; padding: 8px 4px calc(12px + env(safe-area-inset-bottom)); }
.more__item {
  display: flex; align-items: center; gap: 14px; min-height: 48px; padding: 0 14px;
  border-radius: 12px; color: var(--eh-text); font-weight: 500; text-decoration: none;
}
.more__item:hover { background: var(--eh-bg); text-decoration: none; }
.more__item--active { background: var(--eh-primary-soft); color: var(--eh-primary); }
</style>

<style>
.more-sheet.el-drawer { border-radius: 16px 16px 0 0; }
</style>
