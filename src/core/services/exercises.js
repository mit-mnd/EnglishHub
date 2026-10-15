/**
 * Bài tập / quiz trong bài học: chấm từng câu ngay khi trả lời; nộp bài đạt ngưỡng thì hoàn thành bài học.
 * ESSAY chấm bằng bộ chấm bài viết, SPEAKING lấy điểm từ bản ghi âm đã chấm.
 */
import * as db from '../storage/db'
import { nowIso, plusMs } from '../clock'
import { AppError, conflict, invalid, notFound } from '../errors'
import { exercisesOfLesson, getTest } from '../content'
import { essayConfig, grade, referencesOwnOptions } from '../domain/grader'
import { record as recordActivity } from './activity'
import { complete as completeLesson } from './progress'
import { attemptResponse, questionReview, toPayload } from './questions'
import { requireProfile } from './shared'
import { evaluateWritingSubmission, getSpeaking } from './skills'

const DEFAULT_PASSING = 60
const SPEAKING_PASS = 70
const WRITING_PASS = 60

const passing = (t) => t.passingPercent ?? DEFAULT_PASSING
const totalPoints = (t) => t.questions.reduce((s, q) => s + q.points, 0)
const exerciseAttempts = () => db.read('attempts').filter((a) => a.kind === 'EXERCISE')
const isExpired = (a, graceMs = 0) => a.expiresAt && Date.now() > new Date(a.expiresAt).getTime() + graceMs
/** Điểm câu = điểm tối đa × (điểm thành phần / 100). */
const scaled = (points, pct) => Math.round(points * Math.max(0, Math.min(100, pct))) / 100

function saveAttempt(attempt) {
  db.update('attempts', (list) => {
    const i = list.findIndex((a) => a.id === attempt.id)
    if (i >= 0) list[i] = attempt
    else list.push(attempt)
  })
}

function requireExercise(testId) {
  const t = getTest(testId)
  if (!t || t.testType === 'PLACEMENT') throw notFound('bài tập', testId)
  return t
}

function requireAttempt(attemptId) {
  const a = exerciseAttempts().find((x) => x.id === Number(attemptId))
  if (!a) throw notFound('bài làm', attemptId)
  return a
}

export function listForLesson(lessonId) {
  const attempts = exerciseAttempts()
  return exercisesOfLesson(lessonId).map((t) => {
    const mine = attempts.filter((a) => a.testId === t.id)
    const best = mine.map((a) => a.percentage).filter((p) => p != null).sort((a, b) => b - a)[0] ?? null
    return {
      testId: t.id, title: t.title, description: t.description ?? null, testType: t.testType,
      questionCount: t.questions.length, durationMinutes: t.durationMinutes ?? null, passingPercent: passing(t),
      skills: [...new Set(t.questions.map((q) => q.skill))],
      attempts: mine.filter((a) => a.status !== 'IN_PROGRESS').length,
      bestPercentage: best, passed: best != null && best >= passing(t),
      inProgressAttemptId: mine.find((a) => a.status === 'IN_PROGRESS' && !isExpired(a))?.id ?? null,
    }
  })
}

/** Tiếp tục lượt đang làm dở (còn hạn) hoặc bắt đầu lượt mới. */
export function start(testId) {
  requireProfile()
  const t = requireExercise(testId)
  let attempt = exerciseAttempts().find((a) => a.testId === t.id && a.status === 'IN_PROGRESS')
  if (attempt && isExpired(attempt)) {
    close(attempt, t, 'EXPIRED')
    attempt = null
  }
  if (!attempt) {
    const startedAt = nowIso()
    attempt = {
      id: db.nextId('attempt'), kind: 'EXERCISE', testId: t.id, lessonId: t.lessonId ?? null, status: 'IN_PROGRESS', startedAt,
      expiresAt: t.durationMinutes ? plusMs(startedAt, t.durationMinutes * 60_000).toISOString() : null,
      maxScore: totalPoints(t), answers: {},
    }
    saveAttempt(attempt)
  }
  const checked = {}
  for (const q of t.questions) {
    const answer = attempt.answers[q.id]
    if (answer?.score != null) checked[q.id] = toCheckResult(q, answer)
  }
  return { attempt: attemptResponse(attempt, t), passingPercent: passing(t), checked }
}

/** Chấm một câu (mỗi câu chỉ chấm một lần trong một lượt). */
export async function check(attemptId, request) {
  const attempt = requireAttempt(attemptId)
  if (attempt.status !== 'IN_PROGRESS') throw conflict('ATTEMPT_NOT_IN_PROGRESS', 'Bài làm đã được nộp')
  if (isExpired(attempt, 30_000)) throw conflict('ATTEMPT_EXPIRED', 'Đã hết thời gian làm bài')
  const t = requireExercise(attempt.testId)
  const q = t.questions.find((x) => x.id === Number(request.questionId))
  if (!q) throw new AppError('QUESTION_NOT_IN_TEST', 'Câu hỏi không thuộc bài kiểm tra này')
  if (attempt.answers[q.id]?.score != null) {
    throw conflict('ANSWER_ALREADY_CHECKED', 'Câu này đã được chấm, hãy làm lại bài nếu muốn trả lời lại')
  }

  let payload = toPayload(request)
  const answer = { answeredAt: nowIso(), correct: null, score: null, feedback: null }
  if (q.type === 'ESSAY') {
    if (!payload.answerText?.trim()) throw invalid('Bạn chưa viết bài')
    const cfg = essayConfig(q)
    const result = evaluateWritingSubmission({
      text: payload.answerText, prompt: q.content, ...cfg, lessonId: attempt.lessonId, questionId: q.id,
    })
    Object.assign(answer, {
      correct: result.feedback.score >= WRITING_PASS, score: scaled(q.points, result.feedback.score),
      feedback: { writing: result.feedback },
    })
  } else if (q.type === 'SPEAKING') {
    if (!request.speakingSubmissionId) throw invalid('Hãy thu âm trước khi kiểm tra')
    const sub = getSpeaking(request.speakingSubmissionId)
    const raw = db.read('speaking').find((s) => s.id === sub.id)
    if (raw.questionId !== q.id) throw invalid('Bản ghi âm không thuộc câu này')
    payload = { answerText: sub.recognizedText }
    Object.assign(answer, {
      correct: sub.pronunciationScore >= SPEAKING_PASS, score: scaled(q.points, sub.pronunciationScore),
      feedback: { speakingSubmissionId: sub.id },
    })
  } else {
    if (!referencesOwnOptions(q, payload)) throw invalid('Phương án trả lời không thuộc câu hỏi')
    const g = grade(q, payload, q.points)
    Object.assign(answer, { correct: g.correct, score: g.score })
  }
  answer.payload = payload
  attempt.answers[q.id] = answer
  saveAttempt(attempt)
  return toCheckResult(q, answer)
}

/** Nộp bài: tính điểm, ghi nhận hoạt động; đạt ngưỡng thì hoàn thành bài học. Idempotent. */
export function finish(attemptId) {
  const attempt = requireAttempt(attemptId)
  const t = requireExercise(attempt.testId)
  let lessonCompleted = false
  if (attempt.status === 'IN_PROGRESS') {
    close(attempt, t, 'SUBMITTED')
    recordActivity({ quizzesTaken: 1, quizScore: attempt.percentage })
    if (attempt.percentage >= passing(t) && t.lessonId) {
      completeLesson(t.lessonId, { score: attempt.percentage })
      lessonCompleted = true
    }
  }
  return toResult(attempt, t, lessonCompleted)
}

/** Chốt lượt làm: câu chưa trả lời = 0 điểm. */
function close(attempt, t, status) {
  const max = totalPoints(t)
  const score = Object.values(attempt.answers).reduce((s, a) => s + (a.score ?? 0), 0)
  Object.assign(attempt, {
    status, submittedAt: nowIso(), score: Math.round(score * 100) / 100, maxScore: max,
    percentage: max === 0 ? 0 : Math.min(100, Math.round((score * 10000) / max) / 100),
  })
  saveAttempt(attempt)
}

function toResult(attempt, t, lessonCompleted) {
  const answers = Object.values(attempt.answers)
  return {
    attemptId: attempt.id, testId: t.id, title: t.title, score: attempt.score, maxScore: attempt.maxScore,
    percentage: attempt.percentage, passingPercent: passing(t), passed: attempt.percentage >= passing(t),
    correctCount: answers.filter((a) => a.correct === true).length,
    answeredCount: answers.filter((a) => a.score != null).length,
    totalQuestions: t.questions.length, lessonCompleted,
    review: t.questions.map((q, i) => questionReview(q, i + 1, attempt.answers[q.id])),
  }
}

function toCheckResult(q, answer) {
  const result = { questionId: q.id, correct: answer.correct, score: answer.score, points: q.points, review: questionReview(q, 0, answer) }
  if (answer.feedback?.writing) result.writing = answer.feedback.writing
  if (answer.feedback?.speakingSubmissionId) {
    try {
      result.speaking = getSpeaking(answer.feedback.speakingSubmissionId)
    } catch {
      /* bản ghi đã bị xóa khỏi lịch sử */
    }
  }
  return result
}
