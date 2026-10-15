/**
 * Chấm phát âm trên trình duyệt (chuyển từ MockPronunciationScorer + WordAligner của bản Java).
 * Không phân tích âm thanh, nhưng vẫn cho phản hồi có ích:
 *  - Có văn bản trình duyệt nhận dạng được (Web Speech API - Chrome/Edge): so khớp từng từ với câu mẫu
 *    -> độ chính xác, độ đầy đủ; độ trôi chảy tính từ tốc độ nói (từ/phút).
 *  - Không có: chỉ ước lượng từ thời lượng ghi âm so với độ dài câu (estimated = true, tối đa 80 điểm).
 * Có thể thay bằng dịch vụ chấm thật (Azure Speech...) bằng cách viết hàm khác cùng chữ ký.
 */

export const PROVIDER = 'BROWSER'
const MIN_WPM = 80
const MAX_WPM = 170

const CONTRACTIONS = {
  "i'm": 'i am', "you're": 'you are', "we're": 'we are', "they're": 'they are', "he's": 'he is', "she's": 'she is',
  "it's": 'it is', "that's": 'that is', "what's": 'what is', "there's": 'there is', "i've": 'i have', "you've": 'you have',
  "we've": 'we have', "i'll": 'i will', "you'll": 'you will', "we'll": 'we will', "i'd": 'i would', "don't": 'do not',
  "doesn't": 'does not', "didn't": 'did not', "isn't": 'is not', "aren't": 'are not', "wasn't": 'was not',
  "can't": 'cannot', "won't": 'will not', "let's": 'let us',
}

/** "Hello," -> [hello]; "I'm" -> [i, am]; "6.50" -> [6.50]. */
export function tokens(word) {
  const w = word.toLowerCase().replace(/’/g, "'")
    .replace(/^[^a-z0-9']+|[^a-z0-9']+$/g, '')
    .replace(/^'+|'+$/g, '')
  if (!w) return []
  return (CONTRACTIONS[w] ?? w).split(' ')
}

/**
 * So khớp câu mẫu với câu nhận dạng được theo từng từ (Levenshtein trên chuỗi từ).
 * @returns { words: [{word, status: CORRECT|MISPRONOUNCED|OMITTED}], matched, substituted, deleted, inserted, referenceTokens }
 */
export function alignWords(reference, hypothesis) {
  const display = []
  const ref = []
  const owner = []
  for (const word of reference.trim().split(/\s+/)) {
    const t = tokens(word)
    if (!t.length) continue
    display.push(word)
    for (const tok of t) {
      ref.push(tok)
      owner.push(display.length - 1)
    }
  }
  const hyp = hypothesis ? hypothesis.trim().split(/\s+/).flatMap(tokens) : []

  const n = ref.length
  const m = hyp.length
  const d = Array.from({ length: n + 1 }, (_, i) => Array.from({ length: m + 1 }, (_, j) => (i === 0 ? j : j === 0 ? i : 0)))
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      const cost = ref[i - 1] === hyp[j - 1] ? 0 : 1
      d[i][j] = Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
    }
  }

  const status = new Array(n)
  let matched = 0, substituted = 0, deleted = 0, inserted = 0
  let i = n, j = m
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && d[i][j] === d[i - 1][j - 1] + (ref[i - 1] === hyp[j - 1] ? 0 : 1)) {
      const same = ref[i - 1] === hyp[j - 1]
      status[i - 1] = same ? 'CORRECT' : 'MISPRONOUNCED'
      if (same) matched++
      else substituted++
      i--
      j--
    } else if (i > 0 && d[i][j] === d[i - 1][j] + 1) {
      status[i - 1] = 'OMITTED'
      deleted++
      i--
    } else {
      inserted++
      j--
    }
  }

  const words = display.map((word, w) => {
    const own = status.filter((_, t) => owner[t] === w)
    const allCorrect = own.every((s) => s === 'CORRECT')
    const allOmitted = own.every((s) => s === 'OMITTED')
    return { word, status: allCorrect ? 'CORRECT' : allOmitted ? 'OMITTED' : 'MISPRONOUNCED' }
  })
  return { words, matched, substituted, deleted, inserted, referenceTokens: n }
}

const pct = (part, total) => (total === 0 ? 0 : (part * 100) / total)
const clamp = (v, min, max) => Math.max(min, Math.min(max, v))
const round1 = (v) => Math.round(v * 10) / 10

/**
 * @param input { referenceText, transcript?, durationMs }
 * @returns { provider, estimated, recognizedText, pronunciationScore, accuracyScore, fluencyScore, completenessScore, words }
 */
export function scorePronunciation({ referenceText, transcript, durationMs }) {
  return transcript?.trim() ? scoreWithTranscript(referenceText, transcript, durationMs) : estimate(referenceText, durationMs)
}

function scoreWithTranscript(reference, transcript, durationMs) {
  const a = alignWords(reference, transcript)
  const spoken = a.matched + a.substituted
  const completeness = pct(spoken, a.referenceTokens)
  const accuracy = spoken === 0 ? 0 : pct(a.matched, spoken)
  const minutes = Math.max(durationMs, 1) / 60000
  const wpm = spoken / minutes
  let fluency = wpm < MIN_WPM ? 100 - (MIN_WPM - wpm) * 1.2 : wpm > MAX_WPM ? 100 - (wpm - MAX_WPM) * 0.8 : 100
  fluency = clamp(fluency - a.inserted * 5, 30, 100)   // từ thừa ~ ngập ngừng, lặp lại
  const pron = accuracy * 0.5 + completeness * 0.3 + fluency * 0.2
  return {
    provider: PROVIDER, estimated: false, recognizedText: transcript.trim(),
    pronunciationScore: round1(pron), accuracyScore: round1(accuracy), fluencyScore: round1(fluency),
    completenessScore: round1(completeness),
    words: a.words.map((w) => ({
      word: w.word,
      accuracy: w.status === 'CORRECT' ? 95 : w.status === 'MISPRONOUNCED' ? 45 : 0,
      errorType: w.status === 'CORRECT' ? 'NONE' : w.status === 'OMITTED' ? 'OMISSION' : 'MISPRONUNCIATION',
    })),
  }
}

function estimate(reference, durationMs) {
  const display = reference.trim().split(/\s+/)
  const expectedMs = display.length * 450 + 600   // ~2,2 từ/giây + khoảng lặng đầu
  const ratio = durationMs / expectedMs
  const completeness = ratio < 1 ? clamp(ratio * 100, 0, 100) : ratio <= 2.5 ? 100 : 70
  const fluency = ratio >= 0.7 && ratio <= 1.8 ? 85 : 60
  const pron = Math.min(80, completeness * 0.6 + fluency * 0.4)
  return {
    provider: PROVIDER, estimated: true, recognizedText: null,
    pronunciationScore: round1(pron), accuracyScore: null, fluencyScore: round1(fluency), completenessScore: round1(completeness),
    words: display.map((w) => ({ word: w, accuracy: null, errorType: 'UNKNOWN' })),
  }
}

/** Gợi ý luyện tập bằng tiếng Việt dựa trên điểm và các từ sai/bỏ sót. */
export function speakingTips(result) {
  const tips = []
  if (result.estimated) {
    tips.push('Trình duyệt chưa hỗ trợ nhận dạng giọng nói nên điểm chỉ là ước lượng. Hãy dùng Chrome hoặc Edge để được chấm từng từ.')
  }
  const omitted = result.words.filter((w) => w.errorType === 'OMISSION').map((w) => w.word)
  const wrong = result.words.filter((w) => w.errorType === 'MISPRONUNCIATION').map((w) => w.word)
  if (omitted.length) tips.push(`Bạn bỏ sót: ${omitted.join(', ')}. Hãy đọc đủ từng từ.`)
  if (wrong.length) tips.push(`Luyện lại phát âm các từ: ${wrong.join(', ')} (bấm "Nghe mẫu" để nghe lại).`)
  if (result.fluencyScore < 70) tips.push('Nói liền mạch hơn: đọc cả cụm từ, tránh ngắt quãng giữa các từ.')
  if (result.pronunciationScore >= 85 && !omitted.length && !wrong.length && !result.estimated) {
    tips.push('Tuyệt vời! Phát âm rõ ràng, đầy đủ. Thử nói nhanh và tự nhiên hơn nhé.')
  }
  return tips
}
