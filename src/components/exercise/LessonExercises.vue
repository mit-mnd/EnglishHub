<script setup>
/** Danh sách bài tập của bài học + mở trình làm bài ngay bên dưới. */
import { onMounted, ref, watch } from 'vue'
import { CircleCheckFilled, EditPen, VideoPlay } from '@element-plus/icons-vue'
import ExercisePlayer from './ExercisePlayer.vue'
import { exerciseApi } from '@/api'
import { skillMeta } from '@/utils/skills'

const props = defineProps({ lessonId: { type: Number, required: true } })
const emit = defineEmits(['lesson-completed'])

const exercises = ref([])
const loading = ref(true)
const activeTestId = ref(null)

/** silent: làm mới số liệu mà không hiện khung loading (nếu không, trình làm bài đang mở sẽ bị huỷ và dựng lại -> bắt làm lại từ đầu). */
async function load({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    exercises.value = await exerciseApi.list(props.lessonId)
  } catch {
    if (!silent) exercises.value = []
  } finally {
    loading.value = false
  }
}

function onFinished(result) {
  load({ silent: true })
  if (result.lessonCompleted) emit('lesson-completed', result)
}

watch(() => props.lessonId, () => {
  activeTestId.value = null
  load()
})
onMounted(load)
</script>

<template>
  <section v-if="loading || exercises.length" class="exercises">
    <h3 class="exercises__title"><el-icon><EditPen /></el-icon> Bài tập</h3>
    <el-skeleton v-if="loading" :rows="2" animated />

    <template v-else>
      <div v-for="ex in exercises" :key="ex.testId">
        <ExercisePlayer v-if="activeTestId === ex.testId" :test-id="ex.testId" :lesson-id="lessonId"
                        @finished="onFinished" @close="activeTestId = null" />
        <article v-else class="eh-card card">
          <div class="card__skills">
            <span v-for="s in ex.skills" :key="s" class="skill" :style="{ background: skillMeta(s).soft, color: skillMeta(s).color }">
              <el-icon><component :is="skillMeta(s).icon" /></el-icon>
            </span>
          </div>
          <div class="card__body">
            <strong>{{ ex.title }}</strong>
            <span class="eh-subtle">{{ ex.description }}</span>
            <span class="eh-subtle">
              {{ ex.questionCount }} câu · cần {{ Math.round(ex.passingPercent) }}% để đạt
              <template v-if="ex.bestPercentage != null"> · tốt nhất {{ Math.round(ex.bestPercentage) }}%</template>
            </span>
          </div>
          <el-tag v-if="ex.passed" type="success" effect="plain"><el-icon><CircleCheckFilled /></el-icon> Đã đạt</el-tag>
          <el-button type="primary" :plain="ex.passed" :icon="VideoPlay" @click="activeTestId = ex.testId">
            {{ ex.inProgressAttemptId ? 'Làm tiếp' : ex.attempts ? 'Làm lại' : 'Bắt đầu' }}
          </el-button>
        </article>
      </div>
    </template>
  </section>
</template>

<style scoped>
.exercises { display: grid; gap: 12px; }
.exercises__title { display: flex; align-items: center; gap: 8px; font-size: 18px; }
.card { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
.card__skills { display: flex; gap: 4px; }
.skill { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 10px; font-size: 17px; }
.card__body { flex: 1; min-width: 200px; display: flex; flex-direction: column; gap: 2px; }
</style>
