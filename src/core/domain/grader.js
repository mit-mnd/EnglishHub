/**
 * Chấm tự động các dạng câu hỏi khách quan (chuyển từ AnswerGrader của bản Java).
 *  - SINGLE/MULTIPLE_CHOICE, ORDERING: đúng hết mới có điểm.
 *  - FILL_BLANK, MATCHING: chấm điểm một phần theo tỉ lệ ô/cặp đúng.
 *  - ESSAY, SPEAKING: không chấm ở đây (graded = false) - xem writingEvaluator / pronunciation.
 *
 * Câu trả lời (AnswerPayload): { selectedOptionIds, textAnswers, matches: {optionId: text}, orderedOptionIds, answerText }
 */

export const AUTO_GRADABLE = new Set(['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'FILL_BLANK', 'MATCHING', 'ORDERING'])

export const round2 = (n) => Math.round((n + Number.EPSILON) * 100) / 100

/** Chuẩn hóa câu trả lời ngắn: bỏ khoảng trắng thừa, không phân biệt hoa thường, thống nhất dấu nháy, bỏ dấu câu cuối. */
export function normalizeAnswer(value) {
  if (value == null) return ''
  return String(value).normalize('NFC')
    .replace(/[’‘]/g, "'")
    .trim()
    .replace(/\s+/g, ' ')
    .replace(/[.!?]+$/, '')
    .trim()
    .toLowerCase()
}

/** Danh sách đáp án chấp nhận cho từng ô của câu FILL_BLANK. */
export function parseBlanks(question) {
  const parsed = typeof question.answerKey === 'string' ? JSON.parse(question.answerKey) : question.answerKey
  return (parsed ?? []).map((b) => (b ?? []).filter((s) => s != null))
}

/** Yêu cầu số từ của câu ESSAY ({"minWords":80,"maxWords":120} trong answerKey). */
export function essayConfig(question) {
  try {
    const cfg = typeof question.answerKey === 'string' ? JSON.parse(question.answerKey) : question.answerKey
    return { minWords: cfg?.minWords ?? null, maxWords: cfg?.maxWords ?? null }
  } catch {
    return { minWords: null, maxWords: null }
  }
}

/** @returns {{ graded: boolean, correct: boolean|null, score: number }} */
export function grade(question, answer, points) {
  if (!AUTO_GRADABLE.has(question.type)) return { graded: false, correct: null, score: 0 }
  if (answer == null) return { graded: true, correct: false, score: 0 }
  switch (question.type) {
    case 'SINGLE_CHOICE': return allOrNothing(isSingleChoiceCorrect(question, answer), points)
    case 'MULTIPLE_CHOICE': return allOrNothing(isMultipleChoiceCorrect(question, answer), points)
    case 'ORDERING': return allOrNothing(isOrderingCorrect(question, answer), points)
    case 'FILL_BLANK': return gradeFillBlank(question, answer, points)
    case 'MATCHING': return gradeMatching(question, answer, points)
    default: return { graded: false, correct: null, score: 0 }
  }
}

/** Câu trả lời chỉ tham chiếu các phương án của chính câu hỏi. */
export function referencesOwnOptions(question, answer) {
  const valid = new Set((question.options ?? []).map((o) => o.id))
  const referenced = [
    ...(answer.selectedOptionIds ?? []),
    ...(answer.orderedOptionIds ?? []),
    ...Object.keys(answer.matches ?? {}).map(Number),
  ]
  return referenced.every((id) => id != null && valid.has(Number(id)))
}

const correctIds = (q) => new Set(q.options.filter((o) => o.correct).map((o) => o.id))
const sameSet = (a, b) => a.size === b.size && [...a].every((x) => b.has(x))

function isSingleChoiceCorrect(q, answer) {
  const selected = answer.selectedOptionIds ?? []
  return selected.length === 1 && sameSet(correctIds(q), new Set([Number(selected[0])]))
}

function isMultipleChoiceCorrect(q, answer) {
  const selected = answer.selectedOptionIds ?? []
  return selected.length > 0 && sameSet(correctIds(q), new Set(selected.map(Number)))
}

/** So sánh theo NỘI DUNG từng mảnh để hai mảnh trùng chữ (vd. hai chữ "the") đổi chỗ vẫn đúng. */
function isOrderingCorrect(q, answer) {
  const ordered = answer.orderedOptionIds ?? []
  if (ordered.length !== q.options.length) return false
  const contentById = new Map(q.options.map((o) => [o.id, normalizeAnswer(o.content)]))
  const expected = [...q.options]
    .sort((a, b) => (a.correctOrder ?? Infinity) - (b.correctOrder ?? Infinity))
    .map((o) => normalizeAnswer(o.content))
  return ordered.every((id, i) => contentById.get(Number(id)) === expected[i])
}

function gradeFillBlank(q, answer, points) {
  const blanks = parseBlanks(q)
  const given = answer.textAnswers ?? []
  let correct = 0
  blanks.forEach((accepted, i) => {
    const value = normalizeAnswer(given[i])
    if (value && accepted.some((a) => normalizeAnswer(a) === value)) correct++
  })
  return partial(correct, blanks.length, points)
}

function gradeMatching(q, answer, points) {
  const matches = answer.matches ?? {}
  const correct = q.options.filter((o) => o.matchText != null
    && normalizeAnswer(o.matchText) === normalizeAnswer(matches[o.id])).length
  return partial(correct, q.options.length, points)
}

function allOrNothing(correct, points) {
  return { graded: true, correct, score: correct ? round2(points) : 0 }
}

function partial(correct, total, points) {
  if (total === 0) return { graded: true, correct: false, score: 0 }
  return { graded: true, correct: correct === total, score: round2((points * correct) / total) }
}
