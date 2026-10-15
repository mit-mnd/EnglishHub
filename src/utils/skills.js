import { Collection, EditPen, Headset, Microphone, Notebook, Reading } from '@element-plus/icons-vue'

/** Nhãn, icon và màu nhận diện của từng kỹ năng. */
export const SKILL_META = {
  LISTENING: { label: 'Nghe', icon: Headset, color: 'color-mix(in srgb, #0ea5e9 72%, var(--eh-text))', soft: 'color-mix(in srgb, #0ea5e9 14%, transparent)' },
  SPEAKING: { label: 'Nói', icon: Microphone, color: 'color-mix(in srgb, #f97316 72%, var(--eh-text))', soft: 'color-mix(in srgb, #f97316 14%, transparent)' },
  READING: { label: 'Đọc', icon: Reading, color: 'color-mix(in srgb, #10b981 72%, var(--eh-text))', soft: 'color-mix(in srgb, #10b981 14%, transparent)' },
  WRITING: { label: 'Viết', icon: EditPen, color: 'color-mix(in srgb, #8b5cf6 72%, var(--eh-text))', soft: 'color-mix(in srgb, #8b5cf6 14%, transparent)' },
  VOCABULARY: { label: 'Từ vựng', icon: Collection, color: 'color-mix(in srgb, #ec4899 72%, var(--eh-text))', soft: 'color-mix(in srgb, #ec4899 14%, transparent)' },
  GRAMMAR: { label: 'Ngữ pháp', icon: Notebook, color: 'color-mix(in srgb, #4f46e5 72%, var(--eh-text))', soft: 'color-mix(in srgb, #4f46e5 14%, transparent)' },
}

export const skillMeta = (skill) => SKILL_META[skill] ?? SKILL_META.GRAMMAR

export const PART_OF_SPEECH = {
  noun: 'danh từ',
  verb: 'động từ',
  adjective: 'tính từ',
  adverb: 'trạng từ',
  pronoun: 'đại từ',
  preposition: 'giới từ',
  conjunction: 'liên từ',
  interjection: 'thán từ',
  phrase: 'cụm từ',
  other: 'khác',
}
