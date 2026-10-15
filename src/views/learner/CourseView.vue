<script setup>
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Clock, VideoPlay } from '@element-plus/icons-vue'
import LessonRow from '@/components/lesson/LessonRow.vue'
import { catalogApi } from '@/api'
import { GOAL_LABELS, levelColor } from '@/utils/labels'

const props = defineProps({ courseId: { type: String, required: true } })
const router = useRouter()

const detail = ref(null)
const errorMessage = ref('')

const course = computed(() => detail.value?.course)
/** Bài nên học tiếp: bài đang học dở đầu tiên, nếu không thì bài chưa học đầu tiên. */
const nextLesson = computed(() => {
  const lessons = detail.value?.lessons ?? []
  return lessons.find((l) => l.progressStatus === 'IN_PROGRESS') ?? lessons.find((l) => l.progressStatus !== 'COMPLETED')
})

watch(() => props.courseId, async (id) => {
  errorMessage.value = ''
  detail.value = null
  try {
    detail.value = await catalogApi.course(id)
  } catch (e) {
    errorMessage.value = e.message
  }
}, { immediate: true })
</script>

<template>
  <div class="eh-page">
    <el-result v-if="errorMessage" icon="error" title="Không mở được khóa học" :sub-title="errorMessage">
      <template #extra><el-button @click="router.push({ name: 'lessons' })">Về thư viện</el-button></template>
    </el-result>
    <el-skeleton v-else-if="!detail" :rows="8" animated />

    <template v-else>
      <el-breadcrumb separator="›" class="crumbs">
        <el-breadcrumb-item :to="{ name: 'lessons' }">Bài học</el-breadcrumb-item>
        <el-breadcrumb-item>{{ course.title }}</el-breadcrumb-item>
      </el-breadcrumb>

      <section class="eh-card head" :style="{ '--lv': levelColor(course.level.code) }">
        <div class="head__info">
          <div class="head__chips">
            <span class="chip-level">{{ course.level.name }} · {{ course.level.cefrRange }}</span>
            <span v-if="course.goalType !== 'GENERAL'" class="eh-chip">{{ GOAL_LABELS[course.goalType] }}</span>
          </div>
          <h2>{{ course.title }}</h2>
          <p class="eh-muted">{{ course.description }}</p>
          <p class="eh-subtle meta"><el-icon><Clock /></el-icon> {{ course.lessonCount }} bài · {{ course.totalMinutes }} phút
            · đã xong {{ course.completedLessons }}/{{ course.lessonCount }}</p>
        </div>
        <el-button v-if="nextLesson" type="primary" size="large" :icon="VideoPlay"
                   @click="router.push({ name: 'lesson', params: { lessonId: nextLesson.id } })">
          {{ course.completedLessons || nextLesson.progressStatus ? 'Học tiếp' : 'Bắt đầu học' }}
        </el-button>
      </section>

      <section class="eh-card list">
        <LessonRow v-for="(l, i) in detail.lessons" :key="l.id" :lesson="l" :index="i + 1" />
      </section>
    </template>
  </div>
</template>

<style scoped>
.crumbs { margin-bottom: 14px; }
.head { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; margin-bottom: 16px; border-top: 5px solid var(--lv); }
.head__info { flex: 1; min-width: 240px; display: grid; gap: 6px; }
.head__chips { display: flex; gap: 8px; }
.chip-level { font-size: 13px; font-weight: 600; padding: 4px 10px; border-radius: 999px; background: var(--lv); color: #fff; }
.head h2 { font-size: 24px; }
.meta { display: inline-flex; align-items: center; gap: 4px; }
.list { padding: 8px; display: grid; gap: 2px; }
</style>
