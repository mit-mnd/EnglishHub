import { createRouter, createWebHistory } from 'vue-router'
import { useProfileStore } from '@/stores/profile'

const WelcomeLayout = () => import('@/layouts/AuthLayout.vue')
const LearnerLayout = () => import('@/layouts/LearnerLayout.vue')

/**
 * meta:
 *  - requiresProfile: cần đã khai báo hồ sơ người học (chưa có -> trang Chào mừng)
 *  - title: tiêu đề tab trình duyệt & header
 */
const routes = [
  { path: '/', redirect: () => (useProfileStore().hasProfile ? { name: 'dashboard' } : { name: 'welcome' }) },
  {
    path: '/welcome',
    component: WelcomeLayout,
    children: [
      { path: '', name: 'welcome', component: () => import('@/views/onboarding/WelcomeView.vue'), meta: { title: 'Bắt đầu' } },
    ],
  },
  {
    path: '/app',
    component: LearnerLayout,
    meta: { requiresProfile: true },
    children: [
      { path: '', redirect: { name: 'dashboard' } },
      { path: 'dashboard', name: 'dashboard', component: () => import('@/views/learner/DashboardView.vue'), meta: { title: 'Tổng quan' } },
      { path: 'placement', name: 'placement', component: () => import('@/views/learner/PlacementIntroView.vue'), meta: { title: 'Kiểm tra xếp lớp' } },
      { path: 'placement/result/:attemptId', name: 'placement-result', component: () => import('@/views/learner/PlacementResultView.vue'), meta: { title: 'Kết quả xếp lớp' }, props: true },
      { path: 'profile', name: 'profile', component: () => import('@/views/learner/ProfileView.vue'), meta: { title: 'Hồ sơ & dữ liệu' } },
      { path: 'path', name: 'learning-path', component: () => import('@/views/learner/LearningPathView.vue'), meta: { title: 'Lộ trình học' } },
      { path: 'lessons', name: 'lessons', component: () => import('@/views/learner/LessonsView.vue'), meta: { title: 'Bài học' } },
      { path: 'courses/:courseId', name: 'course', component: () => import('@/views/learner/CourseView.vue'), meta: { title: 'Khóa học' }, props: true },
      { path: 'lessons/:lessonId', name: 'lesson', component: () => import('@/views/learner/LessonView.vue'), meta: { title: 'Bài học' }, props: true },
      { path: 'vocabulary', name: 'vocabulary', component: () => import('@/views/learner/VocabularyHubView.vue'), meta: { title: 'Từ vựng' } },
      { path: 'vocabulary/notebook', name: 'vocabulary-notebook', component: () => import('@/views/learner/VocabularyView.vue'), meta: { title: 'Sổ từ của tôi' } },
      { path: 'vocabulary/sets/:setId', name: 'vocabulary-set', component: () => import('@/views/learner/VocabularySetView.vue'), meta: { title: 'Bộ từ vựng' }, props: true },
      { path: 'sentences', name: 'sentences', component: () => import('@/views/learner/SentencesView.vue'), meta: { title: 'Mẫu câu' } },
      { path: 'sentences/:topicId', name: 'sentence-topic', component: () => import('@/views/learner/SentenceTopicView.vue'), meta: { title: 'Mẫu câu' }, props: true },
      { path: 'toeic', name: 'toeic', component: () => import('@/views/learner/ToeicView.vue'), meta: { title: 'Luyện thi TOEIC' } },
      { path: 'practice', name: 'practice', component: () => import('@/views/learner/PracticeView.vue'), meta: { title: 'Luyện tập' } },
      { path: 'practice/:gameType(matching|scramble|dictation)', name: 'game', component: () => import('@/views/learner/GameView.vue'), meta: { title: 'Trò chơi từ vựng' }, props: true },
      { path: 'notifications', name: 'notifications', component: () => import('@/views/learner/NotificationsView.vue'), meta: { title: 'Thông báo' } },
    ],
  },
  // Màn làm bài toàn màn hình (không sidebar) để tập trung
  {
    path: '/app/placement/test',
    name: 'placement-test',
    component: () => import('@/views/learner/PlacementTestView.vue'),
    meta: { requiresProfile: true, title: 'Đang làm bài xếp lớp' },
  },
  {
    path: '/app/vocabulary/review',
    name: 'flashcard-review',
    component: () => import('@/views/learner/FlashcardReviewView.vue'),
    meta: { requiresProfile: true, title: 'Ôn tập từ vựng' },
  },
  {
    path: '/app/toeic/part/:part(\\d)',
    name: 'toeic-practice',
    component: () => import('@/views/learner/ToeicSessionView.vue'),
    meta: { requiresProfile: true, title: 'Luyện TOEIC' },
    props: (route) => ({ mode: 'PRACTICE', part: Number(route.params.part) }),
  },
  {
    path: '/app/toeic/test',
    name: 'toeic-test',
    component: () => import('@/views/learner/ToeicSessionView.vue'),
    meta: { requiresProfile: true, title: 'Thi thử TOEIC' },
    props: { mode: 'TEST' },
  },

  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue'), meta: { title: 'Không tìm thấy trang' } },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: (to, from, saved) => saved ?? { top: 0 },
})

router.beforeEach(async (to) => {
  const profile = useProfileStore()
  await profile.ensureLoaded()
  if (to.matched.some((r) => r.meta.requiresProfile) && !profile.hasProfile) {
    return { name: 'welcome', query: to.fullPath !== '/app/dashboard' ? { redirect: to.fullPath } : {} }
  }
  if (to.name === 'welcome' && profile.hasProfile) return { name: 'dashboard' }
  return true
})

router.afterEach((to) => {
  document.title = to.meta.title ? `${to.meta.title} · EnglishHub` : 'EnglishHub'
})

export default router
