/** Tổng hợp số liệu cho trang Tổng quan. Mọi mốc "hôm nay" theo giờ máy của người học. */
import * as db from '../storage/db'
import { addDays, localDate } from '../clock'
import { invalid } from '../errors'
import { getLesson } from '../content'
import { currentStreak } from './activity'
import { latestFinishedPlacement } from './placementRecords'
import { getPath } from './path'
import { levelResponse, requireProfile } from './shared'

const RANGES = { '7d': [7, 'DAY'], '30d': [30, 'DAY'], '90d': [90, 'WEEK'], '365d': [365, 'MONTH'] }

/** Đầu kỳ: ngày / thứ Hai của tuần / ngày 1 của tháng. */
export function periodStart(isoDate, granularity) {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  if (granularity === 'WEEK') date.setDate(date.getDate() - ((date.getDay() + 6) % 7))
  if (granularity === 'MONTH') date.setDate(1)
  return localDate(date)
}

function nextPeriod(isoDate, granularity) {
  if (granularity === 'DAY') return addDays(isoDate, 1)
  if (granularity === 'WEEK') return addDays(isoDate, 7)
  const [y, m] = isoDate.split('-').map(Number)
  return localDate(new Date(y, m, 1))
}

const toMinutes = (seconds) => Math.round(seconds / 60)
const average = (total, count) => (count === 0 ? null : Math.round((total / count) * 10) / 10)

/** Gộp dữ liệu ngày thành chuỗi liên tục (ngày/tuần/tháng); khoảng không có hoạt động = 0. */
export function buildSeries(daily, from, to, granularity) {
  const buckets = new Map()
  for (let d = periodStart(from, granularity); d <= to; d = nextPeriod(d, granularity)) {
    buckets.set(d, { s: 0, lessons: 0, learned: 0, reviewed: 0, quizzes: 0, score: 0 })
  }
  for (const [date, row] of Object.entries(daily)) {
    const b = buckets.get(periodStart(date, granularity))
    if (!b || date < from || date > to) continue
    b.s += row.studySeconds
    b.lessons += row.lessonsCompleted
    b.learned += row.wordsLearned
    b.reviewed += row.wordsReviewed
    b.quizzes += row.quizzesTaken
    b.score += row.totalQuizScore
  }
  return [...buckets].map(([periodStart_, b]) => ({
    periodStart: periodStart_, studyMinutes: toMinutes(b.s), lessonsCompleted: b.lessons, wordsLearned: b.learned,
    wordsReviewed: b.reviewed, quizzesTaken: b.quizzes, averageScore: average(b.score, b.quizzes),
  }))
}

export function getDashboard(range = '7d') {
  const profile = requireProfile()
  if (!RANGES[range]) throw invalid('range phải là một trong: 7d, 30d, 90d, 365d')
  const [days, granularity] = RANGES[range]
  const today = localDate()
  const from = periodStart(addDays(today, -(days - 1)), granularity)
  const daily = db.read('daily')
  const todayMinutes = toMinutes(daily[today]?.studySeconds ?? 0)
  const lifetime = Object.values(daily).reduce((t, r) => ({
    s: t.s + r.studySeconds, quizzes: t.quizzes + r.quizzesTaken, score: t.score + r.totalQuizScore,
  }), { s: 0, quizzes: 0, score: 0 })

  const now = Date.now()
  const notebook = db.read('notebook')
  const count = (pred) => notebook.filter(pred).length
  const progress = db.read('lessonProgress')

  const path = getPath()
  const next = path?.items.find((i) => i.status === 'IN_PROGRESS') ?? path?.items.find((i) => i.status === 'AVAILABLE')
    ?? path?.items.find((i) => i.status === 'LOCKED')
  const placement = latestFinishedPlacement()
  const placementLevel = placement ? levelResponse(placement.resultLevelId) : null

  return {
    range, granularity, today, timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    streak: currentStreak(today),
    todayProgress: { studyMinutes: todayMinutes, goalMinutes: profile.dailyGoalMinutes, goalReached: todayMinutes >= profile.dailyGoalMinutes },
    totals: {
      studyMinutes: toMinutes(lifetime.s),
      lessonsCompleted: Object.values(progress).filter((p) => p.status === 'COMPLETED').length,
      wordsMastered: count((u) => u.status === 'MASTERED'),
      wordsLearning: count((u) => u.status === 'LEARNING' || u.status === 'REVIEW'),
      wordsNew: count((u) => u.status === 'NEW'),
      wordsDue: count((u) => u.status !== 'SUSPENDED' && new Date(u.dueAt).getTime() <= now),
      quizzesTaken: lifetime.quizzes,
      averageQuizScore: average(lifetime.score, lifetime.quizzes),
    },
    series: buildSeries(daily, from, today, granularity),
    learningPath: path ? {
      id: path.id, name: path.name, goalType: path.goalType, totalItems: path.totalItems,
      completedItems: path.completedItems + path.skippedItems, percent: path.percent,
      nextLesson: next ? {
        id: next.lesson.id, title: next.lesson.title, skill: next.lesson.skill,
        estimatedMinutes: getLesson(next.lesson.id).estimatedMinutes, status: next.status,
      } : null,
    } : null,
    latestPlacement: placement ? {
      attemptId: placement.id, percentage: placement.percentage, submittedAt: placement.submittedAt,
      levelCode: placementLevel?.code, levelName: placementLevel?.name,
    } : null,
    unreadNotifications: db.read('notifications').filter((n) => !n.read).length,
  }
}
