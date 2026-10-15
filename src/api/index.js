/**
 * Lớp API của ứng dụng. Trước đây gọi HTTP tới Spring Boot; nay gọi thẳng các service chạy trên trình duyệt
 * (src/core/services) - giữ nguyên tên hàm và dạng dữ liệu trả về nên các màn hình gần như không phải sửa.
 *
 * Mọi hàm trả về Promise; kết quả là bản sao sâu (màn hình sửa thoải mái không ảnh hưởng dữ liệu đã lưu).
 * Lỗi nghiệp vụ là AppError { status, code, message, fieldErrors } như API cũ.
 */
import * as db from '@/core/storage/db'
import { levels } from '@/core/content'
import * as profile from '@/core/services/profile'
import * as catalog from '@/core/services/catalog'
import * as progress from '@/core/services/progress'
import * as path from '@/core/services/path'
import * as placement from '@/core/services/placement'
import * as exercises from '@/core/services/exercises'
import * as vocabulary from '@/core/services/vocabulary'
import * as flashcards from '@/core/services/flashcards'
import * as skills from '@/core/services/skills'
import * as dashboard from '@/core/services/dashboard'
import * as notifications from '@/core/services/notifications'
import * as games from '@/core/services/games'
import * as toeic from '@/core/services/toeic'
import * as sentences from '@/core/services/sentences'

/**
 * Tham số từ màn hình thường là object reactive của Vue (Proxy) - không lưu / structuredClone được.
 * Chuyển về dữ liệu thuần giống như khi gửi JSON qua HTTP (giữ nguyên FormData, Blob, File).
 */
const plain = (arg) => (arg == null || typeof arg !== 'object' || arg instanceof FormData || arg instanceof Blob
  ? arg
  : JSON.parse(JSON.stringify(arg)))

/** Bọc một hàm service: luôn trả Promise, tham số là dữ liệu thuần, kết quả là bản sao sâu. */
const call = (fn) => async (...args) => {
  const result = await fn(...args.map(plain))
  return result == null || result instanceof Blob ? result : structuredClone(result)
}

export const profileApi = {
  get: call(profile.getProfile),
  create: call(profile.createProfile),
  update: call(profile.updateProfile),
}

/** Tương thích màn hình cũ (Tài khoản) */
export const userApi = {
  me: profileApi.get,
  updateProfile: profileApi.update,
}

export const levelApi = {
  list: call(() => levels),
}

export const placementApi = {
  info: call(placement.getInfo),
  start: call(placement.startOrResume),
  saveAnswer: call(placement.saveAnswer),
  submit: call(placement.submit),
  result: call(placement.getResult),
  history: call(placement.history),
}

export const catalogApi = {
  courses: call(catalog.listCourses),
  course: call(catalog.getCourseDetail),
  searchLessons: call(catalog.searchLessons),
  lesson: call(catalog.getLessonDetail),
}

export const progressApi = {
  heartbeat: call(progress.heartbeat),
  complete: call((lessonId, payload = {}) => progress.complete(lessonId, payload)),
}

/** get trả null khi chưa có lộ trình. */
export const pathApi = {
  get: call(path.getPath),
  generate: call((payload) => path.generatePath(payload ?? {})),
  addLesson: call(path.addLesson),
  removeItem: call(path.removeItem),
  skip: call(path.skipItem),
  unskip: call(path.unskipItem),
  reorder: call(path.reorder),
}

export const vocabularyApi = {
  lookup: call(vocabulary.lookup),
  list: call(vocabulary.listNotebook),
  headwords: call(vocabulary.savedHeadwords),
  save: call(vocabulary.saveWord),
  updateNote: call(vocabulary.updateNote),
  suspend: call(vocabulary.setSuspended),
  remove: call(vocabulary.removeWord),
  sets: call(vocabulary.listSets),
  set: call(vocabulary.getSet),
  addSet: call(vocabulary.addSetToNotebook),
}

export const flashcardApi = {
  summary: call(flashcards.summary),
  queue: call((limit = 20) => flashcards.queue(limit)),
  review: call(flashcards.review),
}

export const exerciseApi = {
  list: call(exercises.listForLesson),
  start: call(exercises.start),
  check: call(exercises.check),
  finish: call(exercises.finish),
}

export const speakingApi = {
  /** form: FormData gồm audio (WAV), questionId | referenceText, lessonId, clientTranscript */
  submit: call(skills.submitSpeaking),
  audio: call(skills.speakingAudio),
}

export const writingApi = {
  evaluate: call(skills.evaluateWritingSubmission),
}

export const dashboardApi = {
  get: call((range = '7d') => dashboard.getDashboard(range)),
}

export const notificationApi = {
  list: call((params) => notifications.listNotifications(params)),
  unreadCount: call(notifications.unreadCount),
  markRead: call(notifications.markRead),
  markAllRead: call(notifications.markAllRead),
  remove: call(notifications.removeNotification),
}

export const gameApi = {
  words: call(games.gameWords),
  submit: call(games.submitResult),
  stats: call(games.gameStats),
}

export const toeicApi = {
  overview: call(toeic.overview),
  practice: call(toeic.practiceSet),
  miniTest: call(toeic.miniTest),
  submit: call(toeic.submitSession),
}

export const sentenceApi = {
  topics: call(sentences.listTopics),
  topic: call(sentences.getTopic),
  saved: call(sentences.savedPatterns),
  setSaved: call(sentences.setSaved),
  practiced: call(sentences.markPracticed),
}

/** Sao lưu / khôi phục / xóa dữ liệu trên máy. */
export const dataApi = {
  exportBackup: call(db.exportAll),
  importBackup: call(db.importAll),
  reset: call(db.clearAll),
  usageBytes: call(db.usageBytes),
}
