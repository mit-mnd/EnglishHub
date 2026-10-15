/**
 * Tách nội dung câu FILL_BLANK thành các đoạn text và ô trống.
 * "She {{1}} tea, but {{2}} coffee." ->
 *   [{type:'text',value:'She '},{type:'blank',index:0},{type:'text',value:' tea, but '},...]
 */
export function parseBlanks(content = '') {
  const parts = []
  const regex = /\{\{(\d+)\}\}/g
  let last = 0
  let match
  while ((match = regex.exec(content)) !== null) {
    if (match.index > last) parts.push({ type: 'text', value: content.slice(last, match.index) })
    parts.push({ type: 'blank', index: Number(match[1]) - 1 })
    last = regex.lastIndex
  }
  if (last < content.length) parts.push({ type: 'text', value: content.slice(last) })
  return parts
}

/** Câu hỏi đã được trả lời (dùng cho thanh điều hướng & cảnh báo trước khi nộp). */
export function isAnswered(question, answer) {
  if (!answer) return false
  switch (question.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return (answer.selectedOptionIds?.length ?? 0) > 0
    case 'FILL_BLANK':
      return (answer.textAnswers ?? []).some((t) => t?.trim())
    case 'MATCHING':
      return Object.values(answer.matches ?? {}).some(Boolean)
    case 'ORDERING':
      return (answer.orderedOptionIds?.length ?? 0) > 0
    default:
      return !!answer.answerText?.trim()
  }
}

/** Đổi đáp án đã lưu (AnswerPayload) thành chuỗi dễ đọc khi xem lại bài. */
export function describeAnswer(question, answer) {
  if (!answer || !isAnswered(question, answer)) return 'Chưa trả lời'
  const optionText = (id) => question.options?.find((o) => o.id === id)?.content ?? '?'
  switch (question.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return answer.selectedOptionIds.map(optionText).join(', ')
    case 'ORDERING':
      return answer.orderedOptionIds.map(optionText).join(' ')
    case 'FILL_BLANK':
      return answer.textAnswers.map((t, i) => `(${i + 1}) ${t || '…'}`).join('  ')
    case 'MATCHING':
      return Object.entries(answer.matches).map(([id, text]) => `${optionText(Number(id))} → ${text}`).join('; ')
    default:
      return answer.answerText
  }
}

/** Đáp án đúng dạng chuỗi (từ dữ liệu review của backend). */
export function describeCorrectAnswer(review) {
  switch (review.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE':
      return review.options.filter((o) => o.correct).map((o) => o.content).join(', ')
    case 'ORDERING':
      return [...review.options].sort((a, b) => a.correctOrder - b.correctOrder).map((o) => o.content).join(' ')
    case 'FILL_BLANK':
      return review.acceptedAnswers.map((alts, i) => `(${i + 1}) ${alts.join(' / ')}`).join('  ')
    case 'MATCHING':
      return review.options.map((o) => `${o.content} → ${o.matchText}`).join('; ')
    default:
      return '—'
  }
}

export const OPTION_LETTERS = ['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H']
