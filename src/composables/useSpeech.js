/**
 * Phát âm: ưu tiên file audio từ điển; không có thì dùng giọng đọc của trình duyệt (Web Speech API).
 * speakQueue() đọc lần lượt nhiều câu với giọng nam/nữ khác nhau - dùng cho bài nghe TOEIC (không có file ghi âm).
 */

const FEMALE_HINTS = /female|woman|zira|samantha|susan|hazel|aria|jenny|libby|sonia|karen|moira|tessa|victoria|serena|ava|allison|emma|michelle/i
const MALE_HINTS = /\bmale\b|\bman\b|david|mark|guy|george|daniel|ryan|alex|fred|tom|aaron|eric|christopher|thomas|james|oliver|brian/i

function englishVoices() {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return []
  return window.speechSynthesis.getVoices().filter((v) => v.lang?.toLowerCase().startsWith('en'))
}

/**
 * Chọn giọng theo giới tính dựa trên tên giọng (trình duyệt không cung cấp thuộc tính giới tính).
 * Không đoán được thì lấy 2 giọng tiếng Anh khác nhau cho nam/nữ và chỉnh cao độ để phân biệt.
 */
export function pickVoice(gender, voices = englishVoices()) {
  const preferUs = (list) => list.find((v) => v.lang === 'en-US') ?? list[0]
  const females = voices.filter((v) => FEMALE_HINTS.test(v.name) && !MALE_HINTS.test(v.name.replace(/female/i, '')))
  const males = voices.filter((v) => MALE_HINTS.test(v.name.replace(/female/i, '')) && !FEMALE_HINTS.test(v.name))
  if (gender === 'female' && females.length) return { voice: preferUs(females), pitch: 1 }
  if (gender === 'male' && males.length) return { voice: preferUs(males), pitch: 1 }
  const fallback = gender === 'male' ? voices[1] ?? voices[0] : voices[0]
  return { voice: fallback ?? null, pitch: gender === 'male' ? 0.8 : 1.15 }
}

export function useSpeech() {
  const supported = typeof window !== 'undefined' && 'speechSynthesis' in window
  let queueToken = 0

  function speak(text, accent = 'US', rate = 0.9) {
    if (!supported || !text) return
    window.speechSynthesis.cancel()
    const utterance = new SpeechSynthesisUtterance(text)
    utterance.lang = accent === 'UK' ? 'en-GB' : 'en-US'
    utterance.rate = rate
    const voice = window.speechSynthesis.getVoices().find((v) => v.lang === utterance.lang)
    if (voice) utterance.voice = voice
    window.speechSynthesis.speak(utterance)
  }

  function pronounce(text, audioUrl, accent = 'US') {
    if (audioUrl) {
      new Audio(audioUrl).play().catch(() => speak(text, accent))
    } else {
      speak(text, accent)
    }
  }

  /** Đọc một câu, resolve khi đọc xong (hoặc lỗi). */
  function speakOne(text, gender, rate) {
    return new Promise((resolve) => {
      const { voice, pitch } = pickVoice(gender)
      const u = new SpeechSynthesisUtterance(text)
      u.lang = voice?.lang ?? 'en-US'
      if (voice) u.voice = voice
      u.pitch = pitch
      u.rate = rate
      u.onend = resolve
      u.onerror = resolve
      window.speechSynthesis.speak(u)
    })
  }

  const wait = (ms) => new Promise((r) => setTimeout(r, ms))

  /**
   * Đọc lần lượt: items = [{ text, voice: 'male' | 'female', pauseMs?, lead?, leadPauseMs? }].
   * lead (vd. "A") được đọc thành câu riêng, nghỉ leadPauseMs rồi mới đọc text.
   * onIndex(i) báo câu đang đọc (để tô sáng). Trả Promise hoàn thành khi đọc xong hoặc bị stop().
   */
  async function speakQueue(items, { rate = 0.95, onIndex } = {}) {
    if (!supported) return
    stop()
    const token = ++queueToken
    const cancelled = () => token !== queueToken
    for (let i = 0; i < items.length && !cancelled(); i++) {
      const item = items[i]
      onIndex?.(i)
      if (item.lead) {
        await speakOne(item.lead, item.voice, rate)
        if (cancelled()) break
        await wait(item.leadPauseMs ?? 600)
        if (cancelled()) break
      }
      await speakOne(item.text, item.voice, rate)
      if (cancelled()) break
      await wait(item.pauseMs ?? 400)
    }
    if (!cancelled()) onIndex?.(-1)
  }

  function stop() {
    queueToken++
    if (supported) window.speechSynthesis.cancel()
  }

  return { supported, speak, pronounce, speakQueue, stop }
}
