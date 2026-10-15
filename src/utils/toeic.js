/** Tiện ích thuần cho màn luyện TOEIC (không phụ thuộc Vue - có unit test). */

export const LETTERS = ['A', 'B', 'C', 'D']

export const PART_LABELS = {
  2: 'Part 2 · Hỏi - đáp',
  3: 'Part 3 · Hội thoại',
  4: 'Part 4 · Bài nói ngắn',
  5: 'Part 5 · Hoàn thành câu',
  6: 'Part 6 · Hoàn thành đoạn văn',
  7: 'Part 7 · Đọc hiểu',
}

/** Khoảng nghỉ (ms) trong phần Nghe Part 2 - mô phỏng nhịp đọc của đề thật. */
export const LISTENING_PAUSES = {
  afterQuestion: 1200,   // sau câu hỏi, trước đáp án A
  afterLetter: 650,      // giữa chữ cái "A" và nội dung đáp án
  betweenOptions: 1100,  // sau mỗi đáp án
}

/**
 * Kịch bản đọc cho phần Nghe:
 *  - Part 2: câu hỏi (giọng A) rồi từng đáp án (giọng B), giống đề thật không in sẵn đáp án.
 *    Chữ cái "A/B/C" là một câu đọc riêng (lead) rồi nghỉ, nếu đọc chung "A. ..." giọng tổng hợp gần như không ngắt.
 *  - Part 3/4: từng lượt lời với giọng của người nói.
 */
export function speechQueue(group) {
  if (group.kind === 'response') {
    const other = group.questionVoice === 'male' ? 'female' : 'male'
    return [
      { text: group.script, voice: group.questionVoice, pauseMs: LISTENING_PAUSES.afterQuestion },
      ...group.questions[0].options.map((o, i) => ({
        lead: LETTERS[i], leadPauseMs: LISTENING_PAUSES.afterLetter, text: o, voice: other, pauseMs: LISTENING_PAUSES.betweenOptions,
      })),
    ]
  }
  if (group.kind === 'talk') {
    const voiceOf = Object.fromEntries((group.speakers ?? []).map((s) => [s.name, s.voice]))
    return group.script.map((line) => ({ text: line.text, voice: voiceOf[line.speaker] ?? 'female', speaker: line.speaker, pauseMs: 500 }))
  }
  return []
}

/** Tách đoạn văn Part 6 thành các phần văn bản và ô trống {{n}}. */
export function splitPassage(text) {
  const parts = []
  let last = 0
  for (const m of text.matchAll(/\{\{(\d+)}}/g)) {
    if (m.index > last) parts.push({ type: 'text', value: text.slice(last, m.index) })
    parts.push({ type: 'blank', blank: Number(m[1]) })
    last = m.index + m[0].length
  }
  if (last < text.length) parts.push({ type: 'text', value: text.slice(last) })
  return parts
}

/** Đếm đúng/tổng của một nhóm theo câu trả lời { questionId: index }. */
export function scoreGroup(group, answers) {
  const correct = group.questions.filter((q) => answers[q.id] === q.answer).length
  return { correct, total: group.questions.length }
}

export const questionCount = (groups) => groups.reduce((s, g) => s + g.questions.length, 0)

/** Câu đầu tiên của nhóm thứ i có số thứ tự bao nhiêu trong cả đề (đánh số liên tục 1..n). */
export function firstNumber(groups, index) {
  return groups.slice(0, index).reduce((s, g) => s + g.questions.length, 0) + 1
}
