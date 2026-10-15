/**
 * Lộ trình học cá nhân hóa: chọn bài theo trình độ + mục tiêu (+ kỹ năng yếu từ bài xếp lớp),
 * lên lịch theo số phút học mỗi ngày, cập nhật khi người học bắt đầu / hoàn thành bài.
 */
import * as db from '../storage/db'
import { localDate, nowIso } from '../clock'
import { AppError, conflict, invalid, notFound } from '../errors'
import { courses, getLesson, getLevel, levels, lessonsOfCourse } from '../content'
import { isDone, planDates, prioritizeWeakSkills, refreshStatuses } from '../domain/pathPlanner'
import { lessonCard, levelResponse, requireProfile } from './shared'
import { latestPlacementSnapshot } from './placementRecords'

export const MAX_ITEMS = 30
const GOAL_NAMES = { GENERAL: 'Tổng quát', COMMUNICATION: 'Giao tiếp', TOEIC: 'TOEIC', IELTS: 'IELTS' }

export function getPath() {
  const path = db.read('path')
  return path ? toResponse(path) : null
}

/** payload: { goalType?, levelId?, dailyMinutes? } - bỏ trống = theo hồ sơ / kết quả xếp lớp. */
export function generatePath(payload = {}) {
  const path = build(payload)
  if (!path) throw new AppError('NO_LESSONS_FOR_PATH', 'Chưa có bài học phù hợp với trình độ và mục tiêu này', { status: 404 })
  return toResponse(path)
}

/** Gọi sau bài xếp lớp / khi khai báo hồ sơ: tạo lộ trình nếu chưa có. */
export function generateIfAbsent() {
  if (!db.read('path')) build({})
}

function build(request) {
  const profile = requireProfile()
  if (request.dailyMinutes != null && (request.dailyMinutes < 5 || request.dailyMinutes > 480)) {
    throw invalid('Số phút mỗi ngày từ 5 đến 480', 'dailyMinutes')
  }
  const snapshot = latestPlacementSnapshot()
  const goal = request.goalType || profile.learningGoal
  const level = request.levelId != null
    ? getLevel(request.levelId) ?? (() => { throw notFound('level', request.levelId) })()
    : getLevel(profile.currentLevelId) ?? levels[0]
  const dailyMinutes = request.dailyMinutes ?? profile.dailyGoalMinutes
  // Tự chọn level khác => lộ trình theo mục tiêu, không dựa vào kết quả xếp lớp
  const fromPlacement = request.levelId == null && !!snapshot
  const weakSkills = fromPlacement ? new Set(snapshot.weakSkills) : new Set()
  const selected = selectLessons(level, goal, weakSkills)
  if (!selected.length) return null

  const progress = db.read('lessonProgress')
  const items = selected.map((lesson, i) => ({
    id: i + 1, lessonId: lesson.id, sortOrder: i + 1, custom: false, plannedDate: null, completedAt: null,
    ...statusFromProgress(progress[lesson.id]),
  }))
  const path = {
    id: db.nextId('path'),
    name: `Lộ trình ${GOAL_NAMES[goal]} · ${level.name}`,
    goalType: goal, levelId: level.id, source: fromPlacement ? 'PLACEMENT' : 'GOAL', status: 'ACTIVE',
    dailyMinutes, createdAt: nowIso(), nextItemId: items.length + 1, items,
  }
  refresh(path)
  db.write('path', path)
  // Đồng bộ hồ sơ với lựa chọn mới của người học
  db.update('profile', (p) => {
    p.learningGoal = goal
    p.dailyGoalMinutes = dailyMinutes
  })
  return path
}

function selectLessons(level, goal, weakSkills) {
  const goals = goal === 'GENERAL' ? ['GENERAL'] : [goal, 'GENERAL']
  const index = Math.max(0, levels.findIndex((l) => l.id === level.id))
  const result = [...lessonsOfLevel(level, goals, weakSkills)]
  if (index + 1 < levels.length) result.push(...lessonsOfLevel(levels[index + 1], goals, weakSkills))
  // Không có nội dung ở level hiện tại -> lùi dần xuống level thấp hơn
  for (let i = index - 1; !result.length && i >= 0; i--) result.push(...lessonsOfLevel(levels[i], goals, weakSkills))
  return result.slice(0, MAX_ITEMS)
}

function lessonsOfLevel(level, goals, weakSkills) {
  const matching = courses
    .filter((c) => c.levelId === level.id && goals.includes(c.goalType))
    .sort((a, b) => goals.indexOf(a.goalType) - goals.indexOf(b.goalType))   // đúng mục tiêu trước
  const ordered = matching.flatMap((c) => lessonsOfCourse(c.id))
  return prioritizeWeakSkills(ordered, (l) => l.skill, weakSkills)
}

function statusFromProgress(progress) {
  if (!progress) return { status: 'LOCKED' }
  if (progress.status === 'COMPLETED') return { status: 'COMPLETED', completedAt: progress.completedAt }
  return { status: 'IN_PROGRESS' }
}

/** Sắp theo sortOrder, tính lại trạng thái "học tiếp/sắp tới" và ngày học của các bài chưa xong. */
function refresh(path) {
  path.items.sort((a, b) => a.sortOrder - b.sortOrder)
  refreshStatuses(path.items)
  const pending = path.items.filter((i) => !isDone(i))
  const dates = planDates(pending, (i) => getLesson(i.lessonId)?.estimatedMinutes ?? 10, path.dailyMinutes, localDate())
  pending.forEach((item, i) => { item.plannedDate = dates[i] })
  path.items.filter(isDone).forEach((item) => { item.plannedDate = null })
}

function mutate(fn) {
  const path = db.read('path')
  if (!path) throw new AppError('LEARNING_PATH_NOT_FOUND', 'Bạn chưa có lộ trình học', { status: 404 })
  fn(path)
  refresh(path)
  db.write('path', path)
  return toResponse(path)
}

const requireItem = (path, itemId) => path.items.find((i) => i.id === Number(itemId)) ?? (() => { throw notFound('mục lộ trình', itemId) })()

export function addLesson(lessonId) {
  return mutate((path) => {
    const lesson = getLesson(lessonId)
    if (!lesson) throw notFound('bài học', lessonId)
    if (path.items.some((i) => i.lessonId === lesson.id)) throw conflict('LESSON_ALREADY_IN_PATH', 'Bài học đã có trong lộ trình')
    path.items.push({
      id: path.nextItemId++, lessonId: lesson.id, custom: true, plannedDate: null, completedAt: null,
      sortOrder: Math.max(0, ...path.items.map((i) => i.sortOrder)) + 1,
      ...statusFromProgress(db.read('lessonProgress')[lesson.id]),
    })
  })
}

export function removeItem(itemId) {
  return mutate((path) => {
    const item = requireItem(path, itemId)
    path.items = path.items.filter((i) => i !== item).sort((a, b) => a.sortOrder - b.sortOrder)
    path.items.forEach((i, idx) => { i.sortOrder = idx + 1 })
  })
}

export function skipItem(itemId) {
  return mutate((path) => {
    const item = requireItem(path, itemId)
    if (item.status === 'COMPLETED') throw invalid('Bài học đã hoàn thành, không thể bỏ qua')
    item.status = 'SKIPPED'
  })
}

export function unskipItem(itemId) {
  return mutate((path) => {
    const item = requireItem(path, itemId)
    if (item.status === 'SKIPPED') Object.assign(item, statusFromProgress(db.read('lessonProgress')[item.lessonId]))
  })
}

/** itemIds phải là hoán vị đầy đủ các mục của lộ trình. */
export function reorder(itemIds) {
  return mutate((path) => {
    const ids = itemIds.map(Number)
    const current = new Set(path.items.map((i) => i.id))
    if (ids.length !== current.size || !ids.every((id) => current.has(id))) {
      throw invalid('Danh sách sắp xếp không khớp với các bài trong lộ trình')
    }
    ids.forEach((id, idx) => { requireItem(path, id).sortOrder = idx + 1 })
  })
}

// ------------------------------------------------------------------ phản ứng với tiến độ bài học

export function markLessonStarted(lessonId) {
  const path = db.read('path')
  const item = path?.items.find((i) => i.lessonId === lessonId)
  if (item && (item.status === 'LOCKED' || item.status === 'AVAILABLE')) {
    item.status = 'IN_PROGRESS'
    refresh(path)
    db.write('path', path)
  }
}

export function markLessonCompleted(lessonId) {
  const path = db.read('path')
  const item = path?.items.find((i) => i.lessonId === lessonId)
  if (item && item.status !== 'COMPLETED') {
    item.status = 'COMPLETED'
    item.completedAt = nowIso()
    refresh(path)
    db.write('path', path)
  }
}

// ------------------------------------------------------------------ DTO

function toResponse(path) {
  const progress = db.read('lessonProgress')
  const items = [...path.items].sort((a, b) => a.sortOrder - b.sortOrder).filter((i) => getLesson(i.lessonId))
  const minutes = (i) => getLesson(i.lessonId).estimatedMinutes
  const completed = items.filter((i) => i.status === 'COMPLETED').length
  const skipped = items.filter((i) => i.status === 'SKIPPED').length
  const pending = items.filter((i) => !isDone(i))
  return {
    id: path.id, name: path.name, goalType: path.goalType, level: levelResponse(path.levelId), source: path.source,
    status: path.status, dailyMinutes: path.dailyMinutes, createdAt: path.createdAt,
    totalItems: items.length, completedItems: completed, skippedItems: skipped,
    percent: items.length ? Math.round(((completed + skipped) * 1000) / items.length) / 10 : 0,
    totalMinutes: items.reduce((s, i) => s + minutes(i), 0),
    remainingMinutes: pending.reduce((s, i) => s + minutes(i), 0),
    estimatedFinishDate: pending.map((i) => i.plannedDate).filter(Boolean).sort().at(-1) ?? null,
    currentItemId: items.find((i) => i.status === 'IN_PROGRESS' || i.status === 'AVAILABLE')?.id ?? null,
    items: items.map((i) => ({
      id: i.id, sortOrder: i.sortOrder, status: i.status, custom: i.custom, plannedDate: i.plannedDate ?? null,
      completedAt: i.completedAt ?? null, lesson: lessonCard(getLesson(i.lessonId), progress[i.lessonId]),
    })),
  }
}
