/** Thư viện mẫu câu theo chủ đề: duyệt, lưu mẫu câu yêu thích, đánh dấu đã luyện. */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { notFound } from '../errors'
import { loadSentenceTopics } from '../content'
import { recordStudyTime } from './activity'

const STUDY_SECONDS_PER_PRACTICE = 20

function withState(pattern, state) {
  const s = state[pattern.id]
  return { ...pattern, saved: !!s?.saved, practiceCount: s?.practiceCount ?? 0, lastPracticedAt: s?.lastPracticedAt ?? null }
}

export async function listTopics() {
  const topics = await loadSentenceTopics()
  const state = db.read('sentences')
  return topics.map(({ patterns, ...topic }) => ({
    ...topic,
    patternCount: patterns.length,
    savedCount: patterns.filter((p) => state[p.id]?.saved).length,
    practicedCount: patterns.filter((p) => state[p.id]?.practiceCount).length,
  }))
}

export async function getTopic(id) {
  const topic = (await loadSentenceTopics()).find((t) => t.id === Number(id) || t.slug === id)
  if (!topic) throw notFound('chủ đề mẫu câu', id)
  const state = db.read('sentences')
  return { ...topic, patterns: topic.patterns.map((p) => withState(p, state)) }
}

/** Các mẫu câu đã lưu (kèm tên chủ đề). */
export async function savedPatterns() {
  const state = db.read('sentences')
  return (await loadSentenceTopics()).flatMap((t) => t.patterns
    .filter((p) => state[p.id]?.saved)
    .map((p) => ({ ...withState(p, state), topicId: t.id, topicName: t.name })))
}

export function setSaved(patternId, saved) {
  db.update('sentences', (state) => {
    state[patternId] = { ...state[patternId], saved: !!saved }
  })
  return { patternId: Number(patternId), saved: !!saved }
}

/** Đánh dấu đã luyện (nghe / đọc to / tự kiểm tra) - cộng một chút thời gian học. */
export function markPracticed(patternId) {
  let count = 0
  db.update('sentences', (state) => {
    const s = { ...state[patternId] }
    s.practiceCount = (s.practiceCount ?? 0) + 1
    s.lastPracticedAt = nowIso()
    state[patternId] = s
    count = s.practiceCount
  })
  recordStudyTime(STUDY_SECONDS_PER_PRACTICE)
  return { patternId: Number(patternId), practiceCount: count }
}
