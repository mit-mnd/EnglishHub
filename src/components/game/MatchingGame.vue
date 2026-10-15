<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Timer } from '@element-plus/icons-vue'
import { useSpeech } from '@/composables/useSpeech'
import { formatClock } from '@/utils/format'
import { matchingScore, shuffle } from '@/utils/game'

/**
 * Ghép từ - nghĩa: chọn 1 ô bên trái (từ) và 1 ô bên phải (nghĩa).
 * Đúng -> cặp mờ đi; sai -> rung đỏ, +1 lỗi. Giới hạn 10 giây / cặp.
 */
const props = defineProps({ words: { type: Array, required: true } })
const emit = defineEmits(['finish'])
const { pronounce } = useSpeech()

const SECONDS_PER_PAIR = 10
const timeLimitMs = props.words.length * SECONDS_PER_PAIR * 1000

const left = ref(shuffle(props.words))
const right = ref(shuffle(props.words))
const matched = ref(new Set())
const selectedLeft = ref(null)
const selectedRight = ref(null)
const wrongPair = ref(null)     // { left, right } đang rung đỏ
const mistakes = ref(0)
const timeLeftMs = ref(timeLimitMs)
let timer = null
let deadline = 0
let finished = false

const progress = computed(() => (timeLeftMs.value / timeLimitMs) * 100)

function select(side, word) {
  if (finished || matched.value.has(word.wordId) || wrongPair.value) return
  if (side === 'left') selectedLeft.value = selectedLeft.value === word.wordId ? null : word.wordId
  else selectedRight.value = selectedRight.value === word.wordId ? null : word.wordId
  if (selectedLeft.value != null && selectedRight.value != null) check()
}

function check() {
  const l = selectedLeft.value
  const r = selectedRight.value
  if (l === r) {
    matched.value = new Set(matched.value).add(l)
    const w = props.words.find((x) => x.wordId === l)
    pronounce(w.headword, w.audioUrl)
    selectedLeft.value = selectedRight.value = null
    if (matched.value.size === props.words.length) finish()
  } else {
    mistakes.value++
    wrongPair.value = { left: l, right: r }
    setTimeout(() => {
      wrongPair.value = null
      selectedLeft.value = selectedRight.value = null
    }, 600)
  }
}

function finish() {
  if (finished) return
  finished = true
  clearInterval(timer)
  const correct = matched.value.size
  emit('finish', {
    score: matchingScore({ matched: correct, total: props.words.length, mistakes: mistakes.value, timeLeftMs: timeLeftMs.value, timeLimitMs }),
    correctCount: correct,
    totalCount: props.words.length,
    details: props.words.map((w) => ({ ...w, ok: matched.value.has(w.wordId) })),
    note: mistakes.value ? `${mistakes.value} lần ghép sai` : 'Không ghép sai lần nào',
  })
}

const stateOf = (side, w) => ({
  'tile--matched': matched.value.has(w.wordId),
  'tile--selected': (side === 'left' ? selectedLeft.value : selectedRight.value) === w.wordId,
  'tile--wrong': wrongPair.value && wrongPair.value[side] === w.wordId,
})

onMounted(() => {
  deadline = Date.now() + timeLimitMs
  timer = setInterval(() => {
    timeLeftMs.value = Math.max(0, deadline - Date.now())
    if (timeLeftMs.value === 0) finish()
  }, 200)
})
onUnmounted(() => clearInterval(timer))
</script>

<template>
  <div class="matching">
    <div class="matching__status">
      <span class="matching__clock" :class="{ 'matching__clock--low': timeLeftMs < 10000 }">
        <el-icon><Timer /></el-icon> {{ formatClock(timeLeftMs) }}
      </span>
      <el-progress class="matching__bar" :percentage="progress" :show-text="false" :stroke-width="8"
                   :color="timeLeftMs < 10000 ? '#ef4444' : '#4f46e5'" />
      <span class="eh-subtle">{{ matched.size }}/{{ words.length }} cặp · {{ mistakes }} lỗi</span>
    </div>

    <div class="matching__board">
      <div class="matching__col" role="group" aria-label="Từ tiếng Anh">
        <button v-for="w in left" :key="w.wordId" type="button" class="tile tile--word" :class="stateOf('left', w)"
                :disabled="matched.has(w.wordId)" @click="select('left', w)">
          {{ w.headword }}
        </button>
      </div>
      <div class="matching__col" role="group" aria-label="Nghĩa">
        <button v-for="w in right" :key="w.wordId" type="button" class="tile" :class="stateOf('right', w)"
                :disabled="matched.has(w.wordId)" @click="select('right', w)">
          {{ w.meaning }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.matching { display: grid; gap: 16px; }
.matching__status { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.matching__clock { display: inline-flex; align-items: center; gap: 4px; font-weight: 700; font-variant-numeric: tabular-nums; }
.matching__clock--low { color: #ef4444; }
.matching__bar { flex: 1; min-width: 120px; }
.matching__board { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.matching__col { display: grid; gap: 10px; align-content: start; }

.tile {
  min-height: 56px; padding: 10px 14px; border-radius: 12px;
  border: 2px solid var(--eh-border); background: var(--eh-surface); color: var(--eh-text);
  font: inherit; font-size: 15px; text-align: left; line-height: 1.35; cursor: pointer;
  transition: border-color 0.15s, background-color 0.15s, opacity 0.3s, transform 0.15s;
  overflow-wrap: anywhere;
}
.tile--word { font-weight: 600; font-size: 16px; }
.tile:hover:not(:disabled) { border-color: var(--eh-primary); }
.tile--selected { border-color: var(--eh-primary); background: var(--eh-primary-soft); transform: scale(1.02); }
.tile--wrong { border-color: #ef4444; background: #fee2e2; animation: shake 0.4s; }
.tile--matched { border-color: #10b981; background: #d1fae5; opacity: 0.45; cursor: default; }

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-6px); }
  75% { transform: translateX(6px); }
}
@media (max-width: 480px) {
  .tile { font-size: 14px; padding: 8px 10px; min-height: 52px; }
  .tile--word { font-size: 15px; }
}
</style>
