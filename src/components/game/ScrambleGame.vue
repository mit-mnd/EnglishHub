<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { Close, MagicStick, Right } from '@element-plus/icons-vue'
import { useSpeech } from '@/composables/useSpeech'
import { pointsScore, scrambleLetters } from '@/utils/game'

/**
 * Xếp chữ: bấm (hoặc gõ phím) các chữ cái để ghép thành từ đúng.
 * Đúng không gợi ý = 1 điểm, dùng gợi ý = 0.5, bỏ qua = 0. Đủ số chữ thì tự kiểm tra.
 */
const props = defineProps({ words: { type: Array, required: true } })
const emit = defineEmits(['finish'])
const { pronounce } = useSpeech()

const index = ref(0)
const tiles = ref([])
const picked = ref([])           // ô chữ đã chọn theo thứ tự
const hinted = ref(false)
const state = ref('playing')     // playing | wrong | correct | revealed
const points = []
const results = []

const word = computed(() => props.words[index.value])
const target = computed(() => word.value.headword.toLowerCase())
const available = computed(() => tiles.value.filter((t) => !picked.value.includes(t)))

function setup() {
  tiles.value = scrambleLetters(word.value.headword)
  picked.value = []
  hinted.value = false
  state.value = 'playing'
}

function pick(tile) {
  if (state.value === 'correct' || state.value === 'revealed') return
  state.value = 'playing'
  picked.value = [...picked.value, tile]
  if (picked.value.length === target.value.length) check()
}

function unpick(tile) {
  if (state.value === 'correct' || state.value === 'revealed') return
  // Không cho gỡ chữ cái đầu đã được gợi ý
  if (hinted.value && picked.value[0] === tile) return
  state.value = 'playing'
  picked.value = picked.value.filter((t) => t !== tile)
}

/** Gợi ý: đặt sẵn chữ cái đầu tiên đúng vị trí. */
function hint() {
  if (hinted.value || (state.value !== 'playing' && state.value !== 'wrong')) return
  const first = tiles.value.find((t) => t.ch === target.value[0])
  hinted.value = true
  state.value = 'playing'
  picked.value = [first]
}

function check() {
  if (picked.value.map((t) => t.ch).join('') === target.value) {
    state.value = 'correct'
    record(hinted.value ? 0.5 : 1)
    pronounce(word.value.headword, word.value.audioUrl)
    setTimeout(next, 1100)
  } else {
    state.value = 'wrong'
  }
}

function skip() {
  state.value = 'revealed'
  const pool = [...tiles.value]   // chữ trùng (vd "apple" có 2 chữ p) phải lấy 2 ô khác nhau
  picked.value = [...target.value].map((ch) => pool.splice(pool.findIndex((t) => t.ch === ch), 1)[0])
  record(0)
  pronounce(word.value.headword, word.value.audioUrl)
}

function record(p) {
  points[index.value] = p
  results[index.value] = { ...word.value, ok: p > 0 }
}

function next() {
  if (index.value + 1 >= props.words.length) {
    const correct = points.filter((p) => p > 0).length
    emit('finish', {
      score: pointsScore(points, props.words.length),
      correctCount: correct,
      totalCount: props.words.length,
      details: results,
      note: `${points.filter((p) => p === 0.5).length} từ dùng gợi ý`,
    })
    return
  }
  index.value++
  setup()
}

/** Gõ phím chữ cái chọn ô tương ứng, Backspace gỡ chữ cuối, Enter sang từ tiếp khi đã lộ đáp án. */
function onKey(e) {
  if (e.ctrlKey || e.metaKey || e.altKey) return
  if (state.value === 'revealed' && e.key === 'Enter') return next()
  if (e.key === 'Backspace' && picked.value.length) {
    e.preventDefault()
    return unpick(picked.value[picked.value.length - 1])
  }
  if (/^[a-z]$/i.test(e.key)) {
    const tile = available.value.find((t) => t.ch === e.key.toLowerCase())
    if (tile) pick(tile)
  }
}

setup()
onMounted(() => window.addEventListener('keydown', onKey))
onUnmounted(() => window.removeEventListener('keydown', onKey))
</script>

<template>
  <div class="scramble">
    <div class="scramble__head">
      <span class="eh-subtle">Từ {{ index + 1 }}/{{ words.length }}</span>
      <el-progress class="scramble__bar" :percentage="(index / words.length) * 100" :show-text="false" :stroke-width="8" />
    </div>

    <div class="scramble__clue">
      <span class="scramble__pos">{{ word.partOfSpeech }}</span>
      <p class="scramble__meaning">{{ word.meaning }}</p>
      <p class="eh-subtle">{{ target.length }} chữ cái</p>
    </div>

    <div class="slots" :class="{ 'slots--wrong': state === 'wrong', 'slots--correct': state === 'correct' }" aria-live="polite">
      <button v-for="i in target.length" :key="i" type="button" class="slot" :class="{ 'slot--hint': hinted && i === 1 }"
              :disabled="!picked[i - 1]" @click="picked[i - 1] && unpick(picked[i - 1])">
        {{ picked[i - 1]?.ch ?? '' }}
      </button>
    </div>

    <div class="letters">
      <button v-for="t in tiles" :key="t.id" type="button" class="letter" :class="{ 'letter--used': picked.includes(t) }"
              :disabled="picked.includes(t) || state === 'correct' || state === 'revealed'" @click="pick(t)">
        {{ t.ch }}
      </button>
    </div>

    <p v-if="state === 'wrong'" class="scramble__msg scramble__msg--wrong">Chưa đúng - bấm vào chữ ở ô trên để gỡ ra và thử lại.</p>
    <p v-else-if="state === 'correct'" class="scramble__msg scramble__msg--ok">Chính xác! <strong>{{ word.headword }}</strong></p>
    <div v-else-if="state === 'revealed'" class="scramble__msg">
      Đáp án: <strong>{{ word.headword }}</strong> <span v-if="word.phonetic" class="eh-subtle">{{ word.phonetic }}</span>
      <p v-if="word.example" class="eh-subtle scramble__example">{{ word.example }}</p>
    </div>

    <div class="scramble__actions">
      <template v-if="state !== 'revealed'">
        <el-button :icon="MagicStick" :disabled="hinted || state === 'correct'" @click="hint">Gợi ý chữ đầu</el-button>
        <el-button :icon="Close" :disabled="state === 'correct'" @click="skip">Bỏ qua</el-button>
      </template>
      <el-button v-else type="primary" :icon="Right" @click="next">
        {{ index + 1 >= words.length ? 'Xem kết quả' : 'Từ tiếp theo' }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.scramble { display: grid; gap: 20px; justify-items: center; text-align: center; }
.scramble__head { width: 100%; display: flex; align-items: center; gap: 12px; }
.scramble__bar { flex: 1; }
.scramble__clue { display: grid; gap: 4px; justify-items: center; }
.scramble__pos { font-size: 12px; font-style: italic; color: var(--eh-text-3); }
.scramble__meaning { font-size: 20px; font-weight: 600; max-width: 520px; }

.slots, .letters { display: flex; flex-wrap: wrap; justify-content: center; gap: 8px; }
.slot, .letter {
  width: 44px; height: 52px; border-radius: 10px; font: inherit; font-size: 22px; font-weight: 700;
  text-transform: lowercase; cursor: pointer;
}
.slot { border: 2px dashed var(--eh-border); background: var(--eh-surface); color: var(--eh-text); }
.slot:disabled { cursor: default; }
.slot--hint { border-style: solid; border-color: var(--eh-accent); background: var(--eh-highlight); }
.slots--wrong .slot { border-color: var(--eh-danger); animation: shake 0.4s; }
.slots--correct .slot { border-style: solid; border-color: var(--eh-success); background: var(--eh-success-soft); }
.letter {
  border: 0; background: var(--eh-primary); color: var(--eh-on-primary);
  box-shadow: 0 3px 0 color-mix(in srgb, var(--eh-primary) 62%, #000); transition: transform 0.1s, opacity 0.15s;
}
.letter:active:not(:disabled) { transform: translateY(2px); box-shadow: 0 1px 0 color-mix(in srgb, var(--eh-primary) 62%, #000); }
.letter--used { opacity: 0.2; box-shadow: none; cursor: default; }

.scramble__msg { min-height: 24px; }
.scramble__msg--wrong { color: var(--eh-danger-text); }
.scramble__msg--ok { color: var(--eh-success-text); }
.scramble__example { font-style: italic; margin-top: 4px; }
.scramble__actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-5px); }
  75% { transform: translateX(5px); }
}
@media (max-width: 480px) {
  .slot, .letter { width: 36px; height: 44px; font-size: 18px; }
  .slots, .letters { gap: 6px; }
}
</style>
