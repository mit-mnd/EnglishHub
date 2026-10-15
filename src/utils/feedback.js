/**
 * Tách bài viết thành các đoạn thường / đoạn có lỗi theo vị trí [start, end) từ backend
 * (Java và JavaScript cùng đếm theo UTF-16 nên vị trí khớp nhau).
 */
export function segmentText(text, issues = []) {
  const sorted = [...issues].sort((a, b) => a.start - b.start)
  const parts = []
  let cursor = 0
  for (const issue of sorted) {
    if (issue.start < cursor || issue.end > text.length) continue   // bỏ qua lỗi chồng lấn / lệch
    if (issue.start > cursor) parts.push({ text: text.slice(cursor, issue.start), issue: null })
    // Lỗi độ dài 0 (hiếm) vẫn cần một ký tự để hiển thị gạch chân
    parts.push({ text: text.slice(issue.start, issue.end) || ' ', issue })
    cursor = issue.end
  }
  if (cursor < text.length) parts.push({ text: text.slice(cursor), issue: null })
  return parts
}

export const ISSUE_TYPES = {
  SPELLING: { label: 'Chính tả', color: '#ef4444' },
  GRAMMAR: { label: 'Ngữ pháp', color: '#f97316' },
  CAPITALIZATION: { label: 'Viết hoa', color: '#8b5cf6' },
  PUNCTUATION: { label: 'Dấu câu', color: '#0ea5e9' },
  STYLE: { label: 'Văn phong', color: '#64748b' },
}

/** Màu theo điểm 0-100. */
export function scoreColor(score) {
  if (score == null) return '#94a3b8'
  if (score >= 85) return '#10b981'
  if (score >= 70) return '#0ea5e9'
  if (score >= 50) return '#f59e0b'
  return '#ef4444'
}

/** Đếm từ giống backend (chữ cái, có thể nối bằng ' hoặc -). */
export function countWords(text = '') {
  return (text.match(/[A-Za-z]+(?:['’-][A-Za-z]+)*/g) ?? []).length
}
