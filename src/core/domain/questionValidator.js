/**
 * Kiểm tra cấu trúc câu hỏi theo từng dạng - dùng để kiểm tra dữ liệu JSON (npm run validate:data / unit test)
 * trước khi đưa vào ứng dụng, để bộ chấm và màn làm bài luôn nhận dữ liệu hợp lệ.
 *  - SINGLE_CHOICE: 2-8 phương án, đúng 1 phương án đúng.
 *  - MULTIPLE_CHOICE: 2-8 phương án, ít nhất 1 phương án đúng.
 *  - FILL_BLANK: ô trống {{1}}..{{n}} liên tiếp trong đề; answerKey là JSON n mảng đáp án chấp nhận.
 *  - MATCHING: 2-10 cặp, vế trái không trùng, vế phải bắt buộc.
 *  - ORDERING: 2-10 mảnh, correctOrder là hoán vị của 1..n.
 *  - SPEAKING: answerKey là câu mẫu cần đọc. ESSAY: answerKey (tùy chọn) {"minWords","maxWords"}.
 * @returns {string[]} danh sách lỗi (rỗng = hợp lệ)
 */
export function validateQuestion(q) {
  const errors = []
  const opts = q.options ?? []
  const fail = (m) => errors.push(`Câu #${q.id}: ${m}`)
  if (!q.content?.trim()) fail('thiếu đề bài')

  const count = (min, max, what) => {
    if (opts.length < min || opts.length > max) fail(`cần ${min}-${max} ${what}`)
    if (opts.some((o) => !o.content?.trim())) fail(`${what} trống`)
    const ids = new Set(opts.map((o) => o.id))
    if (ids.size !== opts.length || opts.some((o) => o.id == null)) fail('id phương án thiếu hoặc trùng')
  }
  const distinct = (values, message) => {
    const seen = new Set(values.map((v) => v.trim().toLowerCase()))
    if (seen.size !== values.length) fail(message)
  }

  switch (q.type) {
    case 'SINGLE_CHOICE':
    case 'MULTIPLE_CHOICE': {
      count(2, 8, 'phương án')
      distinct(opts.map((o) => o.content ?? ''), 'các phương án bị trùng')
      const correct = opts.filter((o) => o.correct).length
      if (q.type === 'SINGLE_CHOICE' && correct !== 1) fail('câu một đáp án phải có đúng 1 phương án đúng')
      if (correct < 1) fail('cần ít nhất 1 phương án đúng')
      break
    }
    case 'FILL_BLANK': {
      const numbers = [...new Set([...(q.content ?? '').matchAll(/\{\{(\d+)}}/g)].map((m) => Number(m[1])))].sort((a, b) => a - b)
      if (!numbers.length) fail('đề cần ít nhất 1 ô trống {{1}}')
      if (numbers.some((n, i) => n !== i + 1)) fail('các ô trống phải đánh số liên tiếp')
      let blanks
      try {
        blanks = JSON.parse(q.answerKey)
      } catch {
        fail('answerKey không phải JSON')
        break
      }
      if (!Array.isArray(blanks) || blanks.length !== numbers.length) fail(`cần đáp án cho đủ ${numbers.length} ô trống`)
      else if (blanks.some((b) => !Array.isArray(b) || !b.some((s) => s?.trim()))) fail('có ô trống chưa có đáp án')
      break
    }
    case 'MATCHING':
      count(2, 10, 'cặp nối')
      distinct(opts.map((o) => o.content ?? ''), 'vế trái bị trùng')
      if (opts.some((o) => !o.matchText?.trim())) fail('mỗi cặp nối cần vế phải')
      break
    case 'ORDERING': {
      count(2, 10, 'mảnh')
      const orders = opts.map((o) => o.correctOrder).sort((a, b) => a - b)
      if (orders.some((n, i) => n !== i + 1)) fail(`thứ tự đúng phải là 1..${opts.length}`)
      break
    }
    case 'SPEAKING':
      if (!q.answerKey?.trim()) fail('câu luyện nói cần câu mẫu (answerKey)')
      break
    case 'ESSAY':
      break
    default:
      fail(`dạng câu hỏi không hỗ trợ: ${q.type}`)
  }
  if (!(q.points > 0)) fail('điểm (points) phải > 0')
  return errors
}
