<script setup>
import { computed, nextTick, onMounted, ref } from 'vue'
import { CircleCheck, CircleClose, Headset, Right, VideoPlay } from '@element-plus/icons-vue'
import { useSpeech } from '@/composables/useSpeech'
import { checkDictation, pointsScore } from '@/utils/game'

/**
 * Nghe & chép: phát âm từ (file audio từ điển hoặc giọng đọc trình duyệt), học viên gõ lại.
 * Đúng = 1 điểm, sai 1 ký tự với từ dài (>= 5 chữ) = 0.5 điểm, còn lại 0.
 */
const props = defineProps({ words: { type: Array, required: true } })
const emit = defineEmits(['finish'])
const { supported, speak, pronounce } = useSpeech()

const index = ref(0)
const answer = ref('')
const verdict = ref(null)        // null | CORRECT | CLOSE | WRONG
const input = ref(null)
const points = []
const results = []

const word = computed(() => props.words[index.value])
const POINTS = { CORRECT: 1, CLOSE: 0.5, WRONG: 0 }

const play = () => pronounce(word.value.headword, word.value.audioUrl)
const playSlow = () => speak(word.value.headword, 'US', 0.55)

async function focusInput() {
  await nextTick()
  input.value?.focus()
}

function submit() {
  if (verdict.value) return next()
  if (!answer.value.trim()) return
  verdict.value = checkDictation(answer.value, word.value.headword)
  points[index.value] = POINTS[verdict.value]
  results[index.value] = { ...word.value, ok: verdict.value !== 'WRONG', answer: answer.value.trim() }
}

function next() {
  if (index.value + 1 >= props.words.length) {
    emit('finish', {
      score: pointsScore(points, props.words.length),
      correctCount: points.filter((p) => p === 1).length,
      totalCount: props.words.length,
      details: results,
      note: `${points.filter((p) => p === 0.5).length} từ gần đúng (sai 1 ký tự)`,
    })
    return
  }
  index.value++
  answer.value = ''
  verdict.value = null
  play()
  focusInput()
}

onMounted(() => {
  play()
  focusInput()
})
</script>

<template>
  <div class="dictation">
    <div class="dictation__head">
      <span class="eh-subtle">Từ {{ index + 1 }}/{{ words.length }}</span>
      <el-progress class="dictation__bar" :percentage="(index / words.length) * 100" :show-text="false" :stroke-width="8" />
    </div>

    <div class="dictation__player">
      <button type="button" class="dictation__play" aria-label="Nghe lại" @click="play">
        <el-icon :size="40"><Headset /></el-icon>
      </button>
      <div class="dictation__controls">
        <el-button :icon="VideoPlay" round @click="play">Nghe lại</el-button>
        <el-button v-if="supported" round @click="playSlow">Nghe chậm</el-button>
      </div>
      <p class="eh-subtle">Gợi ý: {{ word.meaning }}</p>
    </div>

    <form class="dictation__form" @submit.prevent="submit">
      <el-input ref="input" v-model="answer" size="large" placeholder="Gõ từ bạn nghe được…" :readonly="!!verdict"
                autocomplete="off" autocapitalize="off" spellcheck="false" aria-label="Từ bạn nghe được"
                :class="verdict && `dictation__input--${verdict.toLowerCase()}`" />
      <el-button type="primary" size="large" native-type="submit" :icon="verdict ? Right : undefined" :disabled="!verdict && !answer.trim()">
        {{ !verdict ? 'Kiểm tra' : index + 1 >= words.length ? 'Xem kết quả' : 'Tiếp' }}
      </el-button>
    </form>

    <div v-if="verdict" class="dictation__verdict" :class="`dictation__verdict--${verdict.toLowerCase()}`" aria-live="polite">
      <el-icon :size="22"><CircleCheck v-if="verdict !== 'WRONG'" /><CircleClose v-else /></el-icon>
      <div>
        <strong v-if="verdict === 'CORRECT'">Chính xác!</strong>
        <strong v-else-if="verdict === 'CLOSE'">Gần đúng (sai 1 ký tự) - được nửa điểm</strong>
        <strong v-else>Chưa đúng</strong>
        <p>
          <span class="dictation__word">{{ word.headword }}</span>
          <span v-if="word.phonetic" class="eh-subtle"> {{ word.phonetic }}</span>
          <span class="eh-subtle"> · {{ word.partOfSpeech }}</span>
        </p>
        <p v-if="word.example" class="eh-subtle dictation__example">{{ word.example }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dictation { display: grid; gap: 20px; }
.dictation__head { display: flex; align-items: center; gap: 12px; }
.dictation__bar { flex: 1; }
.dictation__player { display: grid; justify-items: center; gap: 12px; text-align: center; }
.dictation__play {
  display: grid; place-items: center; width: 96px; height: 96px; border-radius: 50%;
  border: 0; background: linear-gradient(135deg, #06b6d4, #0891b2); color: #fff; cursor: pointer;
  box-shadow: 0 8px 24px rgba(8, 145, 178, 0.35); transition: transform 0.15s;
}
.dictation__play:hover { transform: scale(1.05); }
.dictation__controls { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
.dictation__form { display: flex; gap: 8px; max-width: 480px; width: 100%; margin: 0 auto; }
.dictation__form :deep(.el-input__inner) { font-size: 18px; letter-spacing: 0.5px; }
.dictation__input--correct :deep(.el-input__wrapper) { box-shadow: 0 0 0 2px #10b981 inset; }
.dictation__input--close :deep(.el-input__wrapper) { box-shadow: 0 0 0 2px #f59e0b inset; }
.dictation__input--wrong :deep(.el-input__wrapper) { box-shadow: 0 0 0 2px #ef4444 inset; }

.dictation__verdict {
  display: flex; gap: 10px; align-items: flex-start; max-width: 480px; width: 100%; margin: 0 auto;
  padding: 12px 14px; border-radius: 12px;
}
.dictation__verdict--correct { background: #d1fae5; color: #065f46; }
.dictation__verdict--close { background: #fef3c7; color: #92400e; }
.dictation__verdict--wrong { background: #fee2e2; color: #991b1b; }
.dictation__word { font-size: 18px; font-weight: 700; margin-right: 6px; }
.dictation__example { font-style: italic; margin-top: 2px; }
</style>
