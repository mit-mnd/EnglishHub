/** Trò chơi từ vựng: lấy bộ từ để chơi (ưu tiên sổ từ của người học), lưu kết quả và kỷ lục. */
import * as db from '../storage/db'
import { nowIso } from '../clock'
import { invalid } from '../errors'
import { words as allWords } from '../content'
import { recordStudyTime } from './activity'
import { requireProfile, shuffle } from './shared'
import { findWord } from './vocabulary'

const TYPES = ['MATCHING', 'SCRAMBLE', 'DICTATION']
/** Xếp chữ / nghe chép: 1 từ đơn, 3-12 chữ cái. */
const SINGLE_WORD = /^[A-Za-z]{3,12}$/
const MAX_STUDY_SECONDS = 900

function gameWord(w, fromNotebook) {
  return {
    wordId: w.id, headword: w.headword, partOfSpeech: w.partOfSpeech, phonetic: w.phoneticUs ?? w.phoneticUk ?? null,
    audioUrl: w.audioUrlUs ?? w.audioUrlUk ?? null, meaning: w.meaningVi?.trim() ? w.meaningVi : w.definitionEn,
    example: w.example ?? null, fromNotebook,
  }
}

export function gameWords(type, requested = 8) {
  if (!TYPES.includes(type)) throw invalid('Loại trò chơi không hợp lệ', 'gameType')
  const profile = requireProfile()
  const count = Math.max(4, Math.min(Number(requested) || 8, 20))
  const accept = type === 'MATCHING' ? () => true : (h) => SINGLE_WORD.test(h)
  const picked = new Map()   // headword chữ thường -> từ, tránh trùng
  const add = (w, fromNotebook) => {
    const key = w.headword.toLowerCase()
    const meaning = w.meaningVi?.trim() || w.definitionEn
    if (picked.size < count && !picked.has(key) && meaning && accept(w.headword)) picked.set(key, gameWord(w, fromNotebook))
  }
  const notebook = db.read('notebook').filter((uw) => uw.status !== 'SUSPENDED').slice(-200)
  shuffle(notebook).forEach((uw) => {
    const w = findWord(uw.wordId)
    if (w) add(w, true)
  })
  if (picked.size < count) {
    // Sổ từ còn ít: bổ sung từ kho chung ở trình độ hiện tại trở xuống
    const maxLevel = profile.currentLevelId ?? 2
    shuffle(allWords.filter((w) => w.meaningVi && (w.levelId == null || w.levelId <= maxLevel))).forEach((w) => add(w, false))
  }
  return shuffle([...picked.values()])
}

export function submitResult({ gameType, score, correctCount, totalCount, durationSeconds }) {
  if (!TYPES.includes(gameType)) throw invalid('Loại trò chơi không hợp lệ', 'gameType')
  const ints = { score: [0, 100], correctCount: [0, 50], totalCount: [1, 50], durationSeconds: [0, 3600] }
  for (const [field, [min, max]] of Object.entries(ints)) {
    const v = { score, correctCount, totalCount, durationSeconds }[field]
    if (!Number.isInteger(v) || v < min || v > max) throw invalid(`${field} phải từ ${min} đến ${max}`, field)
  }
  if (correctCount > totalCount) throw invalid('Số câu đúng không thể lớn hơn tổng số câu')
  const previous = db.read('games').filter((g) => g.gameType === gameType).map((g) => g.score)
  const previousBest = previous.length ? Math.max(...previous) : null
  const result = { id: db.nextId('game'), gameType, score, correctCount, totalCount, durationSeconds, createdAt: nowIso() }
  db.update('games', (list) => [...list, result])
  if (durationSeconds > 0) recordStudyTime(Math.min(durationSeconds, MAX_STUDY_SECONDS))
  const best = previousBest == null || score > previousBest
  return { id: result.id, score, bestScore: best ? score : previousBest, personalBest: best && score > 0, previousBest }
}

export function gameStats() {
  const results = db.read('games')
  return TYPES.map((gameType) => {
    const mine = results.filter((g) => g.gameType === gameType)
    return {
      gameType, plays: mine.length, bestScore: mine.length ? Math.max(...mine.map((g) => g.score)) : null,
      lastPlayedAt: mine.at(-1)?.createdAt ?? null,
    }
  })
}
