<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft, ArrowRight, Close, Timer } from '@element-plus/icons-vue'
import ScoreRing from '@/components/exercise/ScoreRing.vue'
import ToeicGroup from '@/components/toeic/ToeicGroup.vue'
import { toeicApi } from '@/api'
import { useDashboardStore } from '@/stores/dashboard'
import { formatClock, formatDuration } from '@/utils/format'
import { firstNumber, PART_LABELS, questionCount, scoreGroup } from '@/utils/toeic'

/**
 * Làm bài TOEIC toàn màn hình.
 *  - PRACTICE (theo Part): chấm từng nhóm câu ngay, xem lời giải + kịch bản + bản dịch.
 *  - TEST (thi thử rút gọn): đồng hồ đếm ngược, chuyển câu tự do, nộp bài -> điểm ước lượng, xem lại đáp án.
 */
const props = defineProps({
  mode: { type: String, required: true },
  part: { type: Number, default: null },
})
const router = useRouter()
const dashboard = useDashboardStore()

const session = ref(null)
const loading = ref(true)
const index = ref(0)
const answers = ref({})
const checkedGroups = ref(new Set())
const result = ref(null)
const reviewing = ref(false)
const submitting = ref(false)
const timeLeftMs = ref(0)
let startedAt = Date.now()
let timer = null

const isTest = computed(() => props.mode === 'TEST')
const groups = computed(() => session.value?.groups ?? [])
const group = computed(() => groups.value[index.value])
const total = computed(() => questionCount(groups.value))
const answeredCount = computed(() => Object.keys(answers.value).length)
const groupAnswered = computed(() => group.value?.questions.every((q) => answers.value[q.id] != null))
const revealed = computed(() => reviewing.value || (!isTest.value && checkedGroups.value.has(group.value?.id)))
const title = computed(() => (isTest.value ? 'Thi thử TOEIC rút gọn' : PART_LABELS[props.part] ?? 'Luyện TOEIC'))
const inProgress = computed(() => !result.value && answeredCount.value > 0)

async function load() {
  loading.value = true
  try {
    session.value = isTest.value ? await toeicApi.miniTest() : await toeicApi.practice(props.part)
    startedAt = Date.now()
    if (isTest.value) startTimer(session.value.durationMinutes * 60_000)
  } catch (e) {
    ElMessage.error(e.message)
    router.replace({ name: 'toeic' })
  } finally {
    loading.value = false
  }
}

function startTimer(ms) {
  const deadline = Date.now() + ms
  timeLeftMs.value = ms
  timer = setInterval(() => {
    timeLeftMs.value = Math.max(0, deadline - Date.now())
    if (timeLeftMs.value === 0) {
      clearInterval(timer)
      ElMessage.warning('Hết giờ! Bài làm được nộp tự động.')
      submit()
    }
  }, 500)
}

function onAnswer(questionId, choice) {
  answers.value = { ...answers.value, [questionId]: choice }
}

function check() {
  checkedGroups.value = new Set(checkedGroups.value).add(group.value.id)
}

function go(i) {
  index.value = Math.max(0, Math.min(groups.value.length - 1, i))
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

async function confirmSubmit() {
  const missing = total.value - answeredCount.value
  if (missing > 0) {
    try {
      await ElMessageBox.confirm(`Bạn còn ${missing} câu chưa trả lời. Nộp bài?`, 'Nộp bài', { confirmButtonText: 'Nộp bài', cancelButtonText: 'Làm tiếp', type: 'warning' })
    } catch {
      return
    }
  }
  submit()
}

async function submit() {
  if (submitting.value || result.value) return
  submitting.value = true
  clearInterval(timer)
  try {
    result.value = await toeicApi.submit({
      mode: props.mode, part: props.part, groupIds: groups.value.map((g) => g.id), answers: answers.value,
      durationSeconds: Math.round((Date.now() - startedAt) / 1000),
    })
    dashboard.load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    submitting.value = false
  }
}

function review() {
  reviewing.value = true
  go(0)
}

function restart() {
  answers.value = {}
  checkedGroups.value = new Set()
  result.value = null
  reviewing.value = false
  index.value = 0
  load()
}

/** Màu ô trong bảng chọn câu (thi thử). */
function cellClass(g, q) {
  if (reviewing.value) return answers.value[q.id] === q.answer ? 'cell--ok' : 'cell--bad'
  return { 'cell--done': answers.value[q.id] != null, 'cell--now': g.id === group.value?.id }
}

onBeforeRouteLeave(async () => {
  if (!inProgress.value) return true
  try {
    await ElMessageBox.confirm('Bài đang làm sẽ không được lưu. Thoát?', 'Thoát', { type: 'warning', confirmButtonText: 'Thoát', cancelButtonText: 'Ở lại' })
    return true
  } catch {
    return false
  }
})

onMounted(load)
onBeforeUnmount(() => clearInterval(timer))
</script>

<template>
  <div class="ts">
    <header class="ts__bar">
      <RouterLink :to="{ name: 'toeic' }" class="ts__close" aria-label="Thoát"><el-icon :size="20"><Close /></el-icon></RouterLink>
      <div class="ts__title">
        <strong>{{ title }}</strong>
        <span v-if="groups.length && !result" class="eh-subtle">Đã trả lời {{ answeredCount }}/{{ total }} câu</span>
        <span v-else-if="reviewing" class="eh-subtle">Xem lại đáp án</span>
      </div>
      <span v-if="isTest && !result" class="ts__timer" :class="{ 'ts__timer--low': timeLeftMs < 5 * 60_000 }">
        <el-icon><Timer /></el-icon> {{ formatClock(timeLeftMs) }}
      </span>
      <el-button v-if="isTest && !result" type="primary" :loading="submitting" @click="confirmSubmit">Nộp bài</el-button>
    </header>
    <el-progress v-if="groups.length" :percentage="((index + 1) / groups.length) * 100" :show-text="false" :stroke-width="3" class="ts__progress" />

    <main class="ts__main">
      <div v-if="loading" v-loading="true" class="ts__loading" />

      <!-- Kết quả -->
      <section v-else-if="result && !reviewing" class="eh-card result">
        <ScoreRing :score="result.percent" :size="140" label="% đúng" />
        <h2>{{ result.percent >= 80 ? 'Xuất sắc!' : result.percent >= 60 ? 'Làm tốt lắm!' : 'Cố gắng thêm nhé!' }}</h2>
        <p class="eh-muted">Đúng {{ result.correct }}/{{ result.total }} câu · {{ formatDuration(result.durationSeconds) }}</p>
        <div v-if="result.estimate" class="estimate">
          <div><span>Listening</span><strong>{{ result.estimate.listening ?? '—' }}</strong></div>
          <div><span>Reading</span><strong>{{ result.estimate.reading ?? '—' }}</strong></div>
          <div class="estimate__total"><span>Tổng ước lượng</span><strong>{{ result.estimate.total }}</strong></div>
        </div>
        <p v-if="result.estimate" class="eh-subtle note">
          Điểm quy đổi chỉ mang tính tham khảo (đề rút gọn, không phải bảng quy đổi chính thức của ETS).
        </p>
        <div class="result__actions">
          <el-button @click="review">Xem lại đáp án</el-button>
          <el-button type="primary" @click="restart">{{ isTest ? 'Làm đề khác' : 'Luyện tiếp' }}</el-button>
          <RouterLink :to="{ name: 'toeic' }"><el-button text>Về trang TOEIC</el-button></RouterLink>
        </div>
      </section>

      <!-- Làm bài / xem lại -->
      <template v-else-if="group">
        <div class="ts__layout" :class="{ 'ts__layout--test': isTest }">
          <section class="eh-card ts__card">
            <p class="ts__part">{{ PART_LABELS[group.part] }}<span v-if="group.title && group.kind !== 'passage'"> · {{ group.title }}</span></p>
            <ToeicGroup :key="group.id" :group="group" :answers="answers" :revealed="revealed"
                        :number-start="firstNumber(groups, index)" @answer="onAnswer" />

            <footer class="ts__nav">
              <el-button :icon="ArrowLeft" :disabled="index === 0" @click="go(index - 1)">Trước</el-button>
              <span class="adm-spacer" />
              <template v-if="!isTest && !reviewing">
                <el-button v-if="!revealed" type="primary" :disabled="!groupAnswered" @click="check">Kiểm tra</el-button>
                <span v-else class="ts__score">Đúng {{ scoreGroup(group, answers).correct }}/{{ group.questions.length }}</span>
              </template>
              <el-button v-if="index < groups.length - 1" :type="revealed || isTest ? 'primary' : 'default'"
                         :disabled="!isTest && !revealed" @click="go(index + 1)">
                Tiếp <el-icon class="el-icon--right"><ArrowRight /></el-icon>
              </el-button>
              <el-button v-else-if="reviewing" type="primary" @click="reviewing = false">Xem kết quả</el-button>
              <el-button v-else-if="!isTest" type="primary" :disabled="!revealed" :loading="submitting" @click="submit">Xem kết quả</el-button>
              <el-button v-else type="primary" :loading="submitting" @click="confirmSubmit">Nộp bài</el-button>
            </footer>
          </section>

          <!-- Bảng chọn câu (thi thử / xem lại) -->
          <aside v-if="isTest || reviewing" class="eh-card ts__map">
            <p class="eh-subtle">Bảng câu hỏi</p>
            <div class="ts__cells">
              <template v-for="(g, gi) in groups" :key="g.id">
                <button v-for="(q, qi) in g.questions" :key="q.id" type="button" class="cell" :class="cellClass(g, q)" @click="go(gi)">
                  {{ firstNumber(groups, gi) + qi }}
                </button>
              </template>
            </div>
          </aside>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.ts { min-height: 100dvh; background: var(--eh-bg); }
.ts__bar {
  position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 12px;
  height: var(--eh-header-h); padding: 0 20px; background: var(--eh-surface); border-bottom: 1px solid var(--eh-border);
}
.ts__close { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 50%; color: var(--eh-text-2); }
.ts__close:hover { background: var(--eh-bg); }
.ts__title { flex: 1; min-width: 0; display: grid; }
.ts__title strong { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ts__timer { display: inline-flex; align-items: center; gap: 4px; font-weight: 700; font-variant-numeric: tabular-nums; }
.ts__timer--low { color: #ef4444; }
.ts__progress { position: sticky; top: var(--eh-header-h); z-index: 9; }
.ts__progress :deep(.el-progress-bar__outer) { border-radius: 0; }
.ts__main { max-width: 1100px; margin: 0 auto; padding: 24px 16px 48px; }
.ts__loading { min-height: 320px; }
.ts__layout { display: grid; gap: 20px; }
.ts__layout--test { grid-template-columns: minmax(0, 1fr) 260px; align-items: start; }
.ts__card { display: grid; gap: 18px; }
.ts__part { margin: 0; font-size: 13px; font-weight: 600; color: var(--eh-primary); }
.ts__nav { display: flex; align-items: center; gap: 8px; padding-top: 6px; border-top: 1px solid var(--eh-border); }
.ts__nav .el-button + .el-button { margin-left: 0; }
.ts__score { font-weight: 600; color: var(--eh-text-2); }
.adm-spacer { flex: 1; }
.ts__map { position: sticky; top: calc(var(--eh-header-h) + 16px); display: grid; gap: 10px; }
.ts__cells { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; }
.cell {
  height: 32px; border: 1px solid var(--eh-border); border-radius: 8px; background: var(--eh-surface);
  font: inherit; font-size: 13px; cursor: pointer;
}
.cell--done { background: var(--eh-primary-soft); border-color: var(--el-color-primary-light-5); color: var(--eh-primary); font-weight: 600; }
.cell--now { outline: 2px solid var(--eh-primary); outline-offset: 1px; }
.cell--ok { background: #ecfdf5; border-color: #10b981; color: #047857; }
.cell--bad { background: #fef2f2; border-color: #ef4444; color: #b91c1c; }

.result { display: grid; justify-items: center; gap: 12px; text-align: center; max-width: 560px; margin: 0 auto; }
.estimate { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; width: 100%; }
.estimate div { display: grid; gap: 2px; padding: 12px; border-radius: 12px; background: var(--eh-bg); }
.estimate span { font-size: 12px; color: var(--eh-text-2); }
.estimate strong { font-size: 22px; }
.estimate__total { background: var(--eh-primary-soft) !important; color: var(--eh-primary); }
.note { font-size: 12px; }
.result__actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: center; }
.result__actions .el-button + .el-button { margin-left: 0; }

@media (max-width: 899.98px) {
  .ts__layout--test { grid-template-columns: 1fr; }
  .ts__map { position: static; order: -1; }
  .ts__cells { grid-template-columns: repeat(10, 1fr); }
}
@media (max-width: 479.98px) {
  .ts__bar { padding: 0 10px; gap: 8px; }
  .ts__cells { grid-template-columns: repeat(8, 1fr); }
  .estimate { grid-template-columns: 1fr; }
}
</style>
