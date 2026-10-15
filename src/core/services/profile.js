/**
 * Hồ sơ người học - thay cho tài khoản đăng nhập. Chỉ có MỘT hồ sơ trên mỗi trình duyệt,
 * được khai báo ở lần đầu mở ứng dụng.
 */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid } from '../errors'
import { getLevel } from '../content'
import { createNotification } from './notifications'

const GOALS = ['GENERAL', 'COMMUNICATION', 'TOEIC', 'IELTS']
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/

/** Hồ sơ dạng DTO (currentLevel là object level đầy đủ như API cũ). */
export function toProfileResponse(p) {
  if (!p) return null
  return { ...p, currentLevel: p.currentLevelId ? { ...getLevel(p.currentLevelId) } : null }
}

export function getProfile() {
  return toProfileResponse(db.read('profile'))
}

function validate(payload) {
  const name = (payload.fullName ?? '').trim()
  if (!name) throw invalid('Vui lòng nhập tên của bạn', 'fullName')
  if (name.length > 150) throw invalid('Tên tối đa 150 ký tự', 'fullName')
  if (!GOALS.includes(payload.learningGoal)) throw invalid('Mục tiêu học không hợp lệ', 'learningGoal')
  const minutes = Number(payload.dailyGoalMinutes)
  if (!Number.isInteger(minutes) || minutes < 5 || minutes > 480) throw invalid('Mục tiêu mỗi ngày từ 5 đến 480 phút', 'dailyGoalMinutes')
  if (payload.reminderTime && !TIME.test(payload.reminderTime)) throw invalid('Giờ nhắc không hợp lệ (HH:mm)', 'reminderTime')
  if (payload.levelId != null && !getLevel(payload.levelId)) throw invalid('Trình độ không hợp lệ', 'levelId')
  return { fullName: name, learningGoal: payload.learningGoal, dailyGoalMinutes: minutes }
}

/** Khai báo lần đầu. levelId: trình độ tự chọn (null = sẽ làm bài kiểm tra xếp lớp). */
export function createProfile(payload) {
  const base = validate(payload)
  const profile = {
    id: 1,
    ...base,
    avatarUrl: null,
    currentLevelId: payload.levelId ?? null,
    reminderEnabled: payload.reminderEnabled ?? true,
    reminderTime: payload.reminderTime || '20:00',
    placementCompletedAt: null,
    createdAt: nowIso(),
  }
  db.write('profile', profile)
  createNotification('SYSTEM', `Chào mừng ${base.fullName.split(/\s+/).at(-1)} đến với EnglishHub! 👋`,
    profile.currentLevelId
      ? 'Lộ trình học đã được tạo theo trình độ bạn chọn. Bạn có thể làm bài kiểm tra xếp lớp bất cứ lúc nào để điều chỉnh.'
      : 'Hãy làm bài kiểm tra xếp lớp 20 phút để nhận lộ trình phù hợp với trình độ.',
    profile.currentLevelId ? '/app/path' : '/app/placement')
  return toProfileResponse(profile)
}

/** Sửa hồ sơ (giống PUT /users/me cũ). */
export function updateProfile(payload) {
  const current = db.read('profile')
  if (!current) throw invalid('Bạn chưa khai báo thông tin người học')
  const base = validate({ ...current, ...payload })
  const updated = {
    ...current,
    ...base,
    reminderEnabled: payload.reminderEnabled ?? current.reminderEnabled,
    reminderTime: payload.reminderTime === undefined ? current.reminderTime : payload.reminderTime || null,
    currentLevelId: payload.levelId !== undefined ? payload.levelId : current.currentLevelId,
  }
  db.write('profile', updated)
  return toProfileResponse(updated)
}

/** Cập nhật trình độ sau bài xếp lớp. */
export function setPlacementLevel(levelId) {
  db.update('profile', (p) => {
    if (!p) return
    p.currentLevelId = levelId
    p.placementCompletedAt = nowIso()
  })
}
