/**
 * Đồng hồ dùng chung cho lõi ứng dụng. Test có thể cố định thời gian bằng setNow().
 * Mọi mốc "hôm nay" tính theo giờ máy của người học (dữ liệu chỉ nằm trên máy này).
 */
let fixed = null

export function now() {
  return fixed ? new Date(fixed.getTime()) : new Date()
}

export function nowIso() {
  return now().toISOString()
}

/** Chỉ dùng trong test. null = trở lại giờ thật. */
export function setNow(date) {
  fixed = date == null ? null : new Date(date)
}

/** 'YYYY-MM-DD' theo giờ địa phương. */
export function localDate(date = now()) {
  const d = date instanceof Date ? date : new Date(date)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/** Cộng ngày cho chuỗi 'YYYY-MM-DD'. */
export function addDays(isoDate, days) {
  const [y, m, d] = isoDate.split('-').map(Number)
  return localDate(new Date(y, m - 1, d + days))
}

/** Thời điểm 0h hôm nay (giờ địa phương). */
export function startOfToday(date = now()) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

/** 'HH:mm' hiện tại. */
export function localTime(date = now()) {
  return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function plusMs(date, ms) {
  return new Date(new Date(date).getTime() + ms)
}
