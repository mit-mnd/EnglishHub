/**
 * Lặp lại ngắt quãng theo SM-2 (SuperMemo 2), điều chỉnh cho 4 nút tự đánh giá.
 *
 *  Nút    q   Kết quả
 *  AGAIN  1   Quên: học lại sau 10 phút, repetitions = 0 (đang ở REVIEW/MASTERED thì tính 1 lần quên)
 *  HARD   3   Khó:  lần 1 -> 1 ngày, lần 2 -> 3 ngày, sau đó I × 1,2
 *  GOOD   4   Được: lần 1 -> 1 ngày, lần 2 -> 6 ngày, sau đó I × EF
 *  EASY   5   Dễ:   lần 1 -> 4 ngày, lần 2 -> 8 ngày, sau đó I × EF × 1,3
 *
 *  EF' = max(1,3; EF + 0,1 - (5 - q) × (0,08 + (5 - q) × 0,02))
 *  Trạng thái: I < 6 ngày -> LEARNING, 6..20 -> REVIEW, >= 21 -> MASTERED ("đã thuộc")
 *
 * Khoảng cách mới luôn dài hơn khoảng cách cũ ít nhất 1 ngày (trừ AGAIN) để từ không bị "kẹt".
 */

export const GRADES = { AGAIN: 1, HARD: 3, GOOD: 4, EASY: 5 }
export const RELEARN_DELAY_MS = 10 * 60 * 1000
export const DAY_MS = 24 * 60 * 60 * 1000
const MIN_EASE = 1.3
const MAX_INTERVAL_DAYS = 3650
const REVIEW_THRESHOLD_DAYS = 6
const MASTERED_THRESHOLD_DAYS = 21

const ease = (v) => Math.round(v * 100) / 100

/**
 * @param current { easeFactor, intervalDays, repetitions, lapseCount, status }
 * @returns {{ state, delayMs }}
 */
export function schedule(current, grade) {
  const q = GRADES[grade]
  if (q == null) throw new Error(`Mức đánh giá không hợp lệ: ${grade}`)
  const newEf = Math.max(MIN_EASE, current.easeFactor + 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02))

  if (grade === 'AGAIN') {
    const lapse = current.status === 'REVIEW' || current.status === 'MASTERED'
    return {
      state: { easeFactor: ease(newEf), intervalDays: 0, repetitions: 0, lapseCount: current.lapseCount + (lapse ? 1 : 0), status: 'LEARNING' },
      delayMs: RELEARN_DELAY_MS,
    }
  }

  let interval
  if (current.repetitions === 0) {
    interval = grade === 'EASY' ? 4 : 1
  } else if (current.repetitions === 1) {
    interval = grade === 'HARD' ? 3 : grade === 'EASY' ? 8 : 6
  } else {
    const factor = grade === 'HARD' ? 1.2 : grade === 'EASY' ? newEf * 1.3 : newEf
    // Java Math.round: làm tròn nửa lên (2.5 -> 3)
    interval = Math.max(current.intervalDays + 1, Math.floor(current.intervalDays * factor + 0.5))
  }
  interval = Math.min(interval, MAX_INTERVAL_DAYS)
  return {
    state: {
      easeFactor: ease(newEf), intervalDays: interval, repetitions: current.repetitions + 1,
      lapseCount: current.lapseCount, status: statusForInterval(interval),
    },
    delayMs: interval * DAY_MS,
  }
}

function statusForInterval(interval) {
  if (interval >= MASTERED_THRESHOLD_DAYS) return 'MASTERED'
  return interval >= REVIEW_THRESHOLD_DAYS ? 'REVIEW' : 'LEARNING'
}

/** Trạng thái theo tiến độ (dùng khi bỏ tạm ẩn một từ). */
export function statusFor(intervalDays, repetitions) {
  if (repetitions === 0) return intervalDays === 0 ? 'NEW' : 'LEARNING'
  return statusForInterval(intervalDays)
}

/** Số giây tới lần ôn sau nếu chọn từng nút - hiển thị trên nút. */
export function preview(current) {
  return Object.fromEntries(Object.keys(GRADES).map((g) => [g, Math.round(schedule(current, g).delayMs / 1000)]))
}

export const newCardState = () => ({ easeFactor: 2.5, intervalDays: 0, repetitions: 0, lapseCount: 0, status: 'NEW' })
