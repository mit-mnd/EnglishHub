/**
 * Các thuật toán thuần của lộ trình học (chuyển từ PathPlanner bản Java).
 */
import { addDays } from '../clock'

/** Số bài trong một "nhóm" (~1 tuần học nhẹ); ưu tiên kỹ năng yếu được áp dụng trong từng nhóm. */
export const GROUP_SIZE = 4

/**
 * Đẩy bài của kỹ năng yếu lên đầu TRONG MỖI NHÓM {@link GROUP_SIZE} bài, giữ thứ tự giữa các nhóm:
 * lộ trình vẫn đi từ dễ đến khó theo khóa học, nhưng kỹ năng yếu được luyện sớm hơn trong từng chặng.
 */
export function prioritizeWeakSkills(ordered, skillOf, weakSkills) {
  if (!weakSkills?.size) return [...ordered]
  const result = []
  for (let start = 0; start < ordered.length; start += GROUP_SIZE) {
    const group = ordered.slice(start, start + GROUP_SIZE)
    // Array.prototype.sort là stable -> giữ thứ tự gốc trong cùng mức ưu tiên
    group.sort((a, b) => (weakSkills.has(skillOf(a)) ? 0 : 1) - (weakSkills.has(skillOf(b)) ? 0 : 1))
    result.push(...group)
  }
  return result
}

/**
 * Lên lịch ngày học cho các bài chưa xong theo quỹ thời gian mỗi ngày.
 * Một bài dài hơn quỹ ngày vẫn được xếp trọn vào 1 ngày riêng.
 * @returns {string[]} ngày 'YYYY-MM-DD' tương ứng từng bài
 */
export function planDates(pending, minutesOf, dailyMinutes, startDate) {
  const budget = Math.max(1, dailyMinutes)
  const dates = []
  let day = startDate
  let used = 0
  for (const item of pending) {
    const minutes = Math.max(1, minutesOf(item))
    if (used > 0 && used + minutes > budget) {
      day = addDays(day, 1)
      used = 0
    }
    dates.push(day)
    used += minutes
  }
  return dates
}

export const isDone = (item) => item.status === 'COMPLETED' || item.status === 'SKIPPED'

/**
 * Tính lại trạng thái các bài chưa xong: bài chưa xong đầu tiên là AVAILABLE ("học tiếp"),
 * các bài sau là LOCKED ("sắp tới"); bài đang học dở giữ IN_PROGRESS dù nằm ở đâu.
 * @param items danh sách đã sắp theo sortOrder (sửa trực tiếp)
 */
export function refreshStatuses(items) {
  let currentAssigned = false
  for (const item of items) {
    if (isDone(item)) continue
    if (item.status === 'IN_PROGRESS') {
      currentAssigned = true
      continue
    }
    item.status = currentAssigned ? 'LOCKED' : 'AVAILABLE'
    currentAssigned = true
  }
}
