/** Khóa học & bài học (nội dung từ JSON) kèm tiến độ của người học. */
import * as db from '../storage/db'
import { notFound } from '../errors'
import { courses, getCourse, getLesson, lessons, lessonsOfCourse } from '../content'
import { lessonCard, levelResponse, likeMatch, paginate, progressInfo, sortItems } from './shared'

const progressMap = () => db.read('lessonProgress')

function courseSummary(course, progress) {
  const ls = lessonsOfCourse(course.id)
  return {
    id: course.id, title: course.title, slug: course.slug, description: course.description ?? null,
    level: levelResponse(course.levelId), goalType: course.goalType, thumbnailUrl: course.thumbnailUrl ?? null,
    lessonCount: ls.length,
    totalMinutes: ls.reduce((s, l) => s + l.estimatedMinutes, 0),
    completedLessons: ls.filter((l) => progress[l.id]?.status === 'COMPLETED').length,
    skills: [...new Set(ls.map((l) => l.skill))],
  }
}

export function listCourses({ levelId, goal } = {}) {
  const progress = progressMap()
  return courses
    .filter((c) => (levelId == null || levelId === '' || c.levelId === Number(levelId)) && (!goal || c.goalType === goal))
    .map((c) => courseSummary(c, progress))
}

export function getCourseDetail(id) {
  const course = getCourse(id)
  if (!course) throw notFound('khóa học', id)
  const progress = progressMap()
  return {
    course: courseSummary(course, progress),
    lessons: lessonsOfCourse(course.id).map((l) => lessonCard(l, progress[l.id])),
  }
}

const LESSON_SORTS = {
  title: (l) => l.title.toLowerCase(), estimatedMinutes: (l) => l.estimatedMinutes, sortOrder: (l) => l.sortOrder, createdAt: (l) => l.id,
}

export function searchLessons({ skill, levelId, keyword, page = 0, size = 20, sort } = {}) {
  const progress = progressMap()
  const match = likeMatch(keyword)
  const filtered = lessons.filter((l) => (!skill || l.skill === skill)
    && (levelId == null || levelId === '' || l.levelId === Number(levelId))
    && (!match || match(l.title, l.summary)))
  const result = paginate(sortItems(filtered, sort, LESSON_SORTS), { page, size })
  return { ...result, items: result.items.map((l) => lessonCard(l, progress[l.id])) }
}

export function getLessonDetail(id) {
  const lesson = getLesson(id)
  if (!lesson) throw notFound('bài học', id)
  const progress = progressMap()
  const siblings = lessonsOfCourse(lesson.courseId)
  const index = siblings.findIndex((l) => l.id === lesson.id)
  const nav = (l) => (l ? { id: l.id, title: l.title, skill: l.skill } : null)
  return {
    id: lesson.id, title: lesson.title, summary: lesson.summary, skill: lesson.skill, content: lesson.content ?? null,
    mediaType: lesson.mediaType ?? 'NONE', mediaUrl: lesson.mediaUrl ?? null, durationSeconds: lesson.durationSeconds ?? null,
    estimatedMinutes: lesson.estimatedMinutes, level: levelResponse(lesson.levelId),
    courseId: lesson.courseId, courseTitle: lesson.courseTitle,
    transcript: lesson.transcript.map((s, i) => ({ id: i + 1, startMs: s.startMs, endMs: s.endMs, text: s.text, translation: s.translation ?? null })),
    progress: progressInfo(progress[lesson.id]),
    previous: nav(siblings[index - 1]),
    next: nav(siblings[index + 1]),
    courseLessons: siblings.map((l) => lessonCard(l, progress[l.id])),
  }
}
