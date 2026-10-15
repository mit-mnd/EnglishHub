/**
 * Xử lý âm thanh ghi từ micro -> WAV PCM 16-bit mono 16 kHz (định dạng dịch vụ chấm phát âm yêu cầu).
 */

export const TARGET_SAMPLE_RATE = 16000

/** Ghép các đoạn Float32Array nhận từ AudioWorklet. */
export function mergeChunks(chunks) {
  const length = chunks.reduce((sum, c) => sum + c.length, 0)
  const result = new Float32Array(length)
  let offset = 0
  for (const chunk of chunks) {
    result.set(chunk, offset)
    offset += chunk.length
  }
  return result
}

/** Giảm tần số lấy mẫu bằng cách lấy trung bình từng nhóm mẫu (đủ tốt cho giọng nói). */
export function downsample(samples, fromRate, toRate = TARGET_SAMPLE_RATE) {
  if (fromRate === toRate) return samples
  if (fromRate < toRate) throw new Error('Không hỗ trợ tăng tần số lấy mẫu')
  const ratio = fromRate / toRate
  const length = Math.floor(samples.length / ratio)
  const result = new Float32Array(length)
  for (let i = 0; i < length; i++) {
    const start = Math.floor(i * ratio)
    const end = Math.min(samples.length, Math.floor((i + 1) * ratio))
    let sum = 0
    for (let j = start; j < end; j++) sum += samples[j]
    result[i] = sum / Math.max(1, end - start)
  }
  return result
}

/** Cắt khoảng lặng đầu/cuối (giữ lại `padMs` ms để không mất âm đầu/cuối của từ). */
export function trimSilence(samples, sampleRate, threshold = 0.015, padMs = 200) {
  const pad = Math.floor((sampleRate * padMs) / 1000)
  let start = 0
  while (start < samples.length && Math.abs(samples[start]) < threshold) start++
  let end = samples.length - 1
  while (end > start && Math.abs(samples[end]) < threshold) end--
  if (start >= end) return new Float32Array(0)
  return samples.subarray(Math.max(0, start - pad), Math.min(samples.length, end + pad + 1))
}

/** Mã hóa Float32 [-1, 1] thành file WAV PCM 16-bit mono. */
export function encodeWav(samples, sampleRate = TARGET_SAMPLE_RATE) {
  const buffer = new ArrayBuffer(44 + samples.length * 2)
  const view = new DataView(buffer)
  const writeText = (offset, text) => [...text].forEach((ch, i) => view.setUint8(offset + i, ch.charCodeAt(0)))

  writeText(0, 'RIFF')
  view.setUint32(4, 36 + samples.length * 2, true)
  writeText(8, 'WAVE')
  writeText(12, 'fmt ')
  view.setUint32(16, 16, true)            // kích thước chunk fmt
  view.setUint16(20, 1, true)             // PCM
  view.setUint16(22, 1, true)             // mono
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // byte rate
  view.setUint16(32, 2, true)             // block align
  view.setUint16(34, 16, true)            // bits per sample
  writeText(36, 'data')
  view.setUint32(40, samples.length * 2, true)

  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]))
    view.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true)
  }
  return buffer
}

/** Độ lớn âm thanh (RMS) 0..1 cho thanh hiển thị mức âm. */
export function rms(samples) {
  if (!samples.length) return 0
  let sum = 0
  for (let i = 0; i < samples.length; i++) sum += samples[i] * samples[i]
  return Math.sqrt(sum / samples.length)
}
