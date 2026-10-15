<script setup>
/**
 * Làm bài tập trong bài học: mỗi câu trả lời xong bấm "Kiểm tra" để chấm ngay và xem lời giải.
 * Hỗ trợ mọi dạng câu: trắc nghiệm, điền từ, ghép, sắp xếp (QuestionRenderer), nói (SpeakingRecorder), viết (WritingEditor).
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowLeft, ArrowRight, Check, CircleCheckFilled, CircleCloseFilled, Close, RefreshRight } from '@element-plus/icons-vue'
import QuestionRenderer from '@/components/quiz/QuestionRenderer.vue'
import QuestionStimulus from '@/components/quiz/QuestionStimulus.vue'
import ScoreRing from './ScoreRing.vue'
import SpeakingRecorder from './SpeakingRecorder.vue'
import WritingEditor from './WritingEditor.vue'
import WritingFeedbackView from './WritingFeedbackView.vue'
import { exerciseApi } from '@/api'
import { countWords } from '@/utils/feedback'
import { describeCorrectAnswer, isAnswered } from '@/utils/question'
import { skillMeta } from '@/utils/skills'

const props = defineProps({
  testId: { type: Number, required: true },
  lessonId: { type: Number, default: null },
})
const emit = defineEmits(['finished', 'close'])

const loading = ref(true)
const loadError = ref('')
const attempt = ref(null)
const passingPercent = ref(60)
const index = ref(0)
const answers = reactive({})           // questionId -> AnswerPayload (câu khách quan)
const essays = reactive({})            // questionId -> text
const recordings = reactive({})        // questionId -> kết quả chấm phát âm mới nhất
const checked = reactive({})           // questionId -> CheckResult
const checking = ref(false)
const finishing = ref(false)
const result = ref(null)

const questions = computed(() => attempt.value?.questions ?? [])
const current = computed(() => questions.value[index.value])
const currentResult = computed(() => (current.value ? checked[current.value.id] : null))
const checkedCount = computed(() => Object.keys(checked).length)
const allChecked = computed(() => questions.value.length > 0 && checkedCount.value === questions.value.length)

const canCheck = computed(() => {
  const q = current.value
  if (!q || currentResult.value) return false
  if (q.type === 'ESSAY') return countWords(essays[q.id] ?? '') > 0
  if (q.type === 'SPEAKING') return !!recordings[q.id]
  return isAnswered(q, answers[q.id])
})

async function start() {
  loading.value = true
  loadError.value = ''
  result.value = null
  for (const store of [answers, essays, recordings, checked]) Object.keys(store).forEach((k) => delete store[k])
  try {
    const res = await exerciseApi.start(props.testId)
    attempt.value = res.attempt
    passingPercent.value = Number(res.passingPercent)
    Object.assign(checked, res.checked)
    Object.entries(res.attempt.savedAnswers ?? {}).forEach(([id, payload]) => {
      answers[id] = payload
      if (payload.answerText) essays[id] = payload.answerText
    })
    const firstOpen = res.attempt.questions.findIndex((q) => !checked[q.id])
    index.value = firstOpen === -1 ? 0 : firstOpen
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

async function check() {
  const q = current.value
  const payload = q.type === 'ESSAY' ? { answerText: essays[q.id] }
    : q.type === 'SPEAKING' ? { speakingSubmissionId: recordings[q.id].id }
      : answers[q.id]
  checking.value = true
  try {
    checked[q.id] = await exerciseApi.check(attempt.value.attemptId, { questionId: q.id, ...payload })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    checking.value = false
  }
}

async function finish() {
  finishing.value = true
  try {
    result.value = await exerciseApi.finish(attempt.value.attemptId)
    emit('finished', result.value)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    finishing.value = false
  }
}

function next() {
  const nextOpen = questions.value.findIndex((q, i) => i > index.value && !checked[q.id])
  if (nextOpen !== -1) index.value = nextOpen
  else if (index.value < questions.value.length - 1) index.value += 1
}

function dotClass(q, i) {
  const r = checked[q.id]
  return {
    'dot--current': i === index.value,
    'dot--right': r && r.correct === true,
    'dot--wrong': r && r.correct !== true,
  }
}

onMounted(start)
</script>

<template>
  <div class="player eh-card">
    <header class="player__head">
      <div class="player__title">
        <strong>{{ attempt?.title ?? 'Bài tập' }}</strong>
        <span v-if="attempt && !result" class="eh-subtle">Đã chấm {{ checkedCount }}/{{ questions.length }} câu</span>
      </div>
      <el-button circle text :icon="Close" aria-label="Đóng bài tập" @click="emit('close')" />
    </header>

    <el-skeleton v-if="loading" :rows="5" animated />
    <el-result v-else-if="loadError" icon="error" :title="loadError">
      <template #extra><el-button @click="start">Thử lại</el-button></template>
    </el-result>

    <!-- Kết quả -->
    <section v-else-if="result" class="summary">
      <ScoreRing :score="Number(result.percentage)" :size="120" label="Điểm" />
      <div class="summary__text">
        <h3>{{ result.passed ? 'Bạn đã đạt! 🎉' : 'Chưa đạt, cố gắng thêm nhé!' }}</h3>
        <p class="eh-muted">{{ result.correctCount }}/{{ result.totalQuestions }} câu đạt yêu cầu ·
          cần {{ Math.round(result.passingPercent) }}% để qua bài</p>
        <el-alert v-if="result.lessonCompleted" type="success" :closable="false" show-icon
                  title="Bài học đã được đánh dấu hoàn thành." />
        <div class="summary__actions">
          <el-button :icon="RefreshRight" @click="start">Làm lại</el-button>
          <el-button type="primary" @click="emit('close')">Xong</el-button>
        </div>
      </div>
    </section>

    <!-- Làm bài -->
    <template v-else-if="current">
      <nav class="dots" aria-label="Danh sách câu">
        <button v-for="(q, i) in questions" :key="q.id" type="button" class="dot" :class="dotClass(q, i)"
                :aria-label="`Câu ${i + 1}`" @click="index = i">{{ i + 1 }}</button>
      </nav>

      <div class="question">
        <span class="skill" :style="{ color: skillMeta(current.skill).color }">
          <el-icon><component :is="skillMeta(current.skill).icon" /></el-icon> {{ skillMeta(current.skill).label }}
          · Câu {{ index + 1 }}/{{ questions.length }}
        </span>

        <QuestionStimulus :key="`s${current.id}`" :media-url="current.mediaUrl" :passage="current.passage" />

        <template v-if="current.type === 'SPEAKING'">
          <SpeakingRecorder :key="current.id" :reference-text="current.referenceText" :question-id="current.id"
                            :lesson-id="lessonId" :disabled="!!currentResult" @scored="(r) => (recordings[current.id] = r)" />
        </template>

        <template v-else-if="current.type === 'ESSAY'">
          <p class="prompt">{{ current.content }}</p>
          <WritingEditor v-if="!currentResult?.writing" v-model="essays[current.id]" :min-words="current.minWords"
                         :max-words="current.maxWords" :disabled="checking" />
          <WritingFeedbackView v-else :feedback="currentResult.writing" :text="essays[current.id] ?? ''" />
        </template>

        <QuestionRenderer v-else :key="current.id" :question="current" :model-value="answers[current.id]"
                          :disabled="!!currentResult" @update:model-value="(v) => (answers[current.id] = v)" />
      </div>

      <!-- Phản hồi cho câu khách quan -->
      <div v-if="currentResult && !['ESSAY', 'SPEAKING'].includes(current.type)" class="feedback"
           :class="currentResult.correct ? 'feedback--right' : 'feedback--wrong'" role="status">
        <strong>
          <el-icon><CircleCheckFilled v-if="currentResult.correct" /><CircleCloseFilled v-else /></el-icon>
          {{ currentResult.correct ? 'Chính xác!' : Number(currentResult.score) > 0 ? 'Đúng một phần' : 'Chưa đúng' }}
          <span class="pts">+{{ currentResult.score }}/{{ currentResult.points }} điểm</span>
        </strong>
        <p v-if="!currentResult.correct">Đáp án: <b>{{ describeCorrectAnswer(currentResult.review) }}</b></p>
        <p v-if="currentResult.review.explanation" class="explain">{{ currentResult.review.explanation }}</p>
      </div>
      <div v-else-if="currentResult" class="feedback" :class="currentResult.correct ? 'feedback--right' : 'feedback--wrong'">
        <strong>+{{ currentResult.score }}/{{ currentResult.points }} điểm
          {{ currentResult.correct ? '· Đạt yêu cầu' : '· Chưa đạt, hãy xem gợi ý và luyện thêm' }}</strong>
      </div>

      <footer class="actions">
        <el-button :icon="ArrowLeft" :disabled="index === 0" @click="index -= 1" />
        <span class="spacer" />
        <el-button v-if="!currentResult" type="primary" :icon="Check" :disabled="!canCheck" :loading="checking" @click="check">
          {{ current.type === 'ESSAY' ? 'Nộp bài viết' : current.type === 'SPEAKING' ? 'Dùng bản thu này' : 'Kiểm tra' }}
        </el-button>
        <el-button v-else-if="!allChecked" type="primary" @click="next">
          Câu tiếp <el-icon class="el-icon--right"><ArrowRight /></el-icon>
        </el-button>
        <el-button v-if="allChecked || (currentResult && index === questions.length - 1)" type="success"
                   :loading="finishing" @click="finish">
          Xem kết quả
        </el-button>
      </footer>
    </template>
  </div>
</template>

<style scoped>
.player { display: grid; gap: 16px; border-color: var(--el-color-primary-light-7); }
.player__head { display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.player__title { display: flex; flex-direction: column; }
.dots { display: flex; gap: 6px; flex-wrap: wrap; }
.dot {
  width: 32px; height: 32px; border-radius: 50%; border: 1.5px solid var(--eh-border); background: var(--eh-surface);
  font: inherit; font-size: 13px; font-weight: 600; color: var(--eh-text-2); cursor: pointer;
}
.dot--right { background: var(--eh-success); border-color: var(--eh-success); color: #fff; }
.dot--wrong { background: #f97316; border-color: #f97316; color: #fff; }
.dot--current { box-shadow: 0 0 0 3px rgba(79, 70, 229, 0.3); border-color: var(--eh-primary); }
.question { display: grid; gap: 12px; }
.skill { display: inline-flex; align-items: center; gap: 6px; font-size: 13px; font-weight: 600; }
.prompt { font-size: 17px; font-weight: 500; line-height: 1.6; }
.feedback { padding: 12px 16px; border-radius: 12px; display: grid; gap: 4px; }
.feedback strong { display: inline-flex; align-items: center; gap: 6px; }
.feedback--right { background: var(--eh-success-soft); color: #065f46; }
.feedback--wrong { background: #fff7ed; color: #9a3412; }
.pts { font-weight: 500; font-size: 13px; opacity: 0.8; margin-left: 4px; }
.explain { font-size: 14px; }
.actions { display: flex; align-items: center; gap: 8px; }
.actions .el-button + .el-button { margin-left: 0; }
.spacer { flex: 1; }
.summary { display: flex; align-items: center; gap: 24px; flex-wrap: wrap; padding: 8px 0; }
.summary__text { flex: 1; min-width: 220px; display: grid; gap: 8px; }
.summary__text h3 { font-size: 20px; }
.summary__actions { display: flex; gap: 8px; }
.summary__actions .el-button + .el-button { margin-left: 0; }
</style>
