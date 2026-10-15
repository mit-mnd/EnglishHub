const dateFmt = new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' })
const dateTimeFmt = new Intl.DateTimeFormat('vi-VN', {
  day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit',
})

/** Chuỗi 'YYYY-MM-DD' (LocalDate) được hiểu là ngày theo lịch, không theo UTC. */
function toDate(value) {
  if (value instanceof Date) return value
  if (typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [y, m, d] = value.split('-').map(Number)
    return new Date(y, m - 1, d)
  }
  return new Date(value)
}

/** 'YYYY-MM-DD' theo giờ máy người dùng (toISOString() dùng UTC -> lệch ngày ở VN trước 7h sáng). */
export function localIsoDate(date = new Date()) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

export function formatDate(value) {
  return value ? dateFmt.format(toDate(value)) : '—'
}

export function formatDateTime(value) {
  return value ? dateTimeFmt.format(toDate(value)) : '—'
}

/** 754 -> "12 phút 34 giây" */
export function formatDuration(totalSeconds) {
  const s = Math.max(0, Math.round(totalSeconds ?? 0))
  const h = Math.floor(s / 3600)
  const m = Math.floor((s % 3600) / 60)
  const sec = s % 60
  if (h) return `${h} giờ ${m} phút`
  if (m) return sec ? `${m} phút ${sec} giây` : `${m} phút`
  return `${sec} giây`
}

/** 95 -> "1 giờ 35 phút" */
export function formatMinutes(minutes) {
  const m = Math.max(0, Math.round(minutes ?? 0))
  if (m < 60) return `${m} phút`
  const h = Math.floor(m / 60)
  return m % 60 ? `${h} giờ ${m % 60} phút` : `${h} giờ`
}

/** Khoảng thời gian tới lần ôn sau (giây) -> "10 phút", "1 ngày", "2 tháng". */
export function formatInterval(seconds) {
  const s = Math.max(0, seconds ?? 0)
  if (s < 3600) return `${Math.max(1, Math.round(s / 60))} phút`
  if (s < 86400) return `${Math.round(s / 3600)} giờ`
  const days = Math.round(s / 86400)
  if (days < 30) return `${days} ngày`
  if (days < 365) return `${Math.round(days / 30)} tháng`
  return `${Math.round(days / 365)} năm`
}

/** Đồng hồ đếm ngược: 125000 ms -> "02:05" */
export function formatClock(ms) {
  const total = Math.max(0, Math.ceil(ms / 1000))
  const m = Math.floor(total / 60)
  const s = total % 60
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

export function formatPercent(value, digits = 0) {
  if (value == null) return '—'
  return `${Number(value).toFixed(digits)}%`
}

/** "Nguyễn Văn An" -> "VA" (2 chữ cái cuối, đúng cách gọi tên của người Việt) */
export function initials(fullName = '') {
  const parts = fullName.trim().split(/\s+/).filter(Boolean)
  if (!parts.length) return '?'
  const picked = parts.length === 1 ? parts : parts.slice(-2)
  return picked.map((p) => p[0].toUpperCase()).join('')
}

/** Tên gọi thân mật: từ cuối cùng của họ tên. */
export function firstName(fullName = '') {
  const parts = fullName.trim().split(/\s+/)
  return parts[parts.length - 1] || fullName
}

export function greeting(date = new Date()) {
  const h = date.getHours()
  if (h < 11) return 'Chào buổi sáng'
  if (h < 14) return 'Chào buổi trưa'
  if (h < 18) return 'Chào buổi chiều'
  return 'Chào buổi tối'
}

/** Thời gian tương đối cho thông báo: "vừa xong", "5 phút trước", "3 ngày trước"; quá 7 ngày hiện ngày. */
export function formatRelative(value, now = new Date()) {
  if (!value) return '—'
  const diff = Math.max(0, (now.getTime() - toDate(value).getTime()) / 1000)
  if (diff < 60) return 'vừa xong'
  if (diff < 3600) return `${Math.floor(diff / 60)} phút trước`
  if (diff < 86400) return `${Math.floor(diff / 3600)} giờ trước`
  if (diff < 7 * 86400) return `${Math.floor(diff / 86400)} ngày trước`
  return formatDate(value)
}
