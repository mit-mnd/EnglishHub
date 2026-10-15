export const SKILL_LABELS = {
  LISTENING: 'Nghe',
  SPEAKING: 'Nói',
  READING: 'Đọc',
  WRITING: 'Viết',
  VOCABULARY: 'Từ vựng',
  GRAMMAR: 'Ngữ pháp',
}

export const GOAL_OPTIONS = [
  { value: 'COMMUNICATION', label: 'Giao tiếp', description: 'Tự tin nói chuyện hằng ngày, đi làm, du lịch' },
  { value: 'TOEIC', label: 'TOEIC', description: 'Luyện đề Listening & Reading' },
  { value: 'IELTS', label: 'IELTS', description: 'Học thuật, 4 kỹ năng' },
  { value: 'GENERAL', label: 'Tổng quát', description: 'Nâng trình độ toàn diện' },
]

export const GOAL_LABELS = Object.fromEntries(GOAL_OPTIONS.map((g) => [g.value, g.label]))

export const USER_STATUS = {
  ACTIVE: { label: 'Hoạt động', type: 'success' },
  LOCKED: { label: 'Đã khóa', type: 'warning' },
  DISABLED: { label: 'Vô hiệu hóa', type: 'info' },
}

export const ROLE_LABELS = { LEARNER: 'Học viên', ADMIN: 'Quản trị' }

/** Màu nhận diện cho từng level (dùng cho badge, biểu đồ). */
export const LEVEL_COLORS = {
  FOUNDATION: '#94a3b8',
  ELEMENTARY: '#22c55e',
  INTERMEDIATE: '#0ea5e9',
  ADVANCED: '#8b5cf6',
  EXAM_PREP: '#f59e0b',
  UNASSIGNED: '#cbd5e1',
}

export const levelColor = (code) => LEVEL_COLORS[code] ?? '#4f46e5'

export const skillLabel = (skill) => SKILL_LABELS[skill] ?? skill
