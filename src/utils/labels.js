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

/** Màu nhận diện cho từng level: đủ đậm để chữ trắng trên badge đạt tương phản AA ở cả hai giao diện. */
export const LEVEL_COLORS = {
  FOUNDATION: '#64748b',
  ELEMENTARY: '#15803d',
  INTERMEDIATE: '#0369a1',
  ADVANCED: '#7c3aed',
  EXAM_PREP: '#b45309',
  UNASSIGNED: '#64748b',
}

export const levelColor = (code) => LEVEL_COLORS[code] ?? '#4f46e5'

export const skillLabel = (skill) => SKILL_LABELS[skill] ?? skill
