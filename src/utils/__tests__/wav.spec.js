import { describe, expect, it } from 'vitest'
import { downsample, encodeWav, mergeChunks, rms, trimSilence } from '../wav'

describe('wav', () => {
  it('ghép các đoạn mẫu theo đúng thứ tự', () => {
    expect([...mergeChunks([new Float32Array([1, 2]), new Float32Array([3])])]).toEqual([1, 2, 3])
  })

  it('giảm 48 kHz -> 16 kHz (1/3 số mẫu, lấy trung bình)', () => {
    const out = downsample(new Float32Array([0, 0.3, 0.6, 0.9, 0.9, 0.9]), 48000, 16000)
    expect(out.length).toBe(2)
    expect(out[0]).toBeCloseTo(0.3)
    expect(out[1]).toBeCloseTo(0.9)
  })

  it('cắt khoảng lặng hai đầu nhưng giữ phần đệm', () => {
    const samples = new Float32Array(1000)
    samples.fill(0.5, 400, 600)
    const trimmed = trimSilence(samples, 1000, 0.015, 50)   // 1 kHz -> đệm 50 mẫu
    expect(trimmed.length).toBe(200 + 100)
    expect(trimSilence(new Float32Array(100), 1000).length).toBe(0)
  })

  it('header WAV đúng chuẩn PCM 16-bit mono 16 kHz', () => {
    const buffer = encodeWav(new Float32Array([0, 1, -1, 0.5]), 16000)
    const view = new DataView(buffer)
    const text = (o) => String.fromCharCode(...new Uint8Array(buffer, o, 4))
    expect(buffer.byteLength).toBe(44 + 8)
    expect(text(0)).toBe('RIFF')
    expect(text(8)).toBe('WAVE')
    expect(view.getUint16(22, true)).toBe(1)
    expect(view.getUint32(24, true)).toBe(16000)
    expect(view.getUint32(40, true)).toBe(8)
    expect(view.getInt16(46, true)).toBe(32767)
    expect(view.getInt16(48, true)).toBe(-32768)
  })

  it('rms', () => {
    expect(rms(new Float32Array([0.5, -0.5]))).toBeCloseTo(0.5)
    expect(rms(new Float32Array(0))).toBe(0)
  })
})
