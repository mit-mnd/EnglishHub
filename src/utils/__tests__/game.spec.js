import { describe, expect, it } from 'vitest'
import { checkDictation, levenshtein, matchingScore, pointsScore, scrambleLetters, shuffle } from '../game'
import { formatRelative } from '../format'

describe('game utils', () => {
  it('shuffle giữ nguyên phần tử, không sửa mảng gốc', () => {
    const src = [1, 2, 3, 4, 5]
    const out = shuffle(src)
    expect(out).toHaveLength(5)
    expect([...out].sort()).toEqual(src)
    expect(src).toEqual([1, 2, 3, 4, 5])
  })

  it('scrambleLetters luôn khác thứ tự gốc kể cả khi random cố định', () => {
    const tiles = scrambleLetters('Apple', () => 0.999)
    expect(tiles.map((t) => t.ch).join('')).not.toBe('apple')
    expect(tiles.map((t) => t.ch).sort().join('')).toBe('aelpp')
    expect(new Set(tiles.map((t) => t.id)).size).toBe(5)
    expect(scrambleLetters('aaa').map((t) => t.ch).join('')).toBe('aaa')
  })

  it('levenshtein', () => {
    expect(levenshtein('kitten', 'sitting')).toBe(3)
    expect(levenshtein('', 'abc')).toBe(3)
    expect(levenshtein('same', 'same')).toBe(0)
  })

  it('checkDictation: đúng / gần đúng / sai', () => {
    expect(checkDictation('  Library ', 'library')).toBe('CORRECT')
    expect(checkDictation('libary', 'library')).toBe('CLOSE')
    expect(checkDictation('cot', 'cat')).toBe('WRONG') // từ ngắn: sai 1 chữ là sai
    expect(checkDictation('', 'library')).toBe('WRONG')
  })

  it('matchingScore trong 0..100, thưởng thời gian khi hoàn thành', () => {
    expect(matchingScore({ matched: 6, total: 6, mistakes: 0, timeLeftMs: 60000, timeLimitMs: 60000 })).toBe(100)
    expect(matchingScore({ matched: 6, total: 6, mistakes: 0, timeLeftMs: 30000, timeLimitMs: 60000 })).toBe(85)
    expect(matchingScore({ matched: 3, total: 6, mistakes: 0, timeLeftMs: 0, timeLimitMs: 60000 })).toBe(35)
    expect(matchingScore({ matched: 1, total: 6, mistakes: 10, timeLeftMs: 0, timeLimitMs: 60000 })).toBe(0)
  })

  it('pointsScore', () => {
    expect(pointsScore([1, 1, 0.5, 0], 4)).toBe(63)
    expect(pointsScore([], 0)).toBe(0)
  })

  it('formatRelative', () => {
    const now = new Date('2026-10-14T10:00:00Z')
    expect(formatRelative('2026-10-14T09:59:30Z', now)).toBe('vừa xong')
    expect(formatRelative('2026-10-14T09:45:00Z', now)).toBe('15 phút trước')
    expect(formatRelative('2026-10-12T10:00:00Z', now)).toBe('2 ngày trước')
  })
})
