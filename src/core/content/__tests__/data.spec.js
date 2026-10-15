/**
 * Kiểm tra toàn bộ dữ liệu nội dung trong /data: id không trùng, tham chiếu hợp lệ, câu hỏi đúng cấu trúc.
 * Chạy: npm test (hoặc npm run validate:data). Thêm/sửa file JSON xong nên chạy lại.
 */
import { describe, expect, it } from 'vitest'
import {
  courses, exercises, levels, lessons, loadSentenceTopics, loadToeicPart, placementTest, toeicParts, vocabularySets, words,
} from '../index'
import { validateQuestion } from '../../domain/questionValidator'

const POS = new Set(['noun', 'verb', 'adjective', 'adverb', 'pronoun', 'preposition', 'conjunction', 'interjection', 'phrase', 'other'])
const SKILLS = new Set(['LISTENING', 'SPEAKING', 'READING', 'WRITING', 'VOCABULARY', 'GRAMMAR'])
const levelIds = new Set(levels.map((l) => l.id))
const dupes = (values) => values.filter((v, i) => values.indexOf(v) !== i)

describe('dữ liệu: khóa học & bài học', () => {
  it('id duy nhất, level & kỹ năng hợp lệ, phụ đề theo thứ tự thời gian', () => {
    expect(dupes(courses.map((c) => c.id))).toEqual([])
    expect(dupes(lessons.map((l) => l.id))).toEqual([])
    for (const c of courses) expect(levelIds.has(c.levelId), `course ${c.id}`).toBe(true)
    for (const l of lessons) {
      expect(SKILLS.has(l.skill), `lesson ${l.id} skill`).toBe(true)
      expect(l.estimatedMinutes > 0).toBe(true)
      if (l.mediaType && l.mediaType !== 'NONE') expect(l.mediaUrl, `lesson ${l.id} media`).toBeTruthy()
      l.transcript.forEach((s, i) => {
        expect(s.endMs > s.startMs, `lesson ${l.id} segment ${i}`).toBe(true)
        if (i) expect(s.startMs >= l.transcript[i - 1].startMs).toBe(true)
      })
    }
  })
})

describe('dữ liệu: bài test', () => {
  it('mọi câu hỏi đúng cấu trúc theo dạng', () => {
    const all = [placementTest, ...exercises].flatMap((t) => t.questions)
    expect(dupes(all.map((q) => q.id))).toEqual([])
    expect(all.flatMap(validateQuestion)).toEqual([])
  })

  it('bài xếp lớp chỉ dùng câu chấm tự động; bài tập gắn bài học có thật', () => {
    expect(placementTest.questions.every((q) => !['ESSAY', 'SPEAKING'].includes(q.type))).toBe(true)
    const lessonIds = new Set(lessons.map((l) => l.id))
    for (const t of exercises) expect(lessonIds.has(t.lessonId), `test ${t.id}`).toBe(true)
  })
})

describe('dữ liệu: từ vựng', () => {
  it('id và (từ, từ loại) không trùng; đủ trường bắt buộc', () => {
    expect(words.length).toBeGreaterThan(500)
    expect(dupes(words.map((w) => w.id))).toEqual([])
    expect(dupes(words.map((w) => `${w.headword.toLowerCase()}|${w.partOfSpeech}`))).toEqual([])
    for (const w of words) {
      expect(POS.has(w.partOfSpeech), `${w.headword} pos`).toBe(true)
      expect(w.meaningVi?.trim(), `${w.headword} meaning`).toBeTruthy()
      if (w.levelId != null) expect(levelIds.has(w.levelId), `${w.headword} level`).toBe(true)
    }
  })

  it('bộ từ tham chiếu đúng từ có thật, slug không trùng', () => {
    const ids = new Set(words.map((w) => w.id))
    expect(dupes(vocabularySets.map((s) => s.id))).toEqual([])
    expect(dupes(vocabularySets.map((s) => s.slug))).toEqual([])
    for (const s of vocabularySets) expect(s.wordIds.filter((id) => !ids.has(id)), s.slug).toEqual([])
  })
})

describe('dữ liệu: mẫu câu', () => {
  it('chủ đề & mẫu câu có id duy nhất, đủ nội dung', async () => {
    const topics = await loadSentenceTopics()
    expect(topics.length).toBeGreaterThan(0)
    const patterns = topics.flatMap((t) => t.patterns)
    expect(dupes(topics.map((t) => t.id))).toEqual([])
    expect(dupes(patterns.map((p) => p.id))).toEqual([])
    for (const p of patterns) {
      for (const field of ['pattern', 'meaning', 'example', 'exampleTranslation']) expect(p[field]?.trim(), `${p.id} ${field}`).toBeTruthy()
    }
  })
})

describe('dữ liệu: TOEIC', () => {
  it('có đủ Part 2-7', () => {
    expect(toeicParts).toEqual([2, 3, 4, 5, 6, 7])
  })

  it.each([2, 3, 4, 5, 6, 7])('Part %i: id duy nhất, đáp án hợp lệ, phương án không trùng', async (part) => {
    const data = await loadToeicPart(part)
    expect(dupes(data.items.map((i) => i.id))).toEqual([])
    const questions = data.items.flatMap((item) => {
      if (part === 2) return [{ id: item.id, options: item.responses, answer: item.answer, explanation: item.explanation }]
      if (part === 5) return [{ id: item.id, options: item.options, answer: item.answer, explanation: item.explanation }]
      return item.questions.map((q) => ({ ...q, id: q.id ?? `${item.id}-${q.blank}` }))
    })
    const optionCount = part === 2 ? 3 : 4
    for (const q of questions) {
      expect(q.options.length, `${q.id} options`).toBe(optionCount)
      expect(new Set(q.options.map((o) => o.trim().toLowerCase())).size, `${q.id} trùng phương án`).toBe(optionCount)
      expect(Number.isInteger(q.answer) && q.answer >= 0 && q.answer < optionCount, `${q.id} answer`).toBe(true)
      expect(q.explanation?.trim(), `${q.id} explanation`).toBeTruthy()
    }
    if (part === 5) for (const i of data.items) expect(i.sentence.split('-------').length - 1, i.id).toBe(1)
    if (part === 6) {
      for (const i of data.items) {
        const blanks = [...i.text.matchAll(/\{\{(\d)}}/g)].map((m) => Number(m[1]))
        expect(blanks, i.id).toEqual(i.questions.map((q) => q.blank))
      }
    }
    if (part === 3 || part === 4) {
      for (const i of data.items) {
        const names = new Set(i.speakers.map((s) => s.name))
        expect(i.script.every((l) => names.has(l.speaker)), `${i.id} speakers`).toBe(true)
      }
    }
  })
})
