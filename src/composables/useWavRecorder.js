import { onBeforeUnmount, ref } from 'vue'
import { downsample, encodeWav, mergeChunks, rms, TARGET_SAMPLE_RATE, trimSilence } from '@/utils/wav'

/** AudioWorklet chép từng khung âm thanh từ micro về luồng chính (nạp từ Blob URL, không cần file riêng). */
const WORKLET_SOURCE = `
class EhRecorder extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0]
    if (channel) this.port.postMessage(channel.slice(0))
    return true
  }
}
registerProcessor('eh-recorder', EhRecorder)`

let workletUrl = null
const getWorkletUrl = () => (workletUrl ??= URL.createObjectURL(new Blob([WORKLET_SOURCE], { type: 'application/javascript' })))

/**
 * Ghi âm từ micro thành WAV 16 kHz mono.
 * state: idle | requesting | recording | processing | error
 */
export function useWavRecorder({ maxSeconds = 30 } = {}) {
  const state = ref('idle')
  const error = ref('')
  const level = ref(0)          // 0..1 cho thanh mức âm
  const elapsedMs = ref(0)

  let context = null
  let stream = null
  let chunks = []
  let startedAt = 0
  let timer = null
  const maxMs = maxSeconds * 1000

  const supported = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia
    && typeof window !== 'undefined' && 'AudioWorkletNode' in window

  async function start() {
    error.value = ''
    if (!supported) {
      error.value = 'Trình duyệt không hỗ trợ ghi âm. Hãy dùng Chrome, Edge hoặc Safari bản mới.'
      state.value = 'error'
      return false
    }
    state.value = 'requesting'
    try {
      stream = await navigator.mediaDevices.getUserMedia({
        audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      })
    } catch (e) {
      error.value = e.name === 'NotAllowedError'
        ? 'Bạn chưa cho phép dùng micro. Hãy bấm biểu tượng ổ khóa trên thanh địa chỉ để cấp quyền.'
        : 'Không tìm thấy micro trên thiết bị này.'
      state.value = 'error'
      return false
    }
    context = new AudioContext()
    await context.audioWorklet.addModule(getWorkletUrl())
    const source = context.createMediaStreamSource(stream)
    const node = new AudioWorkletNode(context, 'eh-recorder')
    chunks = []
    node.port.onmessage = (event) => {
      chunks.push(event.data)
      level.value = Math.min(1, rms(event.data) * 6)
    }
    source.connect(node)
    node.connect(context.destination)   // processor không xuất âm -> không vọng tiếng ra loa
    startedAt = performance.now()
    elapsedMs.value = 0
    // Component theo dõi elapsedMs >= maxMs để tự dừng
    timer = setInterval(() => { elapsedMs.value = performance.now() - startedAt }, 100)
    state.value = 'recording'
    return true
  }

  /** Dừng và trả về { blob, durationMs } (WAV 16 kHz); null nếu không có tiếng. */
  async function stop() {
    if (state.value !== 'recording') return null
    state.value = 'processing'
    const sampleRate = context.sampleRate
    cleanup()
    const trimmed = trimSilence(mergeChunks(chunks), sampleRate)
    chunks = []
    if (trimmed.length < sampleRate * 0.4) {
      error.value = 'Không nghe thấy giọng nói. Hãy nói to, rõ và đặt micro gần hơn.'
      state.value = 'error'
      return null
    }
    const samples = downsample(trimmed, sampleRate, TARGET_SAMPLE_RATE)
    state.value = 'idle'
    return {
      blob: new Blob([encodeWav(samples, TARGET_SAMPLE_RATE)], { type: 'audio/wav' }),
      durationMs: Math.round((samples.length / TARGET_SAMPLE_RATE) * 1000),
    }
  }

  function cancel() {
    cleanup()
    chunks = []
    state.value = 'idle'
  }

  function cleanup() {
    clearInterval(timer)
    level.value = 0
    stream?.getTracks().forEach((t) => t.stop())
    context?.close().catch(() => {})
    stream = null
    context = null
  }

  onBeforeUnmount(cleanup)

  return { state, error, level, elapsedMs, maxMs, supported, start, stop, cancel }
}
