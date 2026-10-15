/** Ôn flashcard theo lịch lặp lại ngắt quãng (SM-2). Không giới hạn số thẻ mới mỗi ngày; mỗi phiên chia lô nhỏ cho dễ theo dõi. */
import * as db from '../storage/db'
import { addDays, localDate, nowIso, startOfToday } from '../clock'
import { invalid, notFound } from '../errors'
import { DAY_MS, GRADES, preview, schedule } from '../domain/sm2'
import { record as recordActivity } from './activity'
import { findWord, wordResponse } from './vocabulary'

const NOT_SCHEDULED = new Set(['NEW', 'SUSPENDED'])
const MAX_QUEUE = 50
const DEFAULT_SECONDS_PER_CARD = 8
const MAX_SECONDS_PER_CARD = 60
const FORECAST_DAYS = 7

const dueTime = (uw) => new Date(uw.dueAt).getTime()

function toCard(uw) {
  return {
    userWordId: uw.id, status: uw.status, repetitions: uw.repetitions, intervalDays: uw.intervalDays, dueAt: uw.dueAt,
    contextSentence: uw.contextSentence, note: uw.note,
    word: wordResponse(findWord(uw.wordId) ?? { id: uw.wordId, headword: '?', partOfSpeech: 'other' }, uw),
    intervals: preview(uw),
  }
}

export function summary() {
  const notebook = db.read('notebook')
  const now = Date.now()
  const today = localDate()
  const counts = { NEW: 0, LEARNING: 0, REVIEW: 0, MASTERED: 0, SUSPENDED: 0 }
  notebook.forEach((uw) => { counts[uw.status] = (counts[uw.status] ?? 0) + 1 })
  const scheduled = notebook.filter((uw) => !NOT_SCHEDULED.has(uw.status))
  const dueNow = scheduled.filter((uw) => dueTime(uw) <= now).length

  // Dự báo: số thẻ đến hạn mỗi ngày trong 7 ngày tới (hôm nay gồm cả thẻ đang đến hạn)
  const forecast = Array.from({ length: FORECAST_DAYS }, (_, i) => ({ date: addDays(today, i), count: 0 }))
  forecast[0].count = dueNow
  const end = startOfToday().getTime() + FORECAST_DAYS * DAY_MS
  for (const uw of scheduled) {
    const t = dueTime(uw)
    if (t > now && t < end) {
      const day = forecast.find((f) => f.date === localDate(new Date(t)))
      if (day) day.count++
    }
  }
  const next = scheduled.filter((uw) => dueTime(uw) > now).sort((a, b) => dueTime(a) - dueTime(b))[0]
  const dayStart = startOfToday().getTime()
  return {
    total: notebook.length, newCards: counts.NEW, learning: counts.LEARNING, review: counts.REVIEW, mastered: counts.MASTERED,
    suspended: counts.SUSPENDED, dueNow, newAvailableToday: counts.NEW,
    reviewedToday: db.read('reviews').filter((r) => new Date(r.reviewedAt).getTime() >= dayStart).length,
    nextDueAt: next?.dueAt ?? null, forecast,
  }
}

/** Hàng đợi một phiên (tối đa `limit` thẻ): thẻ đến hạn (hạn sớm nhất trước), rồi thẻ mới theo thứ tự thêm vào sổ. */
export function queue(requestedLimit = 20) {
  const limit = Math.max(1, Math.min(Number(requestedLimit) || 20, MAX_QUEUE))
  const notebook = db.read('notebook')
  const now = Date.now()
  const due = notebook.filter((uw) => !NOT_SCHEDULED.has(uw.status) && dueTime(uw) <= now)
    .sort((a, b) => dueTime(a) - dueTime(b) || a.id - b.id).slice(0, limit)
  const newSlots = limit - due.length
  const fresh = newSlots <= 0 ? [] : notebook.filter((uw) => uw.status === 'NEW')
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt) || a.id - b.id).slice(0, newSlots)
  return {
    cards: [...due, ...fresh].map(toCard), dueCount: due.length, newCount: fresh.length,
  }
}

export function review(userWordId, grade, responseTimeMs) {
  if (!(grade in GRADES)) throw invalid('Thiếu mức đánh giá', 'grade')
  let card
  let wasNew = false
  db.update('notebook', (list) => {
    card = list.find((uw) => uw.id === Number(userWordId))
    if (!card) throw notFound('thẻ', userWordId)
    if (card.status === 'SUSPENDED') throw invalid('Từ này đang tạm ẩn khỏi lịch ôn')
    wasNew = card.status === 'NEW'
    const { state, delayMs } = schedule(card, grade)
    Object.assign(card, state, { dueAt: new Date(Date.now() + delayMs).toISOString(), lastReviewedAt: nowIso() })
  })
  db.update('reviews', (list) => [...list, {
    id: db.nextId('review'), userWordId: card.id, quality: GRADES[grade], wasNew, responseTimeMs: responseTimeMs ?? null,
    reviewedAt: nowIso(),
  }])
  // Thời gian học = thời gian suy nghĩ trên thẻ (tối đa 60 giây để không tính lúc bỏ máy)
  const seconds = responseTimeMs == null ? DEFAULT_SECONDS_PER_CARD
    : Math.max(1, Math.min(MAX_SECONDS_PER_CARD, Math.round(responseTimeMs / 1000)))
  recordActivity({ studySeconds: seconds, wordsLearned: wasNew && grade !== 'AGAIN' ? 1 : 0, wordsReviewed: 1 })
  return { card: toCard(card), grade, nextDueAt: card.dueAt, relearnInSession: grade === 'AGAIN' }
}
