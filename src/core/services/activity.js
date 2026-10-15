/**
 * Ghi nhận hoạt động học theo ngày (thời gian học, bài hoàn thành, từ đã học/ôn, bài tập) và chuỗi ngày học.
 * Đạt các mốc chuỗi ngày -> thông báo thành tích.
 */
import * as db from '../storage/db'
import { addDays, localDate } from '../clock'
import { createNotification } from './notifications'

export const STREAK_MILESTONES = new Set([3, 7, 14, 21, 30, 50, 100, 200, 365])

const emptyDay = () => ({
  studySeconds: 0, lessonsCompleted: 0, wordsLearned: 0, wordsReviewed: 0, quizzesTaken: 0, totalQuizScore: 0, goalReached: false,
})

/**
 * @param delta { studySeconds, lessonsCompleted, wordsLearned, wordsReviewed, quizzesTaken, quizScore }
 */
export function record(delta) {
  const profile = db.read('profile')
  const today = localDate()
  db.update('daily', (daily) => {
    const day = { ...emptyDay(), ...daily[today] }
    day.studySeconds += delta.studySeconds ?? 0
    day.lessonsCompleted += delta.lessonsCompleted ?? 0
    day.wordsLearned += delta.wordsLearned ?? 0
    day.wordsReviewed += delta.wordsReviewed ?? 0
    day.quizzesTaken += delta.quizzesTaken ?? 0
    day.totalQuizScore += delta.quizScore ?? 0
    day.goalReached = day.studySeconds >= (profile?.dailyGoalMinutes ?? 15) * 60
    daily[today] = day
  })
  touchStreak(today)
}

export const recordStudyTime = (seconds) => record({ studySeconds: seconds })
export const recordLessonCompleted = () => record({ lessonsCompleted: 1 })

/** Học hôm nay lần đầu: hôm qua có học -> chuỗi +1, không thì bắt đầu lại từ 1. */
function touchStreak(today) {
  let reached = null
  db.update('streak', (s) => {
    if (s.lastActivityDate === today) return
    s.current = s.lastActivityDate === addDays(today, -1) ? s.current + 1 : 1
    s.longest = Math.max(s.longest, s.current)
    s.lastActivityDate = today
    if (STREAK_MILESTONES.has(s.current)) reached = s.current
  })
  if (reached) {
    createNotification('ACHIEVEMENT', `Chuỗi ${reached} ngày học liên tục! 🔥`,
      `Tuyệt vời! Bạn đã học ${reached} ngày liên tiếp. Giữ vững phong độ nhé!`, '/app/dashboard')
  }
}

/** Chuỗi hiển thị: lần học cuối là hôm nay hoặc hôm qua thì còn hiệu lực, còn lại hiển thị 0. */
export function currentStreak(today = localDate()) {
  const s = db.read('streak')
  if (!s.lastActivityDate) return { current: 0, longest: s.longest, lastActivityDate: null, activeToday: false }
  const activeToday = s.lastActivityDate === today
  const alive = activeToday || s.lastActivityDate === addDays(today, -1)
  return { current: alive ? s.current : 0, longest: s.longest, lastActivityDate: s.lastActivityDate, activeToday }
}
