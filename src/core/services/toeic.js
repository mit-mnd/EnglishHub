/**
 * Luyện thi TOEIC (Listening Part 2-4, Reading Part 5-7) từ dữ liệu JSON trong data/toeic.
 *
 * Mọi Part được chuẩn hóa về "nhóm câu hỏi" (group) để màn hình làm bài dùng chung một kiểu hiển thị:
 *   { id, part, kind: 'response' | 'talk' | 'sentence' | 'passage' | 'documents', ...ngữ liệu, questions: [...] }
 *   question: { id, prompt?, options: string[], answer: number, explanation, blank? }
 * Part 2-4 không có file ghi âm: ứng dụng đọc kịch bản bằng giọng tổng hợp của trình duyệt.
 */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid, notFound } from '../errors'
import { loadToeicPart, toeicParts } from '../content'
import { recordStudyTime } from './activity'
import { requireProfile, shuffle } from './shared'

export const PART_META = {
  2: { section: 'LISTENING', name: 'Hỏi - đáp', practiceGroups: 10 },
  3: { section: 'LISTENING', name: 'Hội thoại', practiceGroups: 3 },
  4: { section: 'LISTENING', name: 'Bài nói ngắn', practiceGroups: 3 },
  5: { section: 'READING', name: 'Hoàn thành câu', practiceGroups: 15 },
  6: { section: 'READING', name: 'Hoàn thành đoạn văn', practiceGroups: 2 },
  7: { section: 'READING', name: 'Đọc hiểu', practiceGroups: 3 },
}

/** Cấu trúc đề thi thử rút gọn (~40 câu, 35 phút). */
export const MINI_TEST = { durationMinutes: 35, groups: { 2: 6, 3: 2, 4: 2, 5: 10, 6: 1, 7: 2 } }
const MAX_STUDY_SECONDS = 3600

// ------------------------------------------------------------------ chuẩn hóa dữ liệu

function normalize(part, item) {
  const base = { id: item.id, part, title: item.title ?? null, translation: item.translation ?? null }
  switch (part) {
    case 2:
      return {
        ...base, kind: 'response', questionVoice: item.questionVoice ?? 'female', script: item.question,
        questions: [{ id: item.id, prompt: null, options: item.responses, answer: item.answer, explanation: item.explanation, type: item.type }],
      }
    case 3:
    case 4:
      return {
        ...base, kind: 'talk', speakers: item.speakers, script: item.script, graphic: item.graphic ?? null, talkType: item.type ?? null,
        questions: item.questions.map((q) => ({ id: q.id, prompt: q.question, options: q.options, answer: q.answer, explanation: q.explanation })),
      }
    case 5:
      return {
        ...base, kind: 'sentence', category: item.category, difficulty: item.difficulty,
        questions: [{ id: item.id, prompt: item.sentence, options: item.options, answer: item.answer, explanation: item.explanation }],
      }
    case 6:
      return {
        ...base, kind: 'passage', docType: item.type, text: item.text,
        questions: item.questions.map((q) => ({
          id: `${item.id}-${q.blank}`, blank: q.blank, prompt: null, options: q.options, answer: q.answer, explanation: q.explanation,
        })),
      }
    case 7:
      return {
        ...base, kind: 'documents', format: item.format, documents: item.documents,
        questions: item.questions.map((q) => ({ id: q.id, prompt: q.question, options: q.options, answer: q.answer, explanation: q.explanation, type: q.type })),
      }
    default:
      throw invalid(`Part ${part} chưa được hỗ trợ`)
  }
}

async function groupsOf(part) {
  const data = await loadToeicPart(part)
  if (!data) throw notFound(`dữ liệu TOEIC Part ${part}`)
  return { data, groups: data.items.map((item) => normalize(part, item)) }
}

/** Số lần đã làm mỗi nhóm câu hỏi (để ưu tiên nhóm chưa làm). */
function seenCounts() {
  const counts = new Map()
  for (const r of db.read('toeic')) for (const g of r.groups ?? []) counts.set(g.id, (counts.get(g.id) ?? 0) + 1)
  return counts
}

/** Chọn n nhóm: nhóm làm ít lần nhất trước, cùng số lần thì ngẫu nhiên. */
function pick(groups, n, seen) {
  return shuffle(groups).sort((a, b) => (seen.get(a.id) ?? 0) - (seen.get(b.id) ?? 0)).slice(0, n)
}

// ------------------------------------------------------------------ API

export async function overview() {
  requireProfile()
  const results = db.read('toeic')
  const parts = []
  for (const part of toeicParts) {
    const { data, groups } = await groupsOf(part)
    const mine = results.filter((r) => r.mode === 'PRACTICE' && r.part === part)
    const seen = seenCounts()
    parts.push({
      part, section: PART_META[part].section, name: PART_META[part].name, title: data.title, description: data.description,
      groupCount: groups.length, questionCount: groups.reduce((s, g) => s + g.questions.length, 0),
      completedGroups: groups.filter((g) => seen.has(g.id)).length,
      sessions: mine.length, bestPercent: mine.length ? Math.max(...mine.map((r) => r.percent)) : null,
      lastPercent: mine.at(-1)?.percent ?? null,
    })
  }
  const tests = results.filter((r) => r.mode === 'TEST')
  return {
    parts,
    miniTest: {
      ...MINI_TEST, questionCount: null,
      attempts: tests.length, bestPercent: tests.length ? Math.max(...tests.map((r) => r.percent)) : null,
      lastEstimate: tests.at(-1)?.estimate ?? null,
    },
    recent: [...results].reverse().slice(0, 10).map(toSummary),
  }
}

/** Bộ câu luyện tập của một Part. */
export async function practiceSet(part, groupCount) {
  requireProfile()
  const p = Number(part)
  if (!PART_META[p]) throw invalid('Part không hợp lệ')
  const { data, groups } = await groupsOf(p)
  const n = Math.max(1, Math.min(Number(groupCount) || PART_META[p].practiceGroups, groups.length))
  return { mode: 'PRACTICE', part: p, title: data.title, description: data.description, groups: pick(groups, n, seenCounts()) }
}

/** Đề thi thử rút gọn: lấy nhóm câu từ mọi Part theo cấu trúc MINI_TEST, thứ tự Part 2 -> 7. */
export async function miniTest() {
  requireProfile()
  const seen = seenCounts()
  const groups = []
  for (const part of toeicParts) {
    const want = MINI_TEST.groups[part]
    if (!want) continue
    groups.push(...pick((await groupsOf(part)).groups, want, seen))
  }
  return { mode: 'TEST', part: null, title: 'Đề thi thử TOEIC rút gọn', durationMinutes: MINI_TEST.durationMinutes, groups }
}

/**
 * Lưu kết quả một lượt: answers = { questionId: chosenIndex } (bỏ trống = sai).
 * Chấm lại trên dữ liệu gốc để kết quả không phụ thuộc màn hình.
 */
export async function submitSession({ mode, part = null, groupIds, answers = {}, durationSeconds = 0 }) {
  requireProfile()
  if (!['PRACTICE', 'TEST'].includes(mode)) throw invalid('mode không hợp lệ')
  const neededParts = [...new Set(groupIds.map((id) => Number(String(id).match(/^p(\d)/)?.[1])))]
  const byId = new Map()
  for (const p of neededParts) (await groupsOf(p)).groups.forEach((g) => byId.set(g.id, g))
  const groups = groupIds.map((id) => byId.get(id) ?? (() => { throw notFound('nhóm câu hỏi', id) })())

  const bySection = { LISTENING: { correct: 0, total: 0 }, READING: { correct: 0, total: 0 } }
  const groupResults = groups.map((g) => {
    let correct = 0
    for (const q of g.questions) if (answers[q.id] === q.answer) correct++
    const section = bySection[PART_META[g.part].section]
    section.correct += correct
    section.total += g.questions.length
    return { id: g.id, part: g.part, correct, total: g.questions.length }
  })
  const correct = groupResults.reduce((s, g) => s + g.correct, 0)
  const total = groupResults.reduce((s, g) => s + g.total, 0)
  const result = {
    id: db.nextId('toeic'), mode, part: part == null ? null : Number(part), correct, total,
    percent: total ? Math.round((correct * 1000) / total) / 10 : 0,
    sections: bySection, estimate: mode === 'TEST' ? estimateScore(bySection) : null,
    durationSeconds: Math.max(0, Math.round(durationSeconds)), groups: groupResults, createdAt: nowIso(),
  }
  db.update('toeic', (list) => [...list, result])
  if (result.durationSeconds > 0) recordStudyTime(Math.min(result.durationSeconds, MAX_STUDY_SECONDS))
  return toSummary(result)
}

/**
 * Ước lượng điểm TOEIC (mỗi phần 5-495) từ tỉ lệ đúng - CHỈ mang tính tham khảo,
 * vì đề rút gọn và không có bảng quy đổi chính thức.
 */
export function estimateScore(sections) {
  const scale = ({ correct, total }) => (total ? Math.round((5 + (490 * correct) / total) / 5) * 5 : null)
  const listening = scale(sections.LISTENING)
  const reading = scale(sections.READING)
  return { listening, reading, total: (listening ?? 0) + (reading ?? 0) }
}

const toSummary = (r) => ({
  id: r.id, mode: r.mode, part: r.part, correct: r.correct, total: r.total, percent: r.percent,
  sections: r.sections, estimate: r.estimate, durationSeconds: r.durationSeconds, createdAt: r.createdAt,
})
