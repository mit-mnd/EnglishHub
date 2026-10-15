/**
 * Chuẩn hóa từ cần tra và sinh các dạng gốc có thể có (lemmatization đơn giản theo quy tắc),
 * giữ thứ tự ưu tiên: từ nguyên gốc trước. Ứng viên sai không gây hại vì chỉ dùng để tìm trong kho từ.
 *   "souvenirs" -> [souvenirs, souvenir]; "stopped" -> [stopped, stopp, stop, stope]
 */

/** Chữ cái, nháy đơn, gạch nối; tối đa 4 từ (cụm từ ngắn). */
export const VALID_QUERY = /^[a-z]+(?:['-][a-z]+)*(?: [a-z]+(?:['-][a-z]+)*){0,3}$/

export function normalizeQuery(raw) {
  return (raw ?? '').toLowerCase().replace(/’/g, "'").replace(/^[^a-z]+|[^a-z]+$/g, '').replace(/\s+/g, ' ')
}

/** Từ / cụm từ ngắn tra được trong từ điển; ngoài ra (câu dài, có dấu câu) thì chỉ dịch. */
export function isWordQuery(raw) {
  const q = normalizeQuery(raw)
  return q.length <= 60 && VALID_QUERY.test(q)
}

/** "stopp" -> "stop" (phụ âm cuối bị nhân đôi khi thêm -ed/-ing). */
const undouble = (stem) => (stem.length > 2 && stem.at(-1) === stem.at(-2) ? stem.slice(0, -1) : stem)

export function candidates(word) {
  const result = new Set([word])
  if (word.includes(' ')) return [...result]
  if (word.endsWith("'s")) result.add(word.slice(0, -2))
  if (word.endsWith('ies') && word.length > 4) result.add(`${word.slice(0, -3)}y`)
  if (word.endsWith('es') && word.length > 3) result.add(word.slice(0, -2))
  if (word.endsWith('s') && !word.endsWith('ss') && word.length > 3) result.add(word.slice(0, -1))
  if (word.endsWith('ied') && word.length > 4) result.add(`${word.slice(0, -3)}y`)
  if (word.endsWith('ed') && word.length > 3) {
    const stem = word.slice(0, -2)
    result.add(stem).add(undouble(stem)).add(`${stem}e`)
  }
  if (word.endsWith('ing') && word.length > 5) {
    const stem = word.slice(0, -3)
    result.add(stem).add(undouble(stem)).add(`${stem}e`)
  }
  return [...result].filter((s) => s.length >= 2)
}
