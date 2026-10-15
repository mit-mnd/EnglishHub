import DOMPurify from 'dompurify'

/** Thẻ HTML được phép trong nội dung bài học (do admin soạn) - mọi thứ khác bị loại bỏ để chống XSS. */
const ALLOWED_TAGS = ['p', 'br', 'strong', 'b', 'em', 'i', 'u', 'h2', 'h3', 'h4', 'ul', 'ol', 'li',
  'blockquote', 'span', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'img', 'a']
const ALLOWED_ATTR = ['href', 'src', 'alt', 'title', 'target', 'rel']

const WORD = /[A-Za-z]+(?:['’-][A-Za-z]+)*/g

export function sanitizeHtml(html) {
  return DOMPurify.sanitize(html ?? '', { ALLOWED_TAGS, ALLOWED_ATTR })
}

/** Từ đã lưu khớp dạng đơn giản: "souvenirs" vẫn được tô nếu đã lưu "souvenir". */
export function isSavedWord(word, savedSet) {
  const w = word.toLowerCase().replace('’', "'")
  return savedSet.has(w) || savedSet.has(w.replace(/'s$/, '')) || savedSet.has(w.replace(/e?s$/, ''))
    || savedSet.has(w.replace(/ies$/, 'y'))
}

/**
 * Lọc HTML rồi bọc mỗi từ tiếng Anh trong <span class="rw" data-w="..."> để bấm tra từ.
 * Chỉ thao tác trên text node nên không phá vỡ cấu trúc thẻ.
 */
export function prepareReadingHtml(html, savedSet = new Set()) {
  const doc = new DOMParser().parseFromString(`<div>${sanitizeHtml(html)}</div>`, 'text/html')
  const root = doc.body.firstElementChild
  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT)
  const textNodes = []
  while (walker.nextNode()) textNodes.push(walker.currentNode)

  for (const node of textNodes) {
    const text = node.nodeValue
    if (!WORD.test(text)) continue
    WORD.lastIndex = 0
    const fragment = doc.createDocumentFragment()
    let last = 0
    for (const match of text.matchAll(WORD)) {
      if (match.index > last) fragment.append(text.slice(last, match.index))
      const span = doc.createElement('span')
      span.className = isSavedWord(match[0], savedSet) ? 'rw rw--saved' : 'rw'
      span.dataset.w = match[0]
      span.textContent = match[0]
      fragment.append(span)
      last = match.index + match[0].length
    }
    if (last < text.length) fragment.append(text.slice(last))
    node.replaceWith(fragment)
  }
  return root.innerHTML
}

/** Câu chứa vị trí `index` trong đoạn văn (dùng làm ngữ cảnh khi lưu từ). */
export function sentenceAround(paragraph, index) {
  const text = paragraph ?? ''
  const start = Math.max(text.lastIndexOf('.', index - 1), text.lastIndexOf('!', index - 1), text.lastIndexOf('?', index - 1)) + 1
  const ends = ['.', '!', '?'].map((c) => text.indexOf(c, index)).filter((i) => i !== -1)
  const end = ends.length ? Math.min(...ends) + 1 : text.length
  return text.slice(start, end).trim().slice(0, 1000)
}
