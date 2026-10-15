/**
 * Bài kiểm tra xếp lớp: bắt đầu / tiếp tục, tự lưu từng câu, nộp bài, chấm theo kỹ năng, xếp level,
 * cập nhật hồ sơ và tự tạo lộ trình học nếu chưa có.
 */
import * as db from '../storage/db'
import { nowIso, plusMs } from '../clock'
import { AppError, conflict, invalid, notFound } from '../errors'
import { getLevel, levels, placementTest } from '../content'
import { grade, referencesOwnOptions } from '../domain/grader'
import { percent, resolveLevel } from '../domain/placement'
import { createNotification } from './notifications'
import { generateIfAbsent } from './path'
import { latestFinishedPlacement as latestFinished, placementAttempts } from './placementRecords'
import { attemptResponse, questionReview, toPayload } from './questions'
import { levelResponse, paginate, requireProfile } from './shared'

/** Ân hạn khi nộp bài sát giờ (độ trễ thao tác). */
const GRACE_MS = 30_000

const test = () => {
  if (!placementTest?.questions?.length) throw new AppError('PLACEMENT_TEST_NOT_AVAILABLE', 'Hiện chưa có bài kiểm tra xếp lớp', { status: 404 })
  return placementTest
}
const isExpired = (a, graceMs = GRACE_MS) => a.expiresAt && Date.now() > new Date(a.expiresAt).getTime() + graceMs
const maxScore = (t) => t.questions.reduce((s, q) => s + q.points, 0)

function saveAttempt(attempt) {
  db.update('attempts', (list) => {
    const i = list.findIndex((a) => a.id === attempt.id)
    if (i >= 0) list[i] = attempt
    else list.push(attempt)
  })
}

function requireAttempt(attemptId) {
  const attempt = placementAttempts().find((a) => a.id === Number(attemptId))
  if (!attempt) throw notFound('bài làm', attemptId)
  return attempt
}

const toSummary = (a) => ({
  attemptId: a.id, status: a.status, startedAt: a.startedAt, submittedAt: a.submittedAt ?? null,
  score: a.score ?? null, maxScore: a.maxScore, percentage: a.percentage ?? null, level: levelResponse(a.resultLevelId),
})

export function getInfo() {
  requireProfile()
  const t = test()
  const inProgress = placementAttempts().find((a) => a.status === 'IN_PROGRESS' && !isExpired(a))
  const latest = latestFinished()
  return {
    testId: t.id, title: t.title, description: t.description ?? null, durationMinutes: t.durationMinutes ?? null,
    totalQuestions: t.questions.length, inProgressAttemptId: inProgress?.id ?? null, latestResult: latest ? toSummary(latest) : null,
  }
}

/** Tiếp tục bài đang làm nếu còn hạn; nếu không thì tạo bài làm mới. */
export function startOrResume() {
  requireProfile()
  const t = test()
  const current = placementAttempts().find((a) => a.status === 'IN_PROGRESS')
  if (current) {
    if (!isExpired(current)) return attemptResponse(current, t)
    finalize(current, 'EXPIRED')
  }
  const startedAt = nowIso()
  const attempt = {
    id: db.nextId('attempt'), kind: 'PLACEMENT', testId: t.id, status: 'IN_PROGRESS', startedAt,
    expiresAt: t.durationMinutes ? plusMs(startedAt, t.durationMinutes * 60_000).toISOString() : null,
    maxScore: maxScore(t), answers: {},
  }
  saveAttempt(attempt)
  return attemptResponse(attempt, t)
}

function upsertAnswer(attempt, request) {
  const q = test().questions.find((x) => x.id === Number(request.questionId))
  if (!q) throw new AppError('QUESTION_NOT_IN_TEST', 'Câu hỏi không thuộc bài kiểm tra này')
  const payload = toPayload(request)
  if (!referencesOwnOptions(q, payload)) throw invalid(`Phương án trả lời không thuộc câu hỏi ${q.id}`)
  attempt.answers[q.id] = { payload, answeredAt: nowIso(), correct: null, score: null }
}

/** Tự lưu một câu trả lời. */
export function saveAnswer(attemptId, request) {
  const attempt = requireAttempt(attemptId)
  if (attempt.status !== 'IN_PROGRESS') throw conflict('ATTEMPT_NOT_IN_PROGRESS', 'Bài làm đã được nộp')
  if (isExpired(attempt)) throw conflict('ATTEMPT_EXPIRED', 'Đã hết thời gian làm bài')
  upsertAnswer(attempt, request)
  saveAttempt(attempt)
}

/** Nộp bài (idempotent). Quá hạn thì bỏ qua các câu gửi kèm, chỉ chấm các câu đã lưu. */
export function submit(attemptId, answers = []) {
  const attempt = requireAttempt(attemptId)
  if (attempt.status === 'IN_PROGRESS') {
    const expired = isExpired(attempt)
    if (!expired) answers.forEach((a) => upsertAnswer(attempt, a))
    finalize(attempt, expired ? 'EXPIRED' : 'SUBMITTED')
  }
  return toResult(requireAttempt(attemptId))
}

export function getResult(attemptId) {
  const attempt = requireAttempt(attemptId)
  if (attempt.status === 'IN_PROGRESS') throw conflict('ATTEMPT_NOT_SUBMITTED', 'Bài làm chưa được nộp')
  return toResult(attempt)
}

export function history({ page = 0, size = 20 } = {}) {
  const list = placementAttempts().filter((a) => a.status !== 'IN_PROGRESS')
    .sort((a, b) => (b.submittedAt ?? '').localeCompare(a.submittedAt ?? '')).map(toSummary)
  return paginate(list, { page, size })
}

/** Chấm toàn bộ, tính điểm theo kỹ năng, xếp level, cập nhật hồ sơ, tạo lộ trình + thông báo. */
function finalize(attempt, status) {
  const t = test()
  let total = 0
  let max = 0
  const bySkill = new Map()
  for (const q of t.questions) {
    const answer = attempt.answers[q.id]
    const g = grade(q, answer?.payload ?? null, q.points)
    if (answer) Object.assign(answer, { correct: g.correct, score: g.score })
    const earned = g.graded ? g.score : 0
    total += earned
    max += q.points
    const s = bySkill.get(q.skill) ?? { score: 0, maxScore: 0 }
    s.score += earned
    s.maxScore += q.points
    bySkill.set(q.skill, s)
  }
  const percentage = percent(total, max)
  const level = resolveLevel(percentage, levels)
  Object.assign(attempt, {
    status,
    submittedAt: status === 'EXPIRED' && attempt.expiresAt ? attempt.expiresAt : nowIso(),
    score: Math.round(total * 100) / 100, maxScore: max, percentage, resultLevelId: level.id,
    skillScores: [...bySkill].map(([skill, s]) => ({
      skill, score: Math.round(s.score * 100) / 100, maxScore: s.maxScore, percentage: percent(s.score, s.maxScore),
    })),
  })
  saveAttempt(attempt)

  db.update('profile', (p) => {
    if (!p) return
    p.currentLevelId = level.id
    p.placementCompletedAt = nowIso()
  })
  createNotification('PATH_UPDATED', 'Lộ trình học đã sẵn sàng',
    `Bạn được xếp trình độ ${level.name}. Xem lộ trình được gợi ý theo kết quả bài kiểm tra.`, '/app/path')
  generateIfAbsent()
}

function toResult(a) {
  const t = test()
  const review = t.questions.map((q, i) => questionReview(q, i + 1, a.answers[q.id]))
  const durationSeconds = a.submittedAt ? Math.round((new Date(a.submittedAt) - new Date(a.startedAt)) / 1000) : 0
  return {
    attemptId: a.id, status: a.status, startedAt: a.startedAt, submittedAt: a.submittedAt, durationSeconds,
    score: a.score, maxScore: a.maxScore, percentage: a.percentage,
    correctCount: Object.values(a.answers).filter((x) => x.correct === true).length,
    totalQuestions: t.questions.length, level: levelResponse(a.resultLevelId) ?? { ...getLevel(levels[0].id) },
    skillScores: a.skillScores ?? [], review,
  }
}
