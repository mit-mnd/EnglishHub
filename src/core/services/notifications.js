/**
 * Thông báo trong ứng dụng. Không có server nên nhắc học được tính mỗi khi ứng dụng hỏi số thông báo
 * chưa đọc (chuông thông báo hỏi 60 giây/lần khi tab đang mở) - xem runReminders().
 */
import * as db from '../storage/db'
import { localDate, localTime, nowIso } from '../clock'
import { notFound } from '../errors'
import { decideReminders } from '../domain/reminderPolicy'
import { paginate } from './shared'

export function createNotification(type, title, message, link = null) {
  const n = { id: db.nextId('notification'), type, title, message, link, read: false, readAt: null, createdAt: nowIso() }
  db.update('notifications', (list) => [...list, n])
  return n
}

export function listNotifications({ unreadOnly = false, page = 0, size = 20 } = {}) {
  runReminders()
  const all = db.read('notifications').filter((n) => !unreadOnly || !n.read).reverse()   // mới nhất trước
  return paginate(all, { page, size })
}

export function unreadCount() {
  runReminders()
  return { count: db.read('notifications').filter((n) => !n.read).length }
}

export function markRead(id) {
  let found
  db.update('notifications', (list) => {
    found = list.find((n) => n.id === Number(id))
    if (!found) throw notFound('thông báo', id)
    if (!found.read) Object.assign(found, { read: true, readAt: nowIso() })
  })
  return found
}

export function markAllRead() {
  let updated = 0
  db.update('notifications', (list) => list.forEach((n) => {
    if (!n.read) {
      Object.assign(n, { read: true, readAt: nowIso() })
      updated++
    }
  }))
  return { updated }
}

export function removeNotification(id) {
  db.update('notifications', (list) => {
    if (!list.some((n) => n.id === Number(id))) throw notFound('thông báo', id)
    return list.filter((n) => n.id !== Number(id))
  })
}

/** Đã gửi thông báo loại này hôm nay chưa (theo giờ máy). */
function sentToday(type, today) {
  return db.read('notifications').some((n) => n.type === type && localDate(new Date(n.createdAt)) === today)
}

/** Tạo nhắc học / cảnh báo mất chuỗi nếu đến lúc (mỗi loại tối đa 1 lần/ngày). */
export function runReminders() {
  const profile = db.read('profile')
  if (!profile) return []
  const today = localDate()
  const daily = db.read('daily')[today]
  const streak = db.read('streak')
  const alive = streak.lastActivityDate && streak.lastActivityDate >= addDaysSafe(today, -1)
  const nowMs = Date.now()
  const dueCards = db.read('notebook')
    .filter((w) => w.status !== 'NEW' && w.status !== 'SUSPENDED' && new Date(w.dueAt).getTime() <= nowMs).length

  const reminders = decideReminders({
    localTime: localTime(),
    reminderEnabled: profile.reminderEnabled,
    reminderTime: profile.reminderTime,
    studyReminderSentToday: sentToday('STUDY_REMINDER', today),
    streakWarningSentToday: sentToday('STREAK', today),
    studyMinutesToday: Math.round((daily?.studySeconds ?? 0) / 60),
    dailyGoalMinutes: profile.dailyGoalMinutes,
    currentStreak: alive ? streak.current : 0,
    studiedToday: streak.lastActivityDate === today,
    dueCards,
  })
  return reminders.map((r) => createNotification(r.type, r.title, r.message, r.link))
}

function addDaysSafe(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number)
  return localDate(new Date(y, m - 1, d + days))
}
