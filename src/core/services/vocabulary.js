/**
 * Từ điển & sổ từ cá nhân.
 * Tra từ: kho từ nội bộ (JSON) trước, không có thì gọi Free Dictionary API (cần mạng) và lưu lại vào máy.
 */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid, notFound } from '../errors'
import { findWordsByHeadword, getLesson, getVocabularySet, getWord, vocabularySets } from '../content'
import { candidates, normalizeQuery, VALID_QUERY } from '../domain/lemmatize'
import { newCardState, statusFor } from '../domain/sm2'
import { likeMatch, paginate, sortItems } from './shared'

/** Id của từ tra từ từ điển ngoài bắt đầu từ đây để không trùng id trong kho nội dung. */
const CUSTOM_WORD_ID_BASE = 1_000_000
const DICTIONARY_URL = 'https://api.dictionaryapi.dev/api/v2/entries/en/'
const KNOWN_POS = new Set(['noun', 'verb', 'adjective', 'adverb', 'pronoun', 'preposition', 'conjunction', 'interjection'])

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
  }
}

const notebookByWord = () => new Map(db.read('notebook').map((uw) => [uw.wordId, uw]))

// ------------------------------------------------------------------ tra từ

export async function lookup(rawQuery) {
  const query = normalizeQuery(rawQuery)
  if (query.length > 60 || !VALID_QUERY.test(query)) throw invalid('Từ cần tra không hợp lệ')
  const owned = notebookByWord()
  const list = candidates(query)

  // 1. Kho nội bộ + từ đã tra trước đây
  const custom = db.read('customWords')
  for (const candidate of list) {
    const hits = [...findWordsByHeadword(candidate), ...custom.filter((w) => w.headword.toLowerCase() === candidate)]
    if (hits.length) {
      return { query, matchedHeadword: candidate, source: 'LOCAL', entries: hits.map((w) => wordResponse(w, owned.get(w.id))) }
    }
  }
  // 2. Từ điển ngoài: thử từ gốc tra được và tối đa 1 dạng rút gọn
  for (const candidate of list.slice(0, 2)) {
    const entries = await fetchDictionary(candidate)
    if (entries.length) {
      const saved = entries.map(cacheCustomWord)
      return { query, matchedHeadword: saved[0].headword, source: 'DICTIONARY_API', entries: saved.map((w) => wordResponse(w, owned.get(w.id))) }
    }
  }
  return { query, matchedHeadword: null, source: 'NONE', entries: [] }
}

/** Gọi Free Dictionary API; lỗi mạng / không tìm thấy -> []. */
async function fetchDictionary(word) {
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 5000)
    const res = await fetch(DICTIONARY_URL + encodeURIComponent(word), { signal: controller.signal })
    clearTimeout(timer)
    if (!res.ok) return []
    const root = await res.json()
    return Array.isArray(root) && root.length ? parseDictionary(root) : []
  } catch {
    return []
  }
}

/** Gộp mọi entry; mỗi từ loại lấy định nghĩa + ví dụ đầu tiên (tối đa 3 từ loại). */
export function parseDictionary(root) {
  const headword = root[0].word
  let fallback = root[0].phonetic ?? null
  let phoneticUk = null, phoneticUs = null, audioUk = null, audioUs = null
  for (const entry of root) {
    for (const p of entry.phonetics ?? []) {
      if (p.audio?.includes('-uk.') && !audioUk) {
        audioUk = p.audio
        phoneticUk = p.text ?? phoneticUk
      } else if (p.audio?.includes('-us.') && !audioUs) {
        audioUs = p.audio
        phoneticUs = p.text ?? phoneticUs
      } else if (p.text && !fallback) {
        fallback = p.text
      }
    }
  }
  const byPos = new Map()
  for (const entry of root) {
    for (const meaning of entry.meanings ?? []) {
      const pos = KNOWN_POS.has(meaning.partOfSpeech?.toLowerCase()) ? meaning.partOfSpeech.toLowerCase() : 'other'
      const slot = byPos.get(pos) ?? {}
      for (const def of meaning.definitions ?? []) {
        slot.definition ??= def.definition
        slot.example ??= def.example
      }
      byPos.set(pos, slot)
    }
  }
  return [...byPos].filter(([, s]) => s.definition).slice(0, 3).map(([pos, s]) => ({
    headword, partOfSpeech: pos, phoneticUk: phoneticUk ?? fallback, phoneticUs: phoneticUs ?? fallback,
    audioUrlUk: audioUk, audioUrlUs: audioUs, definitionEn: s.definition, example: s.example ?? null,
  }))
}

function cacheCustomWord(entry) {
  const existing = db.read('customWords').find((w) => w.headword.toLowerCase() === entry.headword.toLowerCase()
    && w.partOfSpeech === entry.partOfSpeech)
  if (existing) return existing
  const word = { ...entry, id: CUSTOM_WORD_ID_BASE + db.nextId('customWord'), meaningVi: '', source: 'DICTIONARY_API' }
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
