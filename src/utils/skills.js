import { Collection, EditPen, Headset, Microphone, Notebook, Reading } from '@element-plus/icons-vue'

/** Nhãn, icon và màu nhận diện của từng kỹ năng. */
export const SKILL_META = {
  LISTENING: { label: 'Nghe', icon: Headset, color: '#0ea5e9', soft: '#e0f2fe' },
  SPEAKING: { label: 'Nói', icon: Microphone, color: '#f97316', soft: '#ffedd5' },
  READING: { label: 'Đọc', icon: Reading, color: '#10b981', soft: '#d1fae5' },
  WRITING: { label: 'Viết', icon: EditPen, color: '#8b5cf6', soft: '#ede9fe' },
  VOCABULARY: { label: 'Từ vựng', icon: Collection, color: '#ec4899', soft: '#fce7f3' },
  GRAMMAR: { label: 'Ngữ pháp', icon: Notebook, color: '#4f46e5', soft: '#eef2ff' },
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
