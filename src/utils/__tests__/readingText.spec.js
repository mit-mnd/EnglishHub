import { describe, expect, it } from 'vitest'
import { isSavedWord, prepareReadingHtml, sanitizeHtml, sentenceAround } from '../readingText'

describe('readingText', () => {
  it('loại bỏ script, sự kiện và thuộc tính nguy hiểm', () => {
    const dirty = '<p onclick="alert(1)">Hi<script>alert(2)</script><img src="x" onerror="alert(3)"></p>'
    const clean = sanitizeHtml(dirty)
    expect(clean).not.toMatch(/script|onclick|onerror/)
    expect(clean).toContain('<p>Hi')
  })

  it('bọc từng từ, giữ nguyên thẻ và dấu câu', () => {
    const html = prepareReadingHtml('<p>She <strong>bought</strong> souvenirs.</p>', new Set(['souvenir']))
    expect(html).toBe(
      '<p><span class="rw" data-w="She">She</span> <strong><span class="rw" data-w="bought">bought</span></strong> '
      + '<span class="rw rw--saved" data-w="souvenirs">souvenirs</span>.</p>')
  })

  it('giữ từ có nháy đơn và gạch nối là một từ', () => {
    const html = prepareReadingHtml("<p>people's well-being</p>")
    expect(html).toContain('data-w="people\'s"')
    expect(html).toContain('data-w="well-being"')
  })

  it('nhận dạng từ đã lưu ở dạng số nhiều / sở hữu', () => {
    const saved = new Set(['city', 'museum'])
    expect(isSavedWord('cities', saved)).toBe(true)
    expect(isSavedWord('Museum’s', saved)).toBe(true)
    expect(isSavedWord('music', saved)).toBe(false)
  })

  it('lấy câu chứa từ làm ngữ cảnh', () => {
    const p = 'First, she visited the market. Then she had lunch near the river! It was fun.'
    expect(sentenceAround(p, p.indexOf('lunch'))).toBe('Then she had lunch near the river!')
    expect(sentenceAround(p, 2)).toBe('First, she visited the market.')
  })
})
