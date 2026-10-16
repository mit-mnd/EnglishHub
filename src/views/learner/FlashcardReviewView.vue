<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, Loading, Setting } from '@element-plus/icons-vue'
import AppLogo from '@/components/common/AppLogo.vue'
import FlashCard from '@/components/flashcard/FlashCard.vue'
import { flashcardApi } from '@/api'
import { useBreakpoint } from '@/composables/useBreakpoint'
import { useSpeech } from '@/composables/useSpeech'
import { useDashboardStore } from '@/stores/dashboard'
import { formatDateTime, formatDuration, formatInterval } from '@/utils/format'
import { storage } from '@/utils/storage'

const router = useRouter()
const dashboard = useDashboardStore()
const { isMobile } = useBreakpoint()
const { pronounce } = useSpeech()

const GRADES = [
  { key: 'AGAIN', label: 'Quên', hint: 'Không nhớ', type: 'danger' },
  { key: 'HARD', label: 'Khó', hint: 'Nhớ nhưng rất chật vật', type: 'warning' },
  { key: 'GOOD', label: 'Được', hint: 'Nhớ sau một chút suy nghĩ', type: 'primary' },
  { key: 'EASY', label: 'Dễ', hint: 'Nhớ ngay lập tức', type: 'success' },
]
const RELEARN_GAP = 3   // thẻ "Quên" quay lại sau 3 thẻ khác

const queue = ref([])
const loading = ref(true)
const error = ref('')
const flipped = ref(false)
const grading = ref(false)
const done = ref(false)
const direction = ref(storage.get('eh.fc.direction') ?? 'EN_VI')
const autoSpeak = ref(storage.get('eh.fc.autoSpeak') !== 'false')
const stats = ref({ reviewed: 0, again: 0, startedAt: Date.now(), cardIds: new Set() })
const nextDueAt = ref(null)
let shownAt = Date.now()

const current = computed(() => queue.value[0])
const total = computed(() => stats.value.cardIds.size + queue.value.filter((c) => !stats.value.cardIds.has(c.userWordId)).length)
const progress = computed(() => (total.value ? Math.round((stats.value.cardIds.size / total.value) * 100) : 0))
const accuracy = computed(() => (stats.value.reviewed ? Math.round(((stats.value.reviewed - stats.value.again) / stats.value.reviewed) * 100) : 0))

async function load() {
  loading.value = true
  error.value = ''
  done.value = false
  try {
    const res = await flashcardApi.queue(20)
    queue.value = res.cards
    stats.value = { reviewed: 0, again: 0, startedAt: Date.now(), cardIds: new Set() }
    if (!queue.value.length) await finishSession()
    else showCard()
  } catch (e) {
    error.value = e.message
  } finally {
    loading.value = false
  }
}

function showCard() {
  flipped.value = false
  shownAt = Date.now()
}

function flip() {
  if (!current.value || grading.value) return
  flipped.value = !flipped.value
  if (flipped.value && autoSpeak.value) {
    const w = current.value.word
    pronounce(w.headword, w.audioUrlUs || w.audioUrlUk)
  }
}

async function grade(key) {
  if (!flipped.value || grading.value || !current.value) return
  grading.value = true
  const card = current.value
  try {
    const res = await flashcardApi.review(card.userWordId, key, Date.now() - shownAt)
    stats.value.reviewed += 1
    stats.value.cardIds.add(card.userWordId)
    if (key === 'AGAIN') stats.value.again += 1
    queue.value.shift()
    // Quên -> học lại trong phiên: chèn thẻ (đã cập nhật lịch) quay lại sau vài thẻ
    if (res.relearnInSession) queue.value.splice(Math.min(RELEARN_GAP, queue.value.length), 0, res.card)
    if (queue.value.length) showCard()
    else await finishSession()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    grading.value = false
  }
}

async function finishSession() {
  done.value = true
  dashboard.load()
  try {
    const summary = await flashcardApi.summary()
    nextDueAt.value = summary.dueNow + summary.newAvailableToday > 0 ? 'now' : summary.nextDueAt
  } catch {
    nextDueAt.value = null
  }
}

function saveSettings() {
  storage.set('eh.fc.direction', direction.value)
  storage.set('eh.fc.autoSpeak', String(autoSpeak.value))
}

/** Phím tắt: Space/Enter lật thẻ; 1-4 chọn mức đánh giá. */
function onKeydown(e) {
  if (e.target?.tagName === 'INPUT' || e.ctrlKey || e.metaKey || done.value) return
  if (e.code === 'Space' || e.key === 'Enter') {
    e.preventDefault()
    if (!flipped.value) flip()
  } else if (/^[1-4]$/.test(e.key) && flipped.value) {
    grade(GRADES[Number(e.key) - 1].key)
  }
}

onMounted(() => {
  load()
  window.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => window.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div class="review">
    <header class="review__head">
      <AppLogo :compact="isMobile" home />
      <div class="review__title">
        <strong>Ôn tập từ vựng</strong>
        <span v-if="!done && total" class="eh-subtle">{{ stats.cardIds.size }}/{{ total }} thẻ</span>
      </div>
      <el-popover placement="bottom-end" :width="260" trigger="click" @hide="saveSettings">
        <template #reference>
          <el-button circle text :icon="Setting" aria-label="Tùy chọn ôn tập" />
        </template>
        <div class="settings">
          <span class="eh-subtle">Mặt trước của thẻ</span>
          <el-radio-group v-model="direction" size="small">
            <el-radio-button value="EN_VI">Tiếng Anh</el-radio-button>
            <el-radio-button value="VI_EN">Nghĩa tiếng Việt</el-radio-button>
          </el-radio-group>
          <el-switch v-model="autoSpeak" active-text="Tự phát âm khi lật thẻ" />
        </div>
      </el-popover>
      <el-button circle text :icon="Close" aria-label="Thoát" @click="router.push({ name: 'vocabulary' })" />
    </header>
    <el-progress class="review__progress" :percentage="done ? 100 : progress" :show-text="false" :stroke-width="4" />

    <main class="review__body">
      <div v-if="loading" class="state"><el-icon class="is-loading" :size="32"><Loading /></el-icon></div>

      <el-result v-else-if="error" icon="error" title="Không tải được thẻ" :sub-title="error">
        <template #extra><el-button type="primary" @click="load">Thử lại</el-button></template>
      </el-result>

      <!-- Kết thúc phiên -->
      <section v-else-if="done" class="eh-card summary">
        <div class="summary__icon">{{ stats.reviewed ? '🎉' : '✅' }}</div>
        <h2>{{ stats.reviewed ? 'Hoàn thành phiên ôn tập!' : 'Không có thẻ nào cần ôn lúc này' }}</h2>
        <dl v-if="stats.reviewed" class="summary__stats">
          <div><dt>Thẻ đã ôn</dt><dd>{{ stats.cardIds.size }}</dd></div>
          <div><dt>Nhớ được</dt><dd>{{ accuracy }}%</dd></div>
          <div><dt>Thời gian</dt><dd>{{ formatDuration((Date.now() - stats.startedAt) / 1000) }}</dd></div>
        </dl>
        <p v-if="nextDueAt === 'now'" class="eh-muted">Vẫn còn thẻ cần ôn hôm nay.</p>
        <p v-else-if="nextDueAt" class="eh-muted">Thẻ tiếp theo đến hạn lúc {{ formatDateTime(nextDueAt) }}.</p>
        <p v-else class="eh-muted">Thêm từ mới từ bài đọc hoặc các bộ từ vựng để tiếp tục học.</p>
        <div class="summary__actions">
          <el-button @click="router.push({ name: 'vocabulary' })">Về trang Từ vựng</el-button>
          <el-button v-if="nextDueAt === 'now'" type="primary" @click="load">Ôn tiếp</el-button>
        </div>
      </section>

      <!-- Thẻ đang ôn -->
      <template v-else-if="current">
        <FlashCard :key="current.userWordId + '-' + stats.reviewed" :card="current" :flipped="flipped"
                   :direction="direction" @flip="flip" />

        <div class="grades" :class="{ 'grades--hidden': !flipped }" role="group" aria-label="Bạn nhớ từ này thế nào?">
          <button v-for="(g, i) in GRADES" :key="g.key" type="button" class="grade" :class="`grade--${g.type}`"
                  :disabled="!flipped || grading" :title="g.hint" @click="grade(g.key)">
            <strong>{{ g.label }}</strong>
            <span>{{ formatInterval(current.intervals[g.key]) }}</span>
            <kbd v-if="!isMobile">{{ i + 1 }}</kbd>
          </button>
        </div>
        <el-button v-if="!flipped" type="primary" size="large" class="show-btn" @click="flip">Xem đáp án</el-button>
      </template>
    </main>
  </div>
</template>

<style scoped>
.review { min-height: 100dvh; display: flex; flex-direction: column; background: var(--eh-bg); }
.review__head {
  display: flex; align-items: center; gap: 12px; height: 64px; padding: 0 24px;
  background: var(--eh-surface); border-bottom: 1px solid var(--eh-border);
}
.review__head .el-button + .el-button { margin-left: 0; }
.review__title { flex: 1; display: flex; flex-direction: column; line-height: 1.3; }
.review__progress :deep(.el-progress-bar__outer) { border-radius: 0; }
.review__progress :deep(.el-progress-bar__inner) { border-radius: 0; }
.review__body {
  flex: 1; width: 100%; max-width: 720px; margin: 0 auto; padding: 32px 20px;
  display: flex; flex-direction: column; align-items: center; gap: 20px;
}
.state { flex: 1; display: grid; place-items: center; }
.settings { display: grid; gap: 10px; }

.grades { display: grid; grid-template-columns: repeat(4, 1fr); gap: 10px; width: 100%; transition: opacity 0.2s; }
.grades--hidden { opacity: 0; pointer-events: none; }
.grade {
  position: relative; display: flex; flex-direction: column; align-items: center; gap: 2px; padding: 12px 8px;
  border-radius: 14px; border: 1.5px solid; background: var(--eh-surface); font: inherit; cursor: pointer;
  transition: transform 0.1s;
}
.grade:hover:not(:disabled) { transform: translateY(-2px); }
.grade:disabled { cursor: default; }
.grade strong { font-size: 16px; }
.grade span { font-size: 13px; opacity: 0.8; }
.grade kbd { position: absolute; top: 6px; right: 8px; font-size: 11px; opacity: 0.5; font-family: inherit; }
.grade--danger { border-color: var(--eh-danger-border); color: var(--eh-danger-text); }
.grade--warning { border-color: var(--eh-accent-border); color: var(--eh-accent-text); }
.grade--primary { border-color: var(--el-color-primary-light-7); color: var(--eh-primary); }
.grade--success { border-color: var(--eh-success-border); color: var(--eh-success-text); }
.show-btn { min-width: 220px; }

.summary { width: 100%; text-align: center; display: grid; gap: 12px; justify-items: center; padding: 36px 24px; }
.summary__icon { font-size: 52px; }
.summary h2 { font-size: 22px; }
.summary__stats { margin: 8px 0; display: flex; gap: 28px; }
.summary__stats div { display: flex; flex-direction: column; }
.summary__stats dt { font-size: 13px; color: var(--eh-text-2); }
.summary__stats dd { margin: 0; font-size: 24px; font-weight: 700; }
.summary__actions { display: flex; gap: 10px; flex-wrap: wrap; justify-content: center; }
.summary__actions .el-button + .el-button { margin-left: 0; }

@media (max-width: 599.98px) {
  .review__head { padding: 0 12px; }
  .review__body { padding: 20px 12px calc(20px + env(safe-area-inset-bottom)); }
  .grades { grid-template-columns: repeat(2, 1fr); }
}
</style>
