/** Tiến độ bài học: heartbeat thời gian học, vị trí audio, % đã xem; hoàn thành bài. */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid, notFound } from '../errors'
import { getLesson, lessonsOfCourse } from '../content'
import { recordLessonCompleted, recordStudyTime } from './activity'
import { markLessonCompleted, markLessonStarted } from './path'
import { progressInfo } from './shared'

function loadOrStart(lessonId) {
  const lesson = getLesson(lessonId)
  if (!lesson) throw notFound('bài học', lessonId)
  const all = db.read('lessonProgress')
  if (!all[lesson.id]) {
    all[lesson.id] = {
      lessonId: lesson.id, status: 'IN_PROGRESS', progressPercent: 0, bestScore: null, lastPositionMs: null,
      timeSpentSeconds: 0, startedAt: nowIso(), completedAt: null,
    }
    db.write('lessonProgress', all)
    markLessonStarted(lesson.id)
  }
  return { lesson, all, progress: all[lesson.id] }
}

/** Gửi định kỳ (~30 giây) khi đang học bài. seconds tối đa 120 mỗi lần. */
export function heartbeat(lessonId, { seconds = 0, positionMs, progressPercent } = {}) {
  if (!Number.isFinite(seconds) || seconds < 0 || seconds > 120) throw invalid('Mỗi lần ghi nhận tối đa 120 giây', 'seconds')
  const { all, progress } = loadOrStart(lessonId)
  progress.timeSpentSeconds += Math.round(seconds)
  if (positionMs != null) progress.lastPositionMs = positionMs
  if (progress.status !== 'COMPLETED' && progressPercent != null && progressPercent > progress.progressPercent) {
    progress.progressPercent = Math.min(100, progressPercent)
  }
  db.write('lessonProgress', all)
  if (seconds > 0) recordStudyTime(Math.round(seconds))
  return progressInfo(progress)
}

export function complete(lessonId, { score } = {}) {
  const { lesson, all, progress } = loadOrStart(lessonId)
  const firstCompletion = progress.status !== 'COMPLETED'
  if (firstCompletion) {
    Object.assign(progress, { status: 'COMPLETED', completedAt: nowIso(), progressPercent: 100 })
  }
  if (score != null && (progress.bestScore == null || score > progress.bestScore)) progress.bestScore = score
  db.write('lessonProgress', all)
  if (firstCompletion) {
    recordLessonCompleted()
    markLessonCompleted(lesson.id)
  }
  const siblings = lessonsOfCourse(lesson.courseId)
  const next = siblings[siblings.findIndex((l) => l.id === lesson.id) + 1]
  return {
    progress: progressInfo(progress), firstCompletion,
    nextInCourse: next ? { id: next.id, title: next.title, skill: next.skill } : null,
  }
}
