<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, ArrowRight, Check, CircleCheck, Close, Flag, Grid, Loading, Timer, WarningFilled,
} from '@element-plus/icons-vue'
import AppLogo from '@/components/common/AppLogo.vue'
import QuestionNavigator from '@/components/quiz/QuestionNavigator.vue'
import QuestionRenderer from '@/components/quiz/QuestionRenderer.vue'
import QuestionStimulus from '@/components/quiz/QuestionStimulus.vue'
import { placementApi } from '@/api'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { formatClock } from '@/utils/format'
import { skillLabel } from '@/utils/labels'
import { isAnswered } from '@/utils/question'
import { storage } from '@/utils/storage'

const router = useRouter()
const { isMobile } = useBreakpoint()

const attempt = ref(null)
const loading = ref(true)
const loadError = ref('')
const submitting = ref(false)
const answers = reactive({})          // questionId -> AnswerPayload
const saveState = reactive({})        // questionId -> 'saving' | 'saved' | 'error'
const flagged = ref(new Set())
const currentIndex = ref(0)
const navigatorOpen = ref(false)

const questions = computed(() => attempt.value?.questions ?? [])
const current = computed(() => questions.value[currentIndex.value])
const answeredCount = computed(() => questions.value.filter((q) => isAnswered(q, answers[q.id])).length)
const progressPercent = computed(() => (questions.value.length ? Math.round((answeredCount.value / questions.value.length) * 100) : 0))
const isLast = computed(() => currentIndex.value === questions.value.length - 1)

const saveStatus = computed(() => {
  const states = Object.values(saveState)
  if (states.includes('error')) return 'error'
  if (states.includes('saving')) return 'saving'
  return states.length ? 'saved' : 'idle'
})

const navigatorProps = computed(() => ({
  questions: questions.value,
  answers,
  flagged: flagged.value,
  currentIndex: currentIndex.value,
  saveStatus: saveStatus.value,
  submitting: submitting.value,
  answeredCount: answeredCount.value,
}))

// ------------------------------------------------------------------ Đồng hồ (theo giờ server)
const serverOffsetMs = ref(0)    // giờ server - giờ máy học viên
const nowMs = ref(Date.now())
let clockTimer = null
const remainingMs = computed(() => {
  if (!attempt.value?.expiresAt) return null
  return new Date(attempt.value.expiresAt).getTime() - (nowMs.value + serverOffsetMs.value)
})
const isLowTime = computed(() => remainingMs.value != null && remainingMs.value <= 2 * 60 * 1000)

watch(remainingMs, (ms) => {
  if (ms != null && ms <= 0 && !submitting.value && attempt.value) {
    ElMessage.warning('Đã hết giờ! Bài làm của bạn đang được nộp tự động.')
    submit(true)
  }
})

// ------------------------------------------------------------------ Autosave
const saveTimers = {}
const flagKey = () => `eh.placement.flags.${attempt.value?.attemptId}`

function onAnswer(question, payload) {
  answers[question.id] = payload
  clearTimeout(saveTimers[question.id])
  // Ô nhập chữ: chờ người học gõ xong; câu chọn: lưu ngay
  const delay = ['FILL_BLANK', 'ESSAY'].includes(question.type) ? 700 : 0
  saveTimers[question.id] = setTimeout(() => persist(question.id), delay)
}

async function persist(questionId) {
  if (submitting.value) return
  saveState[questionId] = 'saving'
  try {
    await placementApi.saveAnswer(attempt.value.attemptId, { questionId, ...answers[questionId] })
    saveState[questionId] = 'saved'
  } catch (e) {
    saveState[questionId] = 'error'
    if (e.code === 'ATTEMPT_EXPIRED') {
      submit(true)
    } else if (e.code === 'ATTEMPT_NOT_IN_PROGRESS') {
      goToResult()
    }
  }
}

function retryFailedSaves() {
  Object.entries(saveState).filter(([, s]) => s === 'error').forEach(([id]) => persist(Number(id)))
}

// ------------------------------------------------------------------ Điều hướng
function goTo(index) {
  if (index >= 0 && index < questions.value.length) {
    currentIndex.value = index
    navigatorOpen.value = false
  }
}

function toggleFlag() {
  const next = new Set(flagged.value)
  next.has(current.value.id) ? next.delete(current.value.id) : next.add(current.value.id)
  flagged.value = next
  storage.set(flagKey(), JSON.stringify([...next]))
}

/** Phím tắt: A-D / 1-4 chọn đáp án, mũi tên trái/phải chuyển câu. */
function onKeydown(event) {
  if (!current.value || submitting.value) return
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || event.ctrlKey || event.metaKey || event.altKey) return
  if (event.key === 'ArrowRight') { goTo(currentIndex.value + 1); return }
  if (event.key === 'ArrowLeft') { goTo(currentIndex.value - 1); return }
  if (current.value.type !== 'SINGLE_CHOICE') return
  const key = event.key.toUpperCase()
  const index = /^[1-9]$/.test(key) ? Number(key) - 1 : 'ABCDEFGH'.indexOf(key)
  const option = current.value.options?.[index]
  if (option) onAnswer(current.value, { selectedOptionIds: [option.id] })
}

// ------------------------------------------------------------------ Nộp bài
async function confirmSubmit() {
  const unanswered = questions.value.length - answeredCount.value
  try {
    await ElMessageBox.confirm(
      unanswered
        ? `Bạn còn ${unanswered} câu chưa trả lời. Câu bỏ trống sẽ được tính 0 điểm. Vẫn nộp bài?`
        : 'Bạn đã trả lời tất cả các câu. Nộp bài ngay?',
      'Nộp bài',
      { confirmButtonText: 'Nộp bài', cancelButtonText: 'Làm tiếp', type: unanswered ? 'warning' : 'success' },
    )
  } catch {
    return
  }
  submit(false)
}

async function submit(auto) {
  if (submitting.value) return
  submitting.value = true
  Object.values(saveTimers).forEach(clearTimeout)
  const payload = questions.value
    .filter((q) => isAnswered(q, answers[q.id]))
    .map((q) => ({ questionId: q.id, ...answers[q.id] }))
  try {
    await placementApi.submit(attempt.value.attemptId, payload)
    storage.set(flagKey(), null)
    goToResult()
  } catch (e) {
    submitting.value = false
    ElMessage.error(auto ? `Nộp bài tự động thất bại: ${e.message}` : e.message)
  }
}

function goToResult() {
  router.replace({ name: 'placement-result', params: { attemptId: attempt.value.attemptId } })
}

async function exit() {
  try {
    await ElMessageBox.confirm(
      'Câu trả lời đã được lưu, bạn có thể quay lại làm tiếp. Lưu ý: thời gian làm bài vẫn tiếp tục chạy.',
      'Tạm rời khỏi bài làm?',
      { confirmButtonText: 'Rời khỏi', cancelButtonText: 'Ở lại', type: 'info' },
    )
    router.push({ name: 'placement' })
  } catch {
    /* ở lại */
  }
}

// ------------------------------------------------------------------ Khởi tạo
async function start() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await placementApi.start()
    serverOffsetMs.value = new Date(res.serverTime).getTime() - Date.now()
    attempt.value = res
    Object.entries(res.savedAnswers ?? {}).forEach(([id, payload]) => { answers[Number(id)] = payload })
    try {
      flagged.value = new Set(JSON.parse(storage.get(flagKey()) ?? '[]'))
    } catch {
      flagged.value = new Set()
    }
    // Tiếp tục bài cũ: nhảy tới câu đầu tiên chưa làm
    const firstOpen = res.questions.findIndex((q) => !isAnswered(q, answers[q.id]))
    currentIndex.value = firstOpen === -1 ? 0 : firstOpen
    clockTimer = setInterval(() => { nowMs.value = Date.now() }, 250)
  } catch (e) {
    loadError.value = e.message
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  start()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  clearInterval(clockTimer)
  Object.values(saveTimers).forEach(clearTimeout)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="test">
    <header class="test__header">
      <AppLogo :compact="isMobile" />
      <div class="test__heading">
        <strong>{{ attempt?.title ?? 'Bài kiểm tra xếp lớp' }}</strong>
        <span v-if="attempt" class="eh-subtle">Đã làm {{ answeredCount }}/{{ questions.length }} câu</span>
      </div>
      <div v-if="remainingMs != null" class="timer" :class="{ 'timer--low': isLowTime }" role="timer"
           :aria-label="`Thời gian còn lại ${formatClock(remainingMs)}`">
        <el-icon><Timer /></el-icon>{{ formatClock(remainingMs) }}
      </div>
      <el-button :icon="Close" circle text aria-label="Rời khỏi bài làm" @click="exit" />
    </header>
    <el-progress v-if="attempt" class="test__progress" :percentage="progressPercent" :show-text="false" :stroke-width="4" />

    <div v-if="loading" class="test__state"><el-icon class="is-loading" :size="32"><Loading /></el-icon></div>

    <el-result v-else-if="loadError" icon="error" title="Không thể bắt đầu bài làm" :sub-title="loadError" class="test__state">
      <template #extra>
        <el-button @click="router.push({ name: 'placement' })">Quay lại</el-button>
        <el-button type="primary" @click="start">Thử lại</el-button>
      </template>
    </el-result>

    <div v-else-if="current" class="test__body">
      <main class="question-area">
        <div class="question-meta">
          <span class="eh-chip">Câu {{ current.number }}/{{ questions.length }}</span>
          <span class="eh-chip eh-chip--muted">{{ skillLabel(current.skill) }}</span>
          <span class="save" :class="`save--${saveState[current.id] ?? 'idle'}`" aria-live="polite">
            <template v-if="saveState[current.id] === 'saving'"><el-icon class="is-loading"><Loading /></el-icon> Đang lưu…</template>
            <template v-else-if="saveState[current.id] === 'saved'"><el-icon><CircleCheck /></el-icon> Đã lưu</template>
            <template v-else-if="saveState[current.id] === 'error'">
              <el-icon><WarningFilled /></el-icon> Chưa lưu được
              <el-button link type="danger" size="small" @click="retryFailedSaves">Thử lại</el-button>
            </template>
          </span>
        </div>

        <div class="eh-card question-card">
          <QuestionStimulus :key="`s${current.id}`" :media-url="current.mediaUrl" :passage="current.passage" />
          <QuestionRenderer
            :key="current.id"
            :question="current"
            :model-value="answers[current.id]"
            :disabled="submitting"
            @update:model-value="(v) => onAnswer(current, v)"
          />
          <p v-if="!isMobile && current.type === 'SINGLE_CHOICE'" class="eh-subtle shortcut-hint">
            Mẹo: nhấn phím A–D để chọn đáp án, ← → để chuyển câu.
          </p>
        </div>

        <footer class="question-nav">
          <el-button :icon="ArrowLeft" :disabled="currentIndex === 0" @click="goTo(currentIndex - 1)">
            <span v-if="!isMobile">Câu trước</span>
          </el-button>
          <el-button :type="flagged.has(current.id) ? 'warning' : 'default'" :plain="flagged.has(current.id)" :icon="Flag"
                     :aria-label="flagged.has(current.id) ? 'Bỏ đánh dấu' : 'Xem lại sau'" @click="toggleFlag">
            <span v-if="!isMobile">{{ flagged.has(current.id) ? 'Bỏ đánh dấu' : 'Xem lại sau' }}</span>
          </el-button>
          <el-button v-if="isMobile" :icon="Grid" aria-label="Danh sách câu hỏi" @click="navigatorOpen = true" />
          <span class="spacer" />
          <el-button v-if="!isLast" type="primary" @click="goTo(currentIndex + 1)">
            Câu tiếp <el-icon class="el-icon--right"><ArrowRight /></el-icon>
          </el-button>
          <el-button v-else type="success" :icon="Check" :loading="submitting" @click="confirmSubmit">Nộp bài</el-button>
        </footer>
      </main>

      <aside v-if="!isMobile" class="navigator eh-card">
        <div class="eh-card__title">Danh sách câu hỏi</div>
        <QuestionNavigator v-bind="navigatorProps" @go="goTo" @submit="confirmSubmit" />
      </aside>
      <el-drawer v-else v-model="navigatorOpen" direction="btt" size="auto" title="Danh sách câu hỏi">
        <QuestionNavigator v-bind="navigatorProps" @go="goTo" @submit="confirmSubmit" />
      </el-drawer>
    </div>
  </div>
</template>

<style scoped>
.test { min-height: 100dvh; display: flex; flex-direction: column; }
.test__header {
  position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 16px;
  height: 64px; padding: 0 24px; background: var(--eh-surface); border-bottom: 1px solid var(--eh-border);
}
.test__heading { flex: 1; min-width: 0; display: flex; flex-direction: column; line-height: 1.3; }
.test__heading strong { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.test__progress { position: sticky; top: 64px; z-index: 10; }
.test__progress :deep(.el-progress-bar__outer) { border-radius: 0; background: var(--eh-border); }
.test__progress :deep(.el-progress-bar__inner) { border-radius: 0; }

.timer {
  display: inline-flex; align-items: center; gap: 6px; padding: 8px 14px; border-radius: 10px;
  font-size: 18px; font-weight: 700; font-variant-numeric: tabular-nums;
  background: var(--eh-primary-soft); color: var(--eh-primary);
}
.timer--low { background: var(--eh-danger-soft); color: var(--eh-danger); animation: pulse 1s ease-in-out infinite; }
@keyframes pulse { 50% { opacity: 0.6; } }

.test__state { flex: 1; display: grid; place-items: center; }

.test__body {
  flex: 1; width: 100%; max-width: 1180px; margin: 0 auto; padding: 24px;
  display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 24px; align-items: start;
}
.question-meta { display: flex; align-items: center; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; }
.eh-chip--muted { background: var(--eh-bg); color: var(--eh-text-2); border: 1px solid var(--eh-border); }
.save { margin-left: auto; display: inline-flex; align-items: center; gap: 4px; font-size: 13px; color: var(--eh-text-3); }
.save--saved { color: var(--eh-success); }
.save--error { color: var(--eh-danger); }

.question-card { padding: 28px; }
.shortcut-hint { margin-top: 18px; }

.question-nav { display: flex; align-items: center; gap: 8px; margin-top: 16px; }
.question-nav .el-button + .el-button { margin-left: 0; }
.spacer { flex: 1; }

.navigator { position: sticky; top: 92px; }

@media (max-width: 1023.98px) {
  .test__body { grid-template-columns: 1fr; }
}
@media (max-width: 767.98px) {
  .test__header { padding: 0 12px; gap: 10px; }
  .timer { font-size: 16px; padding: 6px 10px; }
  .test__body { padding: 16px 12px 96px; }
  .question-card { padding: 18px; }
  .question-nav {
    position: fixed; left: 0; right: 0; bottom: 0; z-index: 10; margin: 0;
    padding: 10px 12px calc(10px + env(safe-area-inset-bottom));
    background: var(--eh-surface); border-top: 1px solid var(--eh-border);
  }
}
</style>
