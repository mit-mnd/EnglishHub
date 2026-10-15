import { describe, expect, it, vi } from 'vitest'
import { firstNumber, scoreGroup, speechQueue, splitPassage } from '../toeic'
import { pickVoice, useSpeech } from '@/composables/useSpeech'

describe('toeic utils', () => {
  it('Part 2: đọc câu hỏi rồi các đáp án bằng giọng còn lại; chữ cái A-C là câu riêng có nghỉ', () => {
    const q = speechQueue({ kind: 'response', questionVoice: 'female', script: 'Where is it?', questions: [{ options: ['Here.', 'Yes.', 'Ten.'] }] })
    expect(q.map((x) => [x.lead ?? null, x.text, x.voice])).toEqual([
      [null, 'Where is it?', 'female'], ['A', 'Here.', 'male'], ['B', 'Yes.', 'male'], ['C', 'Ten.', 'male']])
    expect(q.slice(1).every((x) => x.leadPauseMs >= 500 && x.pauseMs >= 900)).toBe(true)
  })

  it('speakQueue đọc chữ cái và đáp án thành 2 câu riêng, có nghỉ ở giữa', async () => {
    vi.useFakeTimers()
    const spoken = []
    const synth = { cancel: vi.fn(), getVoices: () => [], speak: (u) => { spoken.push({ text: u.text, at: Date.now() }); setTimeout(() => u.onend(), 300) } }
    vi.stubGlobal('speechSynthesis', synth)
    window.speechSynthesis = synth
    vi.stubGlobal('SpeechSynthesisUtterance', class { constructor(text) { this.text = text } })
    const { speakQueue } = useSpeech()
    const done = speakQueue([{ lead: 'A', leadPauseMs: 650, text: 'Here.', voice: 'male', pauseMs: 0 }])
    await vi.runAllTimersAsync()
    await done
    expect(spoken.map((s) => s.text)).toEqual(['A', 'Here.'])
    expect(spoken[1].at - spoken[0].at).toBeGreaterThanOrEqual(300 + 650)   // đọc xong "A" rồi nghỉ 650 ms
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('Part 3: giọng theo người nói', () => {
    const q = speechQueue({
      kind: 'talk', speakers: [{ name: 'Man', voice: 'male' }, { name: 'Woman', voice: 'female' }],
      script: [{ speaker: 'Woman', text: 'Hi.' }, { speaker: 'Man', text: 'Hello.' }],
    })
    expect(q.map((x) => x.voice)).toEqual(['female', 'male'])
  })

  it('tách đoạn văn Part 6 theo ô trống', () => {
    expect(splitPassage('A {{1}} b {{2}}.')).toEqual([
      { type: 'text', value: 'A ' }, { type: 'blank', blank: 1 }, { type: 'text', value: ' b ' }, { type: 'blank', blank: 2 },
      { type: 'text', value: '.' },
    ])
  })

  it('chấm nhóm và đánh số câu liên tục', () => {
    const g = { questions: [{ id: 'a', answer: 1 }, { id: 'b', answer: 0 }] }
    expect(scoreGroup(g, { a: 1, b: 2 })).toEqual({ correct: 1, total: 2 })
    expect(firstNumber([g, g, g], 2)).toBe(5)
  })

  it('chọn giọng nam/nữ theo tên giọng, không có thì đổi cao độ', () => {
    const voices = [{ name: 'Microsoft Zira - English (United States)', lang: 'en-US' }, { name: 'Microsoft David - English (United States)', lang: 'en-US' }]
    expect(pickVoice('female', voices).voice.name).toContain('Zira')
    expect(pickVoice('male', voices).voice.name).toContain('David')
    const anon = [{ name: 'Voice 1', lang: 'en-US' }]
    expect(pickVoice('male', anon).pitch).toBeLessThan(1)
  })
})
