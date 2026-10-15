/**
 * Từ điển & sổ từ cá nhân.
 * Tra từ: kho từ nội bộ (JSON) trước, không có thì tra các từ điển trực tuyến (dictionary.js, cần mạng) và lưu lại vào máy.
 */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid, notFound } from '../errors'
import { findWordsByHeadword, getLesson, getVocabularySet, getWord, vocabularySets } from '../content'
import { candidates, normalizeQuery, VALID_QUERY } from '../domain/lemmatize'
import { newCardState, statusFor } from '../domain/sm2'
import { lookupExternal, parseDictionary, translateVi } from './dictionary'
import { likeMatch, paginate, sortItems } from './shared'

export { parseDictionary }

/** Id của từ tra từ từ điển ngoài bắt đầu từ đây để không trùng id trong kho nội dung. */
const CUSTOM_WORD_ID_BASE = 1_000_000
const MAX_TRANSLATE_CHARS = 300

/** Tìm từ theo id trong kho nội dung hoặc trong các từ đã tra từ từ điển ngoài. */
export function findWord(id) {
  return getWord(id) ?? db.read('customWords').find((w) => w.id === Number(id)) ?? null
}

export function wordResponse(word, saved) {
  return {
    id: word.id, headword: word.headword, partOfSpeech: word.partOfSpeech, phoneticUk: word.phoneticUk ?? null,
    phoneticUs: word.phoneticUs ?? null, audioUrlUk: word.audioUrlUk ?? null, audioUrlUs: word.audioUrlUs ?? null,
    meaningVi: word.meaningVi ?? '', definitionEn: word.definitionEn ?? null, example: word.example ?? null,
    exampleTranslation: word.exampleTranslation ?? null, saved: !!saved, userWordId: saved?.id ?? null,
    autoTranslated: word.source === 'DICTIONARY_API' && !!word.meaningVi,   // nghĩa tiếng Việt do dịch máy, chỉ tham khảo
  }
}

const notebookByWord = () => new Map(db.read('notebook').map((uw) => [uw.wordId, uw]))

// ------------------------------------------------------------------ tra từ

export async function lookup(rawQuery) {
  const query = normalizeQuery(rawQuery)
  if (query.length > 60 || !VALID_QUERY.test(query)) throw invalid('Từ cần tra không hợp lệ')
  const owned = notebookByWord()
  const list = candidates(query)
  const respond = (source, matchedHeadword, words) => ({
    query, matchedHeadword, source, entries: words.map((w) => wordResponse(w, owned.get(w.id))),
  })

  // 1. Kho nội bộ + từ đã tra trước đây
  const custom = db.read('customWords')
  for (const candidate of list) {
    const hits = [...findWordsByHeadword(candidate), ...custom.filter((w) => w.headword.toLowerCase() === candidate)]
    if (hits.length) return respond('LOCAL', candidate, await withVietnamese(hits))
  }

  // 2. Từ điển trực tuyến: thử từ gốc tra được và tối đa 1 dạng rút gọn.
  //    Dạng nào chỉ dịch được (không có định nghĩa) thì giữ làm phương án cuối.
  let translationOnly = null
  let allFailed = true
  for (const candidate of list.slice(0, 2)) {
    const found = await lookupExternal(candidate)
    allFailed &&= found.failed
    if (found.englishFound) {
      const saved = found.entries.map(cacheCustomWord)
      return respond('DICTIONARY_API', saved[0].headword, saved)
    }
    if (found.entries.length && !translationOnly) translationOnly = found.entries
  }
  if (translationOnly) {
    const saved = translationOnly.map(cacheCustomWord)
    return respond('DICTIONARY_API', saved[0].headword, saved)
  }
  return { query, matchedHeadword: null, source: 'NONE', offline: allFailed, entries: [] }
}

/** Từ đã tra trước đây mà chưa có nghĩa tiếng Việt: bổ sung một lần (lỗi mạng thì để lần sau). */
async function withVietnamese(words) {
  const target = words.find((w) => w.source === 'DICTIONARY_API' && !w.meaningVi && !w.viTried)
  if (!target) return words
  const { text, failed } = await translateVi(target.headword)
  if (failed) return words
  const patch = { meaningVi: text, viTried: true }
  db.update('customWords', (list) => list.map((w) => (w.id === target.id ? { ...w, ...patch } : w)))
  return words.map((w) => (w.id === target.id ? { ...w, ...patch } : w))
}

const translationCache = new Map()

/** Dịch một đoạn văn bản (bôi đen nhiều từ / cả câu) sang tiếng Việt. Không lưu vào sổ từ. */
export async function translate(rawText) {
  const text = (rawText ?? '').replace(/\s+/g, ' ').trim()
  if (!text || text.length > MAX_TRANSLATE_CHARS || !/[A-Za-z]/.test(text)) {
    throw invalid(`Đoạn cần dịch không hợp lệ (tối đa ${MAX_TRANSLATE_CHARS} ký tự)`)
  }
  if (translationCache.has(text)) return { text, translation: translationCache.get(text), offline: false }
  const { text: translation, failed } = await translateVi(text)
  if (translation) translationCache.set(text, translation)
  return { text, translation: translation || null, offline: !!failed }
}

/** Lưu từ tra được vào máy; trùng từ + từ loại thì dùng lại (bổ sung nghĩa tiếng Việt nếu bản cũ chưa có). */
function cacheCustomWord(entry) {
  const existing = db.read('customWords').find((w) => w.headword.toLowerCase() === entry.headword.toLowerCase()
    && w.partOfSpeech === entry.partOfSpeech)
  if (existing) {
    if (existing.meaningVi || !entry.meaningVi) return existing
    const updated = { ...existing, meaningVi: entry.meaningVi, viTried: true }
    db.update('customWords', (list) => list.map((w) => (w.id === existing.id ? updated : w)))
    return updated
  }
  const word = { ...entry, id: CUSTOM_WORD_ID_BASE + db.nextId('customWord'), meaningVi: entry.meaningVi ?? '', source: 'DICTIONARY_API' }
  db.update('customWords', (list) => [...list, word])
  return word
}

// ------------------------------------------------------------------ sổ từ

/** Bản ghi sổ từ mới: đến hạn ôn ngay (thẻ mới). */
function newEntry(wordId, extra = {}) {
  return {
    id: db.nextId('userWord'), wordId, source: 'MANUAL', note: null, contextSentence: null, sourceLessonId: null,
    ...newCardState(), dueAt: nowIso(), lastReviewedAt: null, createdAt: nowIso(), ...extra,
  }
}

export function userWordResponse(uw) {
  const word = findWord(uw.wordId)
  return {
    id: uw.id, word: wordResponse(word ?? { id: uw.wordId, headword: '(đã xóa)', partOfSpeech: 'other' }, uw),
    source: uw.source, status: uw.status, note: uw.note, contextSentence: uw.contextSentence,
    sourceLessonId: uw.sourceLessonId, sourceLessonTitle: uw.sourceLessonId ? getLesson(uw.sourceLessonId)?.title ?? null : null,
    dueAt: uw.dueAt, createdAt: uw.createdAt,
  }
}

/** Idempotent: từ đã có trong sổ thì trả về bản ghi cũ (chỉ cập nhật ghi chú nếu có gửi). */
export function saveWord({ wordId, sourceLessonId = null, contextSentence = null, note } = {}) {
  if (!findWord(wordId)) throw notFound('từ', wordId)
  let result
  db.update('notebook', (list) => {
    let uw = list.find((x) => x.wordId === Number(wordId))
    if (!uw) {
      uw = newEntry(Number(wordId), {
        source: sourceLessonId ? 'READING_LOOKUP' : 'MANUAL',
        sourceLessonId: sourceLessonId && getLesson(sourceLessonId) ? Number(sourceLessonId) : null,
        contextSentence: contextSentence?.trim() || null,
      })
      list.push(uw)
    }
    if (note !== undefined) uw.note = note?.trim() || null
    result = uw
  })
  return userWordResponse(result)
}

const NOTEBOOK_SORTS = { createdAt: (u) => u.createdAt, dueAt: (u) => u.dueAt, status: (u) => u.status }

export function listNotebook({ keyword, status, page = 0, size = 20, sort = 'createdAt,desc' } = {}) {
  const match = likeMatch(keyword)
  const filtered = db.read('notebook').filter((uw) => {
    if (status && uw.status !== status) return false
    if (!match) return true
    const w = findWord(uw.wordId)
    return match(w?.headword, w?.meaningVi, uw.note)
  })
  const result = paginate(sortItems(filtered, sort, NOTEBOOK_SORTS), { page, size })
  return { ...result, items: result.items.map(userWordResponse) }
}

function mutateEntry(id, fn) {
  let result
  db.update('notebook', (list) => {
    const uw = list.find((x) => x.id === Number(id))
    if (!uw) throw notFound('từ trong sổ', id)
    fn(uw)
    result = uw
  })
  return userWordResponse(result)
}

export function updateNote(id, note) {
  if ((note ?? '').length > 500) throw invalid('Ghi chú tối đa 500 ký tự', 'note')
  return mutateEntry(id, (uw) => { uw.note = note?.trim() || null })
}

/** Tạm ẩn khỏi lịch ôn hoặc đưa trở lại (tính lại trạng thái theo tiến độ, quá hạn thì ôn ngay). */
export function setSuspended(id, suspended) {
  return mutateEntry(id, (uw) => {
    if (suspended) {
      uw.status = 'SUSPENDED'
    } else if (uw.status === 'SUSPENDED') {
      uw.status = statusFor(uw.intervalDays, uw.repetitions)
      if (new Date(uw.dueAt) < new Date()) uw.dueAt = nowIso()
    }
  })
}

export function removeWord(id) {
  db.update('notebook', (list) => {
    if (!list.some((x) => x.id === Number(id))) throw notFound('từ trong sổ', id)
    return list.filter((x) => x.id !== Number(id))
  })
  db.update('reviews', (list) => list.filter((r) => r.userWordId !== Number(id)))
}

/** Headword (chữ thường) các từ đã lưu - để tô sáng trong bài đọc. */
export function savedHeadwords() {
  return [...new Set(db.read('notebook').map((uw) => findWord(uw.wordId)?.headword.toLowerCase()).filter(Boolean))]
}

// ------------------------------------------------------------------ bộ từ theo chủ đề

function setSummary(set, owned) {
  const ws = set.wordIds.map(getWord).filter(Boolean)
  return {
    id: set.id, name: set.name, slug: set.slug, description: set.description ?? null, levelId: set.levelId ?? null,
    wordCount: ws.length, addedCount: ws.filter((w) => owned.has(w.id)).length, previewWords: ws.slice(0, 5).map((w) => w.headword),
  }
}

export function listSets() {
  const owned = notebookByWord()
  return vocabularySets.map((s) => setSummary(s, owned))
}

export function getSet(id) {
  const set = getVocabularySet(id)
  if (!set) throw notFound('bộ từ', id)
  const owned = notebookByWord()
  return { set: setSummary(set, owned), words: set.wordIds.map(getWord).filter(Boolean).map((w) => wordResponse(w, owned.get(w.id))) }
}

/** Thêm mọi từ của bộ vào sổ (bỏ qua từ đã có); lệch nhau 1 ms để học đúng thứ tự trong bộ. */
export function addSetToNotebook(id) {
  const set = getVocabularySet(id)
  if (!set) throw notFound('bộ từ', id)
  const base = Date.now()
  let added = 0
  db.update('notebook', (list) => {
    const owned = new Set(list.map((uw) => uw.wordId))
    for (const wordId of set.wordIds) {
      if (owned.has(wordId) || !getWord(wordId)) continue
      list.push(newEntry(wordId, { source: 'VOCAB_SET', dueAt: new Date(base + added).toISOString(), createdAt: new Date(base + added).toISOString() }))
      added++
    }
  })
  return { added, alreadyInNotebook: set.wordIds.length - added }
}
