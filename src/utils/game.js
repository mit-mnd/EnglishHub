/**
 * Logic thuần của trò chơi từ vựng (không phụ thuộc Vue) - để test được.
 * Điểm luôn nằm trong 0..100 (khớp ràng buộc phía server).
 */

const clamp = (n) => Math.max(0, Math.min(100, Math.round(n)))

/** Fisher-Yates, trả về mảng mới. random() có thể truyền vào để test. */
export function shuffle(items, random = Math.random) {
  const a = [...items]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

/**
 * Xáo chữ cái của một từ, đảm bảo khác thứ tự gốc (trừ khi không thể, vd "aaa").
 * Trả về mảng ô chữ { id, ch } - id để phân biệt các chữ trùng nhau.
 */
export function scrambleLetters(word, random = Math.random) {
  const original = word.toLowerCase()
  const tiles = [...original].map((ch, id) => ({ id, ch }))
  const canDiffer = new Set(original).size > 1
  const text = (arr) => arr.map((t) => t.ch).join('')
  let result = shuffle(tiles, random)
  for (let tries = 0; canDiffer && tries < 20 && text(result) === original; tries++) {
    result = shuffle(tiles, random)
  }
  // random() cố định (test) có thể luôn ra thứ tự gốc -> xoay vòng tới khi khác
  for (let i = 0; canDiffer && i < result.length && text(result) === original; i++) {
    result = [...result.slice(1), result[0]]
  }
  return result
}

/** Khoảng cách Levenshtein (số thao tác thêm/xóa/sửa 1 ký tự). */
export function levenshtein(a, b) {
  if (a === b) return 0
  if (!a.length) return b.length
  if (!b.length) return a.length
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i)
  for (let i = 1; i <= a.length; i++) {
    const cur = [i]
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1))
    }
    prev = cur
  }
  return prev[b.length]
}

export const normalizeAnswer = (s) => (s ?? '').trim().toLowerCase().replace(/\s+/g, ' ')

/**
 * Chấm 1 câu nghe - chép: CORRECT (khớp), CLOSE (sai đúng 1 ký tự với từ >= 5 chữ - được nửa điểm), WRONG.
 */
export function checkDictation(answer, expected) {
  const a = normalizeAnswer(answer)
  const e = normalizeAnswer(expected)
  if (a === e) return 'CORRECT'
  if (a && e.length >= 5 && levenshtein(a, e) === 1) return 'CLOSE'
  return 'WRONG'
}

/** Ghép cặp: 70% theo số cặp đúng + tối đa 30% thưởng thời gian còn lại khi xong; mỗi lần ghép sai -5. */
export function matchingScore({ matched, total, mistakes, timeLeftMs, timeLimitMs }) {
  if (!total) return 0
  const timeBonus = matched === total ? (30 * Math.max(0, timeLeftMs)) / timeLimitMs : 0
  return clamp((70 * matched) / total + timeBonus - 5 * mistakes)
}

/** Điểm từng câu (1 = đúng, 0.5 = gần đúng / dùng gợi ý, 0 = sai) -> thang 100. */
export function pointsScore(points, total) {
  if (!total) return 0
  return clamp((100 * points.reduce((s, p) => s + p, 0)) / total)
}
