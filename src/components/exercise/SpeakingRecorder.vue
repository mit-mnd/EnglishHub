<script setup>
/**
 * Luyện nói một câu: nghe mẫu -> thu âm (WAV 16 kHz) -> gửi chấm -> xem điểm từng từ.
 * Có thể thu âm nhiều lần; mỗi lần chấm xong phát sự kiện "scored" với kết quả mới nhất.
 */
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { Headset, Loading, Microphone, RefreshRight, VideoPause, VideoPlay } from '@element-plus/icons-vue'
import ScoreRing from './ScoreRing.vue'
import { speakingApi } from '@/api'
import { useSpeech } from '@/composables/useSpeech'
import { useSpeechRecognition } from '@/composables/useSpeechRecognition'
import { useWavRecorder } from '@/composables/useWavRecorder'
import { formatClock } from '@/utils/format'
import { scoreColor } from '@/utils/feedback'

const props = defineProps({
  referenceText: { type: String, required: true },
  questionId: { type: Number, default: null },
  lessonId: { type: Number, default: null },
  disabled: { type: Boolean, default: false },
})
const emit = defineEmits(['scored'])

const recorder = useWavRecorder({ maxSeconds: 30 })
const recognition = useSpeechRecognition()
const { speak } = useSpeech()

const result = ref(null)
const uploading = ref(false)
const uploadError = ref('')
const recordingUrl = ref(null)
const playing = ref(false)
let player = null

const recording = computed(() => recorder.state.value === 'recording')
const busy = computed(() => uploading.value || ['requesting', 'processing'].includes(recorder.state.value))
const errorMessage = computed(() => uploadError.value || recorder.error.value)

const WORD_CLASS = { NONE: 'ok', MISPRONUNCIATION: 'warn', OMISSION: 'miss', INSERTION: 'warn', UNKNOWN: 'unknown' }
const bars = computed(() => [0.6, 1, 0.8, 1, 0.6].map((k) => Math.max(0.12, Math.min(1, recorder.level.value * k * 1.6))))

async function toggle() {
  if (recording.value) await finish()
  else await begin()
}

async function begin() {
  uploadError.value = ''
  if (await recorder.start()) recognition.start('en-US')
}

async function finish() {
  const [recorded, transcript] = await Promise.all([recorder.stop(), recognition.stop()])
  if (!recorded) return
  setRecording(recorded.blob)
  uploading.value = true
  try {
    const form = new FormData()
    form.append('audio', recorded.blob, 'recording.wav')
    if (props.questionId) form.append('questionId', props.questionId)
    else form.append('referenceText', props.referenceText)
    if (props.lessonId) form.append('lessonId', props.lessonId)
    if (transcript) form.append('clientTranscript', transcript)
    result.value = await speakingApi.submit(form)
    emit('scored', result.value)
  } catch (e) {
    uploadError.value = e.message
  } finally {
    uploading.value = false
  }
}

function setRecording(blob) {
  if (recordingUrl.value) URL.revokeObjectURL(recordingUrl.value)
  recordingUrl.value = URL.createObjectURL(blob)
}

function togglePlayback() {
  if (playing.value) {
    player?.pause()
    return
  }
  player = new Audio(recordingUrl.value)
  player.onended = player.onpause = () => { playing.value = false }
  player.play().then(() => { playing.value = true }).catch(() => {})
}

// Quá thời lượng tối đa thì tự dừng & chấm
watch(recorder.elapsedMs, (ms) => {
  if (recording.value && ms >= recorder.maxMs) finish()
})

onBeforeUnmount(() => {
  recognition.abort()
  player?.pause()
  if (recordingUrl.value) URL.revokeObjectURL(recordingUrl.value)
})
</script>

<template>
  <div class="speaking">
    <div class="reference">
      <span class="eh-subtle">Đọc to câu sau:</span>
      <p class="reference__text">{{ referenceText }}</p>
      <div class="reference__actions">
        <el-button size="small" round :icon="Headset" @click="speak(referenceText, 'US')">Nghe mẫu</el-button>
        <el-button size="small" round text @click="speak(referenceText, 'US', 0.65)">Nghe chậm</el-button>
      </div>
    </div>

    <div class="recorder">
      <button type="button" class="mic" :class="{ 'mic--on': recording }" :disabled="disabled || busy"
              :aria-label="recording ? 'Dừng thu âm' : 'Bắt đầu thu âm'" @click="toggle">
        <el-icon v-if="busy" class="is-loading" :size="30"><Loading /></el-icon>
        <el-icon v-else-if="recording" :size="30"><VideoPause /></el-icon>
        <el-icon v-else :size="30"><Microphone /></el-icon>
      </button>
      <div class="recorder__info">
        <template v-if="recording">
          <div class="level" aria-hidden="true">
            <span v-for="(h, i) in bars" :key="i" :style="{ transform: `scaleY(${h})` }" />
          </div>
          <span class="recorder__time">{{ formatClock(recorder.elapsedMs.value) }} / {{ formatClock(recorder.maxMs) }}</span>
          <span class="eh-subtle">Đang thu âm… bấm lần nữa để dừng</span>
        </template>
        <span v-else-if="uploading" class="eh-muted">Đang chấm phát âm…</span>
        <span v-else-if="recorder.state.value === 'requesting'" class="eh-muted">Đang xin quyền dùng micro…</span>
        <template v-else>
          <strong>{{ result ? 'Thu âm lại' : 'Bấm để thu âm' }}</strong>
          <span class="eh-subtle">Tối đa 30 giây. Nói rõ ràng, tốc độ tự nhiên.</span>
        </template>
      </div>
    </div>

    <el-alert v-if="errorMessage" type="error" :title="errorMessage" show-icon :closable="false" />
    <el-alert v-else-if="!recognition.supported && !result" type="info" show-icon :closable="false"
              title="Trình duyệt này không tự nhận dạng giọng nói nên điểm chỉ mang tính ước lượng. Dùng Chrome/Edge để được chấm từng từ." />

    <section v-if="result" class="result">
      <div class="result__top">
        <ScoreRing :score="Number(result.pronunciationScore)" :size="92" label="Phát âm" />
        <div class="metrics">
          <div v-for="m in [
            { key: 'accuracyScore', label: 'Độ chính xác' },
            { key: 'fluencyScore', label: 'Độ trôi chảy' },
            { key: 'completenessScore', label: 'Độ đầy đủ' },
          ]" :key="m.key" class="metric">
            <span>{{ m.label }}</span>
            <el-progress :percentage="Number(result[m.key] ?? 0)" :stroke-width="8"
                         :color="scoreColor(result[m.key] == null ? null : Number(result[m.key]))"
                         :format="() => (result[m.key] == null ? '—' : Math.round(result[m.key]))" />
          </div>
        </div>
      </div>

      <div class="words" aria-label="Kết quả từng từ">
        <span v-for="(w, i) in result.words" :key="i" class="word" :class="`word--${WORD_CLASS[w.errorType] ?? 'unknown'}`"
              :title="w.errorType === 'OMISSION' ? 'Bỏ sót' : w.errorType === 'MISPRONUNCIATION' ? 'Phát âm chưa đúng' : ''">
          {{ w.word }}
        </span>
      </div>
      <p v-if="result.recognizedText" class="eh-subtle">Máy nghe được: “{{ result.recognizedText }}”</p>

      <ul v-if="result.tips.length" class="tips">
        <li v-for="tip in result.tips" :key="tip">{{ tip }}</li>
      </ul>

      <div class="result__actions">
        <el-button v-if="recordingUrl" size="small" :icon="playing ? VideoPause : VideoPlay" @click="togglePlayback">
          {{ playing ? 'Dừng' : 'Nghe lại giọng của bạn' }}
        </el-button>
        <el-button size="small" :icon="RefreshRight" :disabled="disabled || busy" @click="begin">Thu âm lại</el-button>
        <el-tooltip v-if="result.provider === 'MOCK'"
                    content="Đang dùng bộ chấm thử nghiệm. Kết nối Azure Speech để chấm phát âm theo âm vị.">
          <el-tag size="small" type="info" effect="plain">Chấm thử nghiệm</el-tag>
        </el-tooltip>
      </div>
    </section>
  </div>
</template>

<style scoped>
.speaking { display: grid; gap: 16px; }
.reference { padding: 16px 18px; border-radius: 14px; background: linear-gradient(135deg, #fff7ed, #ffedd5); }
.reference__text { font-size: 22px; font-weight: 600; line-height: 1.4; margin: 4px 0 10px; color: #9a3412; }
.reference__actions { display: flex; gap: 6px; }
.reference__actions .el-button + .el-button { margin-left: 0; }

.recorder { display: flex; align-items: center; gap: 16px; }
.mic {
  display: grid; place-items: center; flex-shrink: 0; width: 72px; height: 72px; border-radius: 50%; border: 0;
  background: #f97316; color: #fff; cursor: pointer; box-shadow: 0 8px 20px rgba(249, 115, 22, 0.35);
  transition: transform 0.15s, background-color 0.15s;
}
.mic:hover:not(:disabled) { transform: scale(1.05); }
.mic:disabled { opacity: 0.6; cursor: not-allowed; }
.mic--on { background: var(--eh-danger); animation: pulse 1.2s ease-in-out infinite; }
@keyframes pulse { 0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.45); } 50% { box-shadow: 0 0 0 14px rgba(239, 68, 68, 0); } }
.recorder__info { display: flex; flex-direction: column; gap: 4px; }
.recorder__time { font-weight: 700; font-variant-numeric: tabular-nums; }
.level { display: flex; align-items: center; gap: 4px; height: 28px; }
.level span { width: 6px; height: 28px; border-radius: 3px; background: var(--eh-danger); transform-origin: center; transition: transform 0.08s; }

.result { display: grid; gap: 14px; padding: 16px; border: 1px solid var(--eh-border); border-radius: 14px; }
.result__top { display: flex; align-items: center; gap: 20px; flex-wrap: wrap; }
.metrics { flex: 1; min-width: 200px; display: grid; gap: 8px; }
.metric { display: grid; grid-template-columns: 110px 1fr; align-items: center; gap: 8px; font-size: 14px; }
.words { display: flex; flex-wrap: wrap; gap: 6px; }
.word { padding: 4px 10px; border-radius: 8px; font-size: 17px; font-weight: 500; }
.word--ok { background: var(--eh-success-soft); color: #047857; }
.word--warn { background: #fff7ed; color: #c2410c; text-decoration: underline wavy #f97316; text-underline-offset: 4px; }
.word--miss { background: var(--eh-danger-soft); color: #b91c1c; text-decoration: line-through; }
.word--unknown { background: var(--eh-bg); color: var(--eh-text-2); }
.tips { margin: 0; padding-left: 18px; display: grid; gap: 4px; font-size: 14px; color: var(--eh-text-2); }
.result__actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.result__actions .el-button + .el-button { margin-left: 0; }
@media (max-width: 599.98px) {
  .reference__text { font-size: 19px; }
  .metric { grid-template-columns: 1fr; gap: 2px; }
}
</style>
