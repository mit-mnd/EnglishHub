/**
 * Nhận dạng giọng nói của trình duyệt (Web Speech API - Chrome/Edge).
 * Chạy song song khi ghi âm; văn bản nhận dạng được gửi kèm để bộ chấm Mock so khớp từng từ.
 * Trình duyệt không hỗ trợ thì trả về null (backend sẽ chấm ước lượng).
 */
export function useSpeechRecognition() {
  const Recognition = typeof window !== 'undefined' && (window.SpeechRecognition || window.webkitSpeechRecognition)
  const supported = !!Recognition
  let recognition = null
  let finalText = ''
  let ended = null

  function start(lang = 'en-US') {
    if (!supported) return
    finalText = ''
    recognition = new Recognition()
    recognition.lang = lang
    recognition.continuous = true
    recognition.interimResults = false
    recognition.maxAlternatives = 1
    recognition.onresult = (event) => {
      for (let i = event.resultIndex; i < event.results.length; i++) {
        if (event.results[i].isFinal) finalText += ` ${event.results[i][0].transcript}`
      }
    }
    ended = new Promise((resolve) => {
      recognition.onend = resolve
      recognition.onerror = resolve
    })
    try {
      recognition.start()
    } catch {
      recognition = null
    }
  }

  /** Dừng và chờ kết quả cuối (tối đa 1,5 giây). */
  async function stop() {
    if (!recognition) return null
    recognition.stop()
    await Promise.race([ended, new Promise((r) => setTimeout(r, 1500))])
    recognition = null
    const text = finalText.trim()
    return text || null
  }

  function abort() {
    recognition?.abort()
    recognition = null
  }

  return { supported, start, stop, abort }
}
