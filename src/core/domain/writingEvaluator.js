/**
 * Chấm bài viết theo quy tắc - chạy hoàn toàn trên trình duyệt, phù hợp trình độ A1-B1
 * (chuyển từ RuleBasedWritingEvaluator của bản Java).
 *
 * Phát hiện: lỗi chính tả thường gặp, "i" viết thường, chữ đầu câu viết thường, lặp từ, a/an,
 * hòa hợp chủ ngữ - động từ cơ bản, khoảng trắng/dấu câu, câu quá dài, thiếu dấu kết thúc.
 *
 * Điểm tổng = 30% hoàn thành yêu cầu (số từ) + 35% ngữ pháp/chính tả (lỗi trên 100 từ)
 *           + 20% từ vựng (độ đa dạng) + 15% mạch lạc (từ nối, số câu).
 * Giới hạn: không bắt được lỗi chia thì theo ngữ cảnh ("Yesterday I eat" vẫn được coi là đúng).
 */

export const PROVIDER = 'RULE_BASED'
const WORD = /[A-Za-z]+(?:['’-][A-Za-z]+)*/g

const MISSPELLINGS = {
  recieve: 'receive', becuase: 'because', beacuse: 'because', definately: 'definitely', alot: 'a lot', wich: 'which',
  thier: 'their', untill: 'until', tommorow: 'tomorrow', tomorow: 'tomorrow', accomodation: 'accommodation',
  beleive: 'believe', goverment: 'government', enviroment: 'environment', seperate: 'separate', occured: 'occurred',
  freind: 'friend', wierd: 'weird', realy: 'really', finaly: 'finally', beautifull: 'beautiful', wonderfull: 'wonderful',
  intresting: 'interesting', diffrent: 'different', resturant: 'restaurant', wether: 'whether', whit: 'with',
  becouse: 'because', studing: 'studying', comming: 'coming', writting: 'writing', goed: 'went', buyed: 'bought',
  eated: 'ate', swimmed: 'swam', teached: 'taught', thinked: 'thought', catched: 'caught',
}
/** Từ bắt đầu bằng nguyên âm nhưng phát âm phụ âm (dùng "a"). */
const CONSONANT_SOUND_PREFIXES = ['uni', 'use', 'usu', 'uti', 'ure', 'euro', 'eu', 'one', 'once', 'ewe', 'ufo']
/** Từ bắt đầu bằng "h" câm (dùng "an"). */
const SILENT_H_PREFIXES = ['hour', 'honest', 'honor', 'honour', 'heir']
/** Trợ động từ đứng trước -> "does she have" là đúng. */
const AUXILIARIES = new Set(['does', 'did', "doesn't", "didn't", 'will', 'would', 'can', 'could', 'should', 'may', 'might',
  'must', 'to', 'let', 'make', 'makes', 'made', 'help', 'helps'])
const LINKERS = new Set(['first', 'firstly', 'second', 'then', 'next', 'after', 'finally', 'however', 'because', 'so', 'also',
  'but', 'although', 'moreover', 'furthermore', 'therefore', 'besides', 'later', 'meanwhile', 'while', 'since', 'example',
  'instance', 'addition', 'conclusion', 'overall', 'lastly'])
const STOPWORDS = new Set(['the', 'a', 'an', 'and', 'or', 'but', 'to', 'of', 'in', 'on', 'at', 'for', 'with', 'is', 'are', 'was',
  'were', 'be', 'i', 'you', 'he', 'she', 'it', 'we', 'they', 'my', 'your', 'his', 'her', 'our', 'their', 'this', 'that',
  'very', 'so', 'then', 'have', 'has', 'had', 'do', 'did'])

const clamp = (v, min, max) => Math.max(min, Math.min(max, v))
const issue = (start, end, type, original, suggestion, message) => ({ start, end, type, original, suggestion, message })
const matchCase = (original, replacement) =>
  (original[0] === original[0].toUpperCase() && original[0] !== original[0].toLowerCase()
    ? replacement[0].toUpperCase() + replacement.slice(1) : replacement)

export function words(text) {
  return [...(text ?? '').matchAll(WORD)].map((m) => m[0])
}

export function countWords(text) {
  return words(text).length
}

export function startsWithVowelSound(word) {
  const w = word.toLowerCase()
  if (SILENT_H_PREFIXES.some((p) => w.startsWith(p))) return true
  if (CONSONANT_SOUND_PREFIXES.some((p) => w.startsWith(p))) return false
  return 'aeiou'.includes(w[0])
}

/**
 * @param input { text, prompt?, minWords?, maxWords? }
 * @returns WritingFeedback { provider, score, criteria, wordCount, sentenceCount, issues, correctedText, strengths, improvements }
 */
export function evaluateWriting({ text = '', minWords = null, maxWords = null }) {
  const ws = words(text)
  const wordCount = ws.length
  const sentenceCount = countSentences(text)
  if (wordCount === 0) {
    return {
      provider: PROVIDER, score: 0, criteria: { taskAchievement: 0, grammar: 0, vocabulary: 0, coherence: 0 },
      wordCount: 0, sentenceCount: 0, issues: [], correctedText: text, strengths: [], improvements: ['Bạn chưa viết nội dung nào.'],
    }
  }
  const issues = resolveOverlaps(findIssues(text))
  const task = taskScore(wordCount, minWords, maxWords)
  const errors = issues.filter((i) => i.type !== 'STYLE').length
  // Lỗi viết hoa / dấu câu nhẹ hơn lỗi ngữ pháp / chính tả (tính nửa lỗi)
  const weighted = issues.reduce((s, i) => s + ({ GRAMMAR: 1, SPELLING: 1, CAPITALIZATION: 0.5, PUNCTUATION: 0.5 }[i.type] ?? 0), 0)
  const grammar = clamp(Math.round(100 - (weighted * 100 / wordCount) * 5), 20, 100)
  const vocabulary = vocabularyScore(ws)
  const coherence = coherenceScore(ws, sentenceCount)
  const score = Math.round(task * 0.30 + grammar * 0.35 + vocabulary * 0.20 + coherence * 0.15)
  const criteria = { taskAchievement: task, grammar, vocabulary, coherence }
  return {
    provider: PROVIDER, score, criteria, wordCount, sentenceCount, issues,
    correctedText: applyCorrections(text, issues),
    strengths: strengths(criteria, errors),
    improvements: improvements(criteria, wordCount, minWords, maxWords, issues),
  }
}

// ------------------------------------------------------------------ phát hiện lỗi

function findIssues(text) {
  const issues = []
  for (const m of text.matchAll(WORD)) {
    const lower = m[0].toLowerCase()
    const fix = MISSPELLINGS[lower]
    if (fix && fix !== lower) {
      const irregular = lower.endsWith('ed')
      issues.push(issue(m.index, m.index + m[0].length, irregular ? 'GRAMMAR' : 'SPELLING', m[0], matchCase(m[0], fix),
        irregular ? `Động từ bất quy tắc: dạng quá khứ là "${fix}"` : 'Sai chính tả'))
    }
  }
  for (const m of text.matchAll(/(?<![A-Za-z'])i(?![A-Za-z])/g)) {
    issues.push(issue(m.index, m.index + 1, 'CAPITALIZATION', 'i', 'I', 'Đại từ "I" luôn viết hoa'))
  }
  for (const m of text.matchAll(/(?:^|[.!?]\s+)([a-z])/g)) {
    const start = m.index + m[0].length - 1
    issues.push(issue(start, start + 1, 'CAPITALIZATION', m[1], m[1].toUpperCase(), 'Chữ cái đầu câu cần viết hoa'))
  }
  for (const m of text.matchAll(/\b([A-Za-z]+)\s+\1\b/gi)) {
    const w = m[1].toLowerCase()
    if (w !== 'had' && w !== 'that') {
      issues.push(issue(m.index, m.index + m[0].length, 'GRAMMAR', m[0], m[1], `Lặp từ "${m[1]}"`))
    }
  }
  for (const m of text.matchAll(/\b(a|an)\s+([A-Za-z]+)/gi)) {
    const article = m[1]
    const vowel = startsWithVowelSound(m[2])
    if (article.toLowerCase() === 'a' && vowel) {
      issues.push(issue(m.index, m.index + 1, 'GRAMMAR', article, matchCase(article, 'an'),
        `Dùng "an" trước từ bắt đầu bằng nguyên âm: an ${m[2]}`))
    } else if (article.toLowerCase() === 'an' && !vowel) {
      issues.push(issue(m.index, m.index + 2, 'GRAMMAR', article, matchCase(article, 'a'),
        `Dùng "a" trước từ bắt đầu bằng phụ âm: a ${m[2]}`))
    }
  }
  subjectVerb(text, issues, "(he|she|it)\\s+(don't)", "doesn't", true)
  subjectVerb(text, issues, '(he|she|it)\\s+(have)', 'has', true)
  subjectVerb(text, issues, '(he|she|it)\\s+(are)', 'is', false)
  subjectVerb(text, issues, '(i)\\s+(is|are)', 'am', false)
  subjectVerb(text, issues, '(you|we|they)\\s+(is)', 'are', false)
  subjectVerb(text, issues, '(you|we|they)\\s+(was)', 'were', false)
  subjectVerb(text, issues, '(i|you|we|they)\\s+(has)', 'have', false)
  subjectVerb(text, issues, "(i|you|we|they)\\s+(doesn't)", "don't", false)

  for (const m of text.matchAll(/(?<=\S) {2,}(?=\S)/g)) {
    issues.push(issue(m.index, m.index + m[0].length, 'STYLE', m[0], ' ', 'Thừa khoảng trắng'))
  }
  for (const m of text.matchAll(/\s+([,.!?;:])/g)) {
    issues.push(issue(m.index, m.index + m[0].length, 'PUNCTUATION', m[0], m[1], 'Không đặt khoảng trắng trước dấu câu'))
  }
  for (const m of text.matchAll(/([,;:!?])(?=[A-Za-z])/g)) {
    issues.push(issue(m.index, m.index + 1, 'PUNCTUATION', m[0], `${m[1]} `, 'Cần khoảng trắng sau dấu câu'))
  }
  for (const m of text.matchAll(/[^.!?]+[.!?]*/g)) {
    if (words(m[0]).length > 35) {
      issues.push(issue(m.index, m.index + m[0].length, 'STYLE', m[0].trim(), null, 'Câu quá dài (> 35 từ), nên tách thành 2-3 câu ngắn'))
    }
  }
  const trimmed = text.trimEnd()
  if (trimmed && !/[.!?"')]$/.test(trimmed)) {
    const last = trimmed.match(/[A-Za-z']+$/)
    if (last) {
      issues.push(issue(last.index, last.index + last[0].length, 'PUNCTUATION', last[0], `${last[0]}.`, 'Thiếu dấu chấm kết thúc câu'))
    }
  }
  return issues
}

/** Lỗi hòa hợp chủ ngữ - động từ; bỏ qua khi phía trước có trợ động từ ("does she have" là đúng). */
function subjectVerb(text, issues, regex, fix, checkAuxiliary) {
  for (const m of text.matchAll(new RegExp(`\\b${regex}\\b`, 'gi'))) {
    if (checkAuxiliary && AUXILIARIES.has(previousWord(text, m.index))) continue
    const verbStart = m.index + m[0].length - m[2].length
    issues.push(issue(verbStart, verbStart + m[2].length, 'GRAMMAR', m[2], fix, `Chủ ngữ "${m[1]}" đi với "${fix}"`))
  }
}

function previousWord(text, index) {
  const m = text.slice(0, index).match(/([A-Za-z']+)\s*$/)
  return m ? m[1].toLowerCase() : ''
}

/** Giữ lỗi xuất hiện trước khi hai lỗi chồng lên cùng một đoạn văn bản. */
function resolveOverlaps(issues) {
  const sorted = [...issues].sort((a, b) => a.start - b.start || (b.end - b.start) - (a.end - a.start))
  const result = []
  let lastEnd = -1
  for (const i of sorted) {
    if (i.start >= lastEnd) {
      result.push(i)
      lastEnd = i.end
    }
  }
  return result
}

/** Áp gợi ý sửa từ cuối lên đầu để vị trí các lỗi phía trước không bị lệch. */
export function applyCorrections(text, issues) {
  let out = text
  for (const i of [...issues].filter((x) => x.suggestion != null).sort((a, b) => b.start - a.start)) {
    out = out.slice(0, i.start) + i.suggestion + out.slice(i.end)
  }
  return out
}

// ------------------------------------------------------------------ chấm điểm

function taskScore(wordCount, min, max) {
  if (min == null) return wordCount >= 20 ? 100 : wordCount * 5
  if (wordCount < min) return Math.max(10, Math.round((100 * wordCount) / min))
  if (max != null && wordCount > max * 1.2) return 80
  return 100
}

function vocabularyScore(ws) {
  const unique = new Set()
  let content = 0
  let totalLength = 0
  for (const w of ws) {
    const lower = w.toLowerCase()
    totalLength += lower.length
    if (!STOPWORDS.has(lower) && lower.length >= 3) {
      content++
      unique.add(lower)
    }
  }
  const variety = content === 0 ? 0 : unique.size / content
  const avgLength = totalLength / ws.length
  return clamp(Math.round(40 + variety * 55 + (avgLength > 4.5 ? 5 : 0)), 30, 100)
}

function coherenceScore(ws, sentences) {
  const linkers = new Set(ws.map((w) => w.toLowerCase()).filter((w) => LINKERS.has(w))).size
  let score = linkers === 0 ? 55 : linkers === 1 ? 70 : linkers === 2 ? 85 : 100
  if (ws.length >= 40 && sentences < 3) score -= 10
  return clamp(score, 30, 100)
}

function countSentences(text) {
  return text.split(/[.!?]+/).filter((part) => /[A-Za-z]/.test(part)).length
}

function strengths(c, errors) {
  const list = []
  if (c.taskAchievement === 100) list.push('Bài viết đáp ứng yêu cầu về độ dài.')
  if (errors === 0) list.push('Không phát hiện lỗi ngữ pháp/chính tả cơ bản — rất tốt!')
  else if (c.grammar >= 80) list.push('Ngữ pháp và chính tả khá chắc chắn.')
  if (c.vocabulary >= 80) list.push('Vốn từ đa dạng, ít lặp từ.')
  if (c.coherence >= 85) list.push('Dùng từ nối hợp lý, ý văn mạch lạc.')
  return list
}

function improvements(c, wordCount, min, max, issues) {
  const list = []
  if (min != null && wordCount < min) {
    list.push(`Bài viết còn ngắn: ${wordCount}/${min} từ tối thiểu. Hãy bổ sung thêm chi tiết, ví dụ.`)
  } else if (max != null && wordCount > max * 1.2) {
    list.push(`Bài viết dài hơn yêu cầu (${wordCount} từ, tối đa ~${max}). Hãy viết súc tích hơn.`)
  }
  const spelling = issues.filter((i) => i.type === 'SPELLING').length
  const grammar = issues.filter((i) => i.type === 'GRAMMAR').length
  if (spelling) list.push(`Kiểm tra lại chính tả (${spelling} lỗi).`)
  if (grammar) list.push(`Chú ý ngữ pháp: chia động từ, mạo từ a/an (${grammar} lỗi).`)
  if (issues.some((i) => i.type === 'CAPITALIZATION')) list.push('Viết hoa chữ đầu câu và đại từ "I".')
  if (c.coherence < 70) list.push('Thêm từ nối (first, then, after that, finally, because, however...) để bài mạch lạc hơn.')
  if (c.vocabulary < 65) list.push('Đa dạng từ vựng hơn, tránh lặp lại một từ nhiều lần.')
  return list
}
