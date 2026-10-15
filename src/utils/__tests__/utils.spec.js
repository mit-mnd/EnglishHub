import { describe, expect, it } from 'vitest'
import { formatClock, formatDuration, formatInterval, formatMinutes, initials, firstName } from '../format'
import { describeAnswer, describeCorrectAnswer, isAnswered, parseBlanks } from '../question'

describe('format', () => {
  it('formatClock làm tròn lên giây và không âm', () => {
    expect(formatClock(125000)).toBe('02:05')
    expect(formatClock(1)).toBe('00:01')
    expect(formatClock(-5000)).toBe('00:00')
  })

  it('formatDuration / formatMinutes', () => {
    expect(formatDuration(754)).toBe('12 phút 34 giây')
    expect(formatDuration(3720)).toBe('1 giờ 2 phút')
    expect(formatDuration(40)).toBe('40 giây')
    expect(formatMinutes(95)).toBe('1 giờ 35 phút')
    expect(formatMinutes(120)).toBe('2 giờ')
  })

  it('formatInterval cho nút đánh giá flashcard', () => {
    expect(formatInterval(600)).toBe('10 phút')
    expect(formatInterval(86400)).toBe('1 ngày')
    expect(formatInterval(15 * 86400)).toBe('15 ngày')
    expect(formatInterval(95 * 86400)).toBe('3 tháng')
    expect(formatInterval(800 * 86400)).toBe('2 năm')
  })

  it('tên tiếng Việt: viết tắt 2 chữ cuối, gọi bằng tên', () => {
    expect(initials('Nguyễn Văn An')).toBe('VA')
    expect(initials('An')).toBe('A')
    expect(initials('')).toBe('?')
    expect(firstName('Trần Thị Bình')).toBe('Bình')
  })
})

describe('question helpers', () => {
  it('parseBlanks tách văn bản và ô trống', () => {
    expect(parseBlanks('She {{1}} tea, but {{2}}.')).toEqual([
      { type: 'text', value: 'She ' },
      { type: 'blank', index: 0 },
      { type: 'text', value: ' tea, but ' },
      { type: 'blank', index: 1 },
      { type: 'text', value: '.' },
    ])
    expect(parseBlanks('{{1}}')).toEqual([{ type: 'blank', index: 0 }])
  })

  it('isAnswered theo từng dạng câu', () => {
    expect(isAnswered({ type: 'SINGLE_CHOICE' }, { selectedOptionIds: [1] })).toBe(true)
    expect(isAnswered({ type: 'SINGLE_CHOICE' }, { selectedOptionIds: [] })).toBe(false)
    expect(isAnswered({ type: 'FILL_BLANK' }, { textAnswers: ['', '  '] })).toBe(false)
    expect(isAnswered({ type: 'FILL_BLANK' }, { textAnswers: ['', 'went'] })).toBe(true)
    expect(isAnswered({ type: 'MATCHING' }, { matches: { 1: '' } })).toBe(false)
    expect(isAnswered({ type: 'ORDERING' }, null)).toBe(false)
  })

  it('describeAnswer / describeCorrectAnswer cho màn hình xem lại', () => {
    const review = {
      type: 'ORDERING',
      options: [
        { id: 1, content: 'cooking', correctOrder: 3 },
        { id: 2, content: 'She', correctOrder: 1 },
        { id: 3, content: 'is', correctOrder: 2 },
      ],
    }
    expect(describeAnswer(review, { orderedOptionIds: [2, 1, 3] })).toBe('She cooking is')
    expect(describeCorrectAnswer(review)).toBe('She is cooking')
    expect(describeAnswer(review, null)).toBe('Chưa trả lời')
  })
})
