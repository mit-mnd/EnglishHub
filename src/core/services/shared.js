/** Tiện ích dùng chung cho các service: phân trang, sắp xếp, ánh xạ DTO. */
import * as db from '../storage/db'
import { AppError } from '../errors'
import { getLevel } from '../content'

/** Phân trang giống PageResponse của API cũ: { items, page, size, totalElements, totalPages }. */
export function paginate(items, { page = 0, size = 20 } = {}) {
  const p = Math.max(0, Number(page) || 0)
  const s = Math.max(1, Math.min(200, Number(size) || 20))
  return {
    items: items.slice(p * s, p * s + s),
    page: p,
    size: s,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / s),
  }
}

/** sort = "field,asc|desc" (giống Spring); allowed: field -> hàm lấy giá trị. */
export function sortItems(items, sort, allowed, fallback) {
  const [field, dir = 'asc'] = (sort || fallback || '').split(',')
  const getter = allowed[field]
  if (!getter) return items
  const sign = dir.toLowerCase() === 'desc' ? -1 : 1
  return [...items].sort((a, b) => {
    const x = getter(a)
    const y = getter(b)
    if (x == null && y == null) return 0
    if (x == null) return 1
    if (y == null) return -1
    return (x < y ? -1 : x > y ? 1 : 0) * sign
  })
}

export const likeMatch = (keyword) => {
  const k = (keyword ?? '').trim().toLowerCase()
  return k ? (...values) => values.some((v) => (v ?? '').toLowerCase().includes(k)) : null
}

/** Hồ sơ người học; lỗi nếu chưa khai báo (mọi chức năng học đều cần hồ sơ). */
export function requireProfile() {
  const profile = db.read('profile')
  if (!profile) throw new AppError('PROFILE_REQUIRED', 'Bạn chưa khai báo thông tin người học', { status: 401 })
  return profile
}

export const levelResponse = (levelId) => {
  const l = getLevel(levelId)
  return l ? { ...l } : null
}

/** Thẻ bài học dùng trong danh sách (khóa học, lộ trình, tìm kiếm). */
export function lessonCard(lesson, progress) {
  const level = getLevel(lesson.levelId)
  const card = {
    id: lesson.id, title: lesson.title, summary: lesson.summary, skill: lesson.skill,
    estimatedMinutes: lesson.estimatedMinutes, mediaType: lesson.mediaType ?? 'NONE',
    courseId: lesson.courseId, courseTitle: lesson.courseTitle, levelCode: level?.code, levelName: level?.name,
  }
  if (progress) {
    card.progressStatus = progress.status
    card.progressPercent = progress.progressPercent
  }
  return card
}

export const progressInfo = (p) => (p ? {
  status: p.status, progressPercent: p.progressPercent, lastPositionMs: p.lastPositionMs ?? null,
  timeSpentSeconds: p.timeSpentSeconds, bestScore: p.bestScore ?? null, completedAt: p.completedAt ?? null,
} : null)

/** RNG có seed (mulberry32) - xáo trộn ổn định khi tải lại trang. */
export function seededRandom(seed) {
  let a = seed >>> 0
  return () => {
    a = (a + 0x6d2b79f5) >>> 0
    let t = a
    t = Math.imul(t ^ (t >>> 15), t | 1)
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

export function shuffle(items, random = Math.random) {
  const a = [...items]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}
