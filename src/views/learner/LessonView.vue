<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, CircleCheckFilled, Clock, Guide } from '@element-plus/icons-vue'
import LessonExercises from '@/components/exercise/LessonExercises.vue'
import LessonRow from '@/components/lesson/LessonRow.vue'
import ListeningLesson from '@/components/lesson/ListeningLesson.vue'
import ReadingText from '@/components/lesson/ReadingText.vue'
import { catalogApi, pathApi, progressApi, vocabularyApi } from '@/api'
import { useStudyTracker } from '@/composables/useStudyTracker'
import { useDashboardStore } from '@/stores/dashboard'
import { formatDuration } from '@/utils/format'
import { levelColor } from '@/utils/labels'
import { skillMeta } from '@/utils/skills'

const props = defineProps({ lessonId: { type: String, required: true } })

const router = useRouter()
const dashboard = useDashboardStore()

const lesson = ref(null)
const loading = ref(true)
const errorMessage = ref('')
const savedHeadwords = ref([])
const completing = ref(false)
const completedDialog = ref(null)     // { timeSpentSeconds, next }
const listening = ref()
const contentBox = ref()
const readPercent = ref(0)

const id = computed(() => Number(props.lessonId))
const meta = computed(() => skillMeta(lesson.value?.skill))
const isCompleted = computed(() => lesson.value?.progress?.status === 'COMPLETED')
const hasMedia = computed(() => lesson.value && lesson.value.mediaType !== 'NONE' && lesson.value.mediaUrl)
const resumeMs = computed(() => (isCompleted.value ? 0 : lesson.value?.progress?.lastPositionMs ?? 0))

async function load() {
  loading.value = true
  errorMessage.value = ''
  completedDialog.value = null
  try {
    const [detail, headwords] = await Promise.all([catalogApi.lesson(id.value), vocabularyApi.headwords().catch(() => [])])
    lesson.value = detail
    savedHeadwords.value = headwords
    readPercent.value = Number(detail.progress?.progressPercent ?? 0)
  } catch (e) {
    errorMessage.value = e.message
  } finally {
    loading.value = false
  }
}

// ---------- Theo dõi thời gian học & tiến độ ----------
function snapshot() {
  if (listening.value) return listening.value.snapshot()
  return { positionMs: null, progressPercent: readPercent.value, mediaPlaying: false }
}
const tracker = useStudyTracker(id.value, snapshot)

/** Bài đọc: % = độ sâu đã cuộn tới của phần nội dung. */
function onScroll() {
  const el = contentBox.value
  if (!el || hasMedia.value) return
  const rect = el.getBoundingClientRect()
  const seen = Math.min(rect.height, window.innerHeight - rect.top)
  readPercent.value = Math.max(readPercent.value, Math.min(100, Math.max(0, (seen / rect.height) * 100)))
}
onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))

// ---------- Hoàn thành ----------
async function complete() {
  if (completing.value) return
  completing.value = true
  try {
    const res = await progressApi.complete(id.value)
    lesson.value.progress = res.progress
    const current = lesson.value.courseLessons.find((l) => l.id === id.value)
    if (current) current.progressStatus = 'COMPLETED'
    completedDialog.value = { timeSpentSeconds: res.progress.timeSpentSeconds, first: res.firstCompletion, next: await nextSuggestion(res.nextInCourse) }
    dashboard.load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    completing.value = false
  }
}

/** Ưu tiên bài kế tiếp trong lộ trình; không có lộ trình thì lấy bài kế tiếp của khóa học. */
async function nextSuggestion(nextInCourse) {
  try {
    const path = await pathApi.get()
    const item = path?.items.find((i) => i.id === path.currentItemId)
    if (item && item.lesson.id !== id.value) return { ...item.lesson, fromPath: true }
  } catch {
    /* bỏ qua */
  }
  return nextInCourse
}

/** Đạt bài tập -> backend đã đánh dấu hoàn thành bài học; cập nhật giao diện. */
async function onExerciseCompleted() {
  const detail = await catalogApi.lesson(id.value).catch(() => null)
  if (detail) {
    lesson.value.progress = detail.progress
    lesson.value.courseLessons = detail.courseLessons
  }
  dashboard.load()
}

function onListeningFinished() {
  if (!isCompleted.value) complete()
}

function goTo(lessonId) {
  completedDialog.value = null
  router.push({ name: 'lesson', params: { lessonId } })
}

// Cùng một component được dùng lại khi chuyển bài (bài trước/sau) -> báo tracker đổi bài rồi tải lại
watch(id, (newId) => {
  tracker.switchLesson(newId)
  load()
}, { immediate: true })
</script>

<template>
  <div class="eh-page">
    <el-skeleton v-if="loading" :rows="10" animated />

    <el-result v-else-if="errorMessage" icon="error" title="Không mở được bài học" :sub-title="errorMessage">
      <template #extra><el-button type="primary" @click="router.push({ name: 'lessons' })">Về danh sách bài học</el-button></template>
    </el-result>

    <div v-else class="layout">
      <main class="main">
        <el-breadcrumb separator="›" class="crumbs">
          <el-breadcrumb-item :to="{ name: 'lessons' }">Bài học</el-breadcrumb-item>
          <el-breadcrumb-item :to="{ name: 'course', params: { courseId: lesson.courseId } }">{{ lesson.courseTitle }}</el-breadcrumb-item>
        </el-breadcrumb>

        <header class="head">
          <span class="skill" :style="{ background: meta.soft, color: meta.color }">
            <el-icon><component :is="meta.icon" /></el-icon> {{ meta.label }}
          </span>
          <h1 class="title">{{ lesson.title }}</h1>
          <p v-if="lesson.summary" class="eh-muted">{{ lesson.summary }}</p>
          <div class="facts">
            <span class="level" :style="{ '--lv': levelColor(lesson.level.code) }">{{ lesson.level.name }}</span>
            <span><el-icon><Clock /></el-icon> {{ lesson.estimatedMinutes }} phút</span>
            <span v-if="isCompleted" class="done"><el-icon><CircleCheckFilled /></el-icon> Đã hoàn thành</span>
            <span v-else-if="lesson.progress" class="eh-subtle">Đã học {{ formatDuration(lesson.progress.timeSpentSeconds) }}</span>
          </div>
        </header>

        <section ref="contentBox" class="content">
          <ListeningLesson v-if="hasMedia" ref="listening" :key="lesson.id" :lesson="lesson" :start-at-ms="resumeMs"
                           @finished="onListeningFinished" />

          <div v-if="lesson.content" class="eh-card text-card">
            <p class="eh-subtle tip">💡 Bấm vào một từ (hoặc bôi đen một cụm từ) để tra nghĩa và lưu vào sổ từ.</p>
            <ReadingText :html="lesson.content" :saved-headwords="savedHeadwords" :lesson-id="lesson.id"
                         @saved="(w) => savedHeadwords.push(w.toLowerCase())" />
          </div>

          <LessonExercises :lesson-id="lesson.id" @lesson-completed="onExerciseCompleted" />
        </section>

        <footer class="nav">
          <el-button v-if="lesson.previous" :icon="ArrowLeft" @click="goTo(lesson.previous.id)">
            <span class="nav__label">{{ lesson.previous.title }}</span>
          </el-button>
          <span class="spacer" />
          <el-button v-if="!isCompleted" type="primary" size="large" :icon="Check" :loading="completing" @click="complete">
            Hoàn thành bài học
          </el-button>
          <el-button v-else-if="lesson.next" type="primary" size="large" @click="goTo(lesson.next.id)">
            Bài tiếp theo <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
        </footer>
      </main>

      <aside class="side eh-card">
        <div class="eh-card__title">{{ lesson.courseTitle }}</div>
        <div class="toc">
          <LessonRow v-for="(l, i) in lesson.courseLessons" :key="l.id" :lesson="l" :index="i + 1" compact
                     :active="l.id === lesson.id" />
        </div>
        <RouterLink :to="{ name: 'learning-path' }" class="to-path">
          <el-icon><Guide /></el-icon> Xem lộ trình của tôi
        </RouterLink>
      </aside>
    </div>

    <el-dialog :model-value="!!completedDialog" width="420px" align-center :show-close="false" @close="completedDialog = null">
      <div v-if="completedDialog" class="done-dialog">
        <div class="done-dialog__icon">🎉</div>
        <h2>{{ completedDialog.first ? 'Hoàn thành bài học!' : 'Bạn đã học lại bài này' }}</h2>
        <p class="eh-muted">Thời gian học bài này: {{ formatDuration(completedDialog.timeSpentSeconds) }}</p>
        <div v-if="completedDialog.next" class="done-dialog__next">
          <span class="eh-subtle">{{ completedDialog.next.fromPath ? 'Tiếp theo trong lộ trình' : 'Bài tiếp theo' }}</span>
          <strong>{{ completedDialog.next.title }}</strong>
        </div>
      </div>
      <template #footer>
        <el-button @click="completedDialog = null">Ở lại</el-button>
        <el-button v-if="completedDialog?.next" type="primary" @click="goTo(completedDialog.next.id)">Học tiếp</el-button>
        <el-button v-else type="primary" @click="router.push({ name: 'learning-path' })">Về lộ trình</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 24px; align-items: start; }
.crumbs { margin-bottom: 14px; }
.head { margin-bottom: 20px; display: grid; gap: 8px; justify-items: start; }
.skill { display: inline-flex; align-items: center; gap: 6px; padding: 4px 10px; border-radius: 999px; font-size: 13px; font-weight: 600; }
.title { font-size: 28px; }
.facts { display: flex; flex-wrap: wrap; align-items: center; gap: 14px; font-size: 14px; color: var(--eh-text-2); }
.facts span { display: inline-flex; align-items: center; gap: 4px; }
.level { padding: 2px 10px; border-radius: 999px; background: var(--lv); color: #fff; font-weight: 600; font-size: 13px; }
.done { color: var(--eh-success); font-weight: 600; }
.content { display: grid; gap: 16px; }
.text-card { padding: 24px 28px; }
.tip { margin-bottom: 12px; }
.nav { display: flex; align-items: center; gap: 12px; margin-top: 24px; }
.nav__label { max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.spacer { flex: 1; }
.side { position: sticky; top: 84px; padding: 16px 12px; }
.side .eh-card__title { padding: 0 8px; margin-bottom: 8px; }
.toc { display: grid; gap: 2px; max-height: 60vh; overflow-y: auto; }
.to-path { display: flex; align-items: center; gap: 6px; padding: 12px 8px 0; font-weight: 500; }
.done-dialog { text-align: center; display: grid; gap: 8px; justify-items: center; }
.done-dialog__icon { font-size: 48px; }
.done-dialog h2 { font-size: 22px; }
.done-dialog__next { display: grid; gap: 2px; margin-top: 8px; padding: 12px 16px; border-radius: 12px; background: var(--eh-bg); width: 100%; }

@media (max-width: 1099.98px) {
  .layout { grid-template-columns: 1fr; }
  .side { position: static; }
}
@media (max-width: 767.98px) {
  .title { font-size: 22px; }
  .text-card { padding: 18px; }
  .nav { flex-wrap: wrap; }
  .nav__label { max-width: 120px; }
}
</style>
