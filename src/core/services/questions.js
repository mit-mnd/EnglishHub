/**
 * Chuyển câu hỏi / bài làm sang DTO, dùng chung cho bài xếp lớp và bài tập trong bài học.
 * Đề gửi cho màn hình KHÔNG chứa đáp án (trừ câu mẫu SPEAKING - là nội dung cần đọc).
 */
import { essayConfig, parseBlanks } from '../domain/grader'
import { seededRandom, shuffle } from './shared'

/** Câu hỏi để làm bài (không lộ đáp án). */
export function questionView(q, number, seed) {
  const view = {
    id: q.id, number, type: q.type, skill: q.skill, content: q.content, points: q.points,
  }
  if (q.passage) view.passage = q.passage
  if (q.mediaUrl) view.mediaUrl = q.mediaUrl
  if (['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'ORDERING', 'MATCHING'].includes(q.type)) {
    view.options = q.options.map((o) => ({ id: o.id, content: o.content }))
  }
  if (q.type === 'MATCHING') view.matchChoices = shuffle(q.options.map((o) => o.matchText), seededRandom(seed * 31 + q.id))
  if (q.type === 'FILL_BLANK') view.blankCount = parseBlanks(q).length
  if (q.type === 'SPEAKING') view.referenceText = q.answerKey
  if (q.type === 'ESSAY') Object.assign(view, essayConfig(q))
  return view
}

/** Danh sách câu hỏi của một lượt làm (xáo trộn theo seed nếu bài test bật shuffle). */
export function attemptQuestions(test, attemptId) {
  const ordered = test.shuffleQuestions ? shuffle(test.questions, seededRandom(attemptId)) : test.questions
  return ordered.map((q, i) => questionView(q, i + 1, attemptId))
}

/** Lời giải một câu (sau khi chấm / nộp bài). */
export function questionReview(q, number, answer) {
  const review = {
    questionId: q.id, number, type: q.type, skill: q.skill, content: q.content,
    correct: answer?.correct ?? false, score: answer?.score ?? 0, points: q.points,
  }
  if (q.passage) review.passage = q.passage
  if (q.options?.length) {
    review.options = q.options.map((o) => {
      const v = { id: o.id, content: o.content, correct: !!o.correct }
      if (o.matchText != null) v.matchText = o.matchText
      if (o.correctOrder != null) v.correctOrder = o.correctOrder
      return v
    })
  }
  if (q.type === 'FILL_BLANK') review.acceptedAnswers = parseBlanks(q)
  if (answer?.payload) review.yourAnswer = answer.payload
  if (q.explanation) review.explanation = q.explanation
  return review
}

/** Câu trả lời đã lưu: { questionId: payload }. */
export const savedAnswers = (attempt) =>
  Object.fromEntries(Object.entries(attempt.answers).map(([id, a]) => [id, a.payload]))

export function attemptResponse(attempt, test) {
  return {
    attemptId: attempt.id, testId: test.id, title: test.title, status: attempt.status,
    startedAt: attempt.startedAt, expiresAt: attempt.expiresAt ?? null, serverTime: new Date().toISOString(),
    questions: attemptQuestions(test, attempt.id),
    savedAnswers: savedAnswers(attempt),
  }
}

/** Chỉ giữ các field của AnswerPayload (bỏ questionId...). */
export function toPayload(req) {
  const p = {}
  for (const key of ['selectedOptionIds', 'textAnswers', 'matches', 'orderedOptionIds', 'answerText']) {
    if (req?.[key] != null) p[key] = req[key]
  }
  return p
}
