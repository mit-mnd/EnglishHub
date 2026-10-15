import { describe, expect, it } from 'vitest'
import { countWords, scoreColor, segmentText } from '../feedback'

describe('feedback', () => {
  it('tách văn bản theo vị trí lỗi', () => {
    const text = 'She have a umbrella.'
    const parts = segmentText(text, [
      { start: 9, end: 10, type: 'GRAMMAR' },
      { start: 4, end: 8, type: 'GRAMMAR' },
    ])
    expect(parts.map((p) => [p.text, !!p.issue])).toEqual([
      ['She ', false], ['have', true], [' ', false], ['a', true], [' umbrella.', false],
    ])
    expect(parts.map((p) => p.text).join('')).toBe(text)
  })

  it('bỏ qua lỗi chồng lấn hoặc vượt độ dài', () => {
    const parts = segmentText('abc def', [{ start: 0, end: 3 }, { start: 1, end: 2 }, { start: 5, end: 99 }])
    expect(parts.filter((p) => p.issue)).toHaveLength(1)
  })

  it('đếm từ như backend', () => {
    // I'm | a | well-known | student | aren't | I
    expect(countWords("I'm a well-known student, aren't I?")).toBe(6)
    expect(countWords('')).toBe(0)
  })

  it('màu theo điểm', () => {
    expect(scoreColor(90)).toBe('#10b981')
    expect(scoreColor(40)).toBe('#ef4444')
  })
})
